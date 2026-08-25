package com.example.execution.engines

import com.example.execution.ConsoleMessage
import com.example.execution.ConsoleMessageType
import com.example.execution.Diagnostic
import com.example.execution.DiagnosticSeverity
import com.example.execution.ExecutionResult
import com.example.execution.ExecutionState
import com.example.execution.RunConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class EmbeddedJsEngine {
    private val isStopping = AtomicBoolean(false)

    fun sendStdin(input: String) {}

    fun stop() {
        isStopping.set(true)
    }

    suspend fun execute(
        mainFile: File,
        projectDir: File,
        config: RunConfig,
        onLog: (ConsoleMessage) -> Unit
    ): ExecutionResult = withContext(Dispatchers.IO) {
        isStopping.set(false)
        val startTime = System.currentTimeMillis()
        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()

        val timeoutMs = if (config.timeoutMillis > 0) config.timeoutMillis else 30000L
        val result = withTimeoutOrNull(timeoutMs) {
            try {
                val lines = mainFile.readLines()
                for (line in lines) {
                    if (isStopping.get()) break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("//")) continue

                    // console.log(...)
                    if (trimmed.startsWith("console.log(") && trimmed.endsWith(");")) {
                        val inner = trimmed.substring(12, trimmed.length - 2).trim()
                        val msg = if ((inner.startsWith("\"") && inner.endsWith("\"")) || (inner.startsWith("'") && inner.endsWith("'"))) {
                            inner.substring(1, inner.length - 1)
                        } else {
                            inner
                        }
                        stdoutBuilder.append(msg).append("\n")
                        onLog(ConsoleMessage(msg, ConsoleMessageType.STDOUT))
                    } else if (trimmed.startsWith("console.error(") && trimmed.endsWith(");")) {
                        val inner = trimmed.substring(14, trimmed.length - 2).trim()
                        val msg = if ((inner.startsWith("\"") && inner.endsWith("\"")) || (inner.startsWith("'") && inner.endsWith("'"))) {
                            inner.substring(1, inner.length - 1)
                        } else {
                            inner
                        }
                        stderrBuilder.append(msg).append("\n")
                        onLog(ConsoleMessage(msg, ConsoleMessageType.STDERR))
                    }
                }
                0
            } catch (e: Exception) {
                if (isStopping.get()) {
                    onLog(ConsoleMessage("Process stopped by user.", ConsoleMessageType.SYSTEM))
                    return@withTimeoutOrNull -2
                }
                val msg = "ReferenceError: ${e.message}"
                stderrBuilder.append(msg).append("\n")
                onLog(ConsoleMessage(msg, ConsoleMessageType.STDERR))
                1
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        if (result == null) {
            onLog(ConsoleMessage("Execution timed out after ${timeoutMs}ms.", ConsoleMessageType.ERROR))
            return@withContext ExecutionResult(
                state = ExecutionState.TIMEOUT,
                exitCode = -1,
                durationMillis = elapsed,
                stdout = stdoutBuilder.toString(),
                stderr = stderrBuilder.toString()
            )
        } else if (result == -2 || isStopping.get()) {
            return@withContext ExecutionResult(
                state = ExecutionState.STOPPED,
                exitCode = 130,
                durationMillis = elapsed,
                stdout = stdoutBuilder.toString(),
                stderr = stderrBuilder.toString()
            )
        } else if (result == 0) {
            return@withContext ExecutionResult(
                state = ExecutionState.SUCCESS,
                exitCode = 0,
                durationMillis = elapsed,
                stdout = stdoutBuilder.toString(),
                stderr = stderrBuilder.toString()
            )
        } else {
            return@withContext ExecutionResult(
                state = ExecutionState.RUNTIME_ERROR,
                exitCode = result,
                durationMillis = elapsed,
                stdout = stdoutBuilder.toString(),
                stderr = stderrBuilder.toString()
            )
        }
    }
}
