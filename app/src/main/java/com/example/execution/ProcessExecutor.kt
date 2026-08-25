package com.example.execution

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

class ProcessExecutor {
    private var activeProcess: Process? = null
    private var processWriter: BufferedWriter? = null
    private val isStopping = AtomicBoolean(false)
    
    suspend fun execute(
        command: List<String>,
        workingDir: File,
        onLog: (ConsoleMessage) -> Unit,
        timeoutMillis: Long = 30000L,
        env: Map<String, String> = emptyMap(),
        maxOutputBytes: Int = 1024 * 1024 * 5 // 5 MB
    ): Int = withContext(Dispatchers.IO) {
        isStopping.set(false)
        try {
            val pb = ProcessBuilder(command)
            pb.directory(workingDir)
            
            // Set sanitized environment
            val processEnv = pb.environment()
            env.forEach { (k, v) -> processEnv[k] = v }
            
            onLog(ConsoleMessage("> " + command.joinToString(" "), ConsoleMessageType.SYSTEM))
            
            val process = pb.start()
            activeProcess = process
            processWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
            
            var outputLimitReached = false
            var byteCount = 0

            val stdoutJob = launch(Dispatchers.IO) {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line = reader.readLine()
                    while (!isStopping.get() && line != null) {
                        val currentLine = line ?: break
                        if (outputLimitReached) {
                            line = reader.readLine()
                            continue
                        }
                        val lineLen = currentLine.length + 1
                        byteCount += lineLen
                        if (byteCount > maxOutputBytes) {
                            outputLimitReached = true
                            onLog(ConsoleMessage("Output limit reached. Process terminated.", ConsoleMessageType.ERROR))
                            process.destroyForcibly()
                            break
                        }
                        onLog(ConsoleMessage(currentLine, ConsoleMessageType.STDOUT))
                        line = reader.readLine()
                    }
                } catch (_: Exception) {}
            }
            
            val stderrJob = launch(Dispatchers.IO) {
                try {
                    val reader = BufferedReader(InputStreamReader(process.errorStream))
                    var line = reader.readLine()
                    while (!isStopping.get() && line != null) {
                        val currentLine = line ?: break
                        if (outputLimitReached) {
                            line = reader.readLine()
                            continue
                        }
                        val lineLen = currentLine.length + 1
                        byteCount += lineLen
                        if (byteCount > maxOutputBytes) {
                            outputLimitReached = true
                            process.destroyForcibly()
                            break
                        }
                        onLog(ConsoleMessage(currentLine, ConsoleMessageType.STDERR))
                        line = reader.readLine()
                    }
                } catch (_: Exception) {}
            }
            
            val exitCode = if (timeoutMillis > 0) {
                withTimeoutOrNull(timeoutMillis) {
                    val code = process.waitFor()
                    stdoutJob.join()
                    stderrJob.join()
                    code
                }
            } else {
                val code = process.waitFor()
                stdoutJob.join()
                stderrJob.join()
                code
            }
            
            if (exitCode == null) {
                process.destroyForcibly()
                onLog(ConsoleMessage("Execution timed out after ${timeoutMillis}ms.", ConsoleMessageType.ERROR))
                return@withContext -1
            }
            
            return@withContext exitCode
            
        } catch (e: Exception) {
            onLog(ConsoleMessage("Process execution failed: ${e.message}", ConsoleMessageType.ERROR))
            return@withContext -1
        } finally {
            try {
                processWriter?.close()
            } catch (_: Exception) {}
            processWriter = null
            activeProcess = null
        }
    }
    
    fun sendStdin(input: String) {
        try {
            processWriter?.let { writer ->
                writer.write(input)
                if (!input.endsWith("\n")) {
                    writer.newLine()
                }
                writer.flush()
            }
        } catch (_: Exception) {}
    }
    
    fun stop() {
        isStopping.set(true)
        try {
            processWriter?.close()
        } catch (_: Exception) {}
        processWriter = null
        try {
            activeProcess?.destroyForcibly()
        } catch (_: Exception) {}
        activeProcess = null
    }
}
