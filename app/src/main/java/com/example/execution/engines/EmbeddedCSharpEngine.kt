package com.example.execution.engines

import com.example.execution.ConsoleMessage
import com.example.execution.ConsoleMessageType
import com.example.execution.Diagnostic
import com.example.execution.DiagnosticSeverity
import com.example.execution.ExecutionResult
import com.example.execution.ExecutionState
import com.example.execution.RunConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class EmbeddedCSharpEngine {
    private val stdinChannel = Channel<String>(Channel.UNLIMITED)
    private val isStopping = AtomicBoolean(false)

    fun sendStdin(input: String) {
        stdinChannel.trySend(input)
    }

    fun stop() {
        isStopping.set(true)
        stdinChannel.trySend("")
    }

    fun validateAndBuild(projectDir: File, onLog: (ConsoleMessage) -> Unit): Pair<Boolean, List<Diagnostic>> {
        val csFiles = projectDir.listFiles { _, name -> name.endsWith(".cs") }?.toList() ?: emptyList()
        if (csFiles.isEmpty()) {
            onLog(ConsoleMessage("Error: No .cs files found.", ConsoleMessageType.ERROR))
            return Pair(false, emptyList())
        }

        val diagnostics = mutableListOf<Diagnostic>()

        for (file in csFiles) {
            val lines = file.readLines()
            var hasMain = false

            for ((idx, line) in lines.withIndex()) {
                val trimmed = line.trim()
                val lineNum = idx + 1

                if (trimmed.contains("Main(") || trimmed.contains("Main (")) {
                    hasMain = true
                }

                // Check for missing semicolon
                if (trimmed.isNotEmpty() &&
                    !trimmed.startsWith("using ") &&
                    !trimmed.startsWith("//") &&
                    !trimmed.startsWith("/*") &&
                    !trimmed.startsWith("*") &&
                    !trimmed.endsWith("{") &&
                    !trimmed.endsWith("}") &&
                    !trimmed.endsWith(";") &&
                    !trimmed.startsWith("class ") &&
                    !trimmed.startsWith("namespace ") &&
                    !trimmed.startsWith("public ") &&
                    !trimmed.startsWith("static ") &&
                    !trimmed.startsWith("if ") &&
                    !trimmed.startsWith("else")
                ) {
                    val col = line.length
                    val raw = "${file.name}($lineNum,$col): error CS1002: ; expected"
                    diagnostics.add(
                        Diagnostic(
                            severity = DiagnosticSeverity.ERROR,
                            language = "csharp",
                            file = file.name,
                            line = lineNum,
                            column = col,
                            message = "; expected",
                            rawMessage = raw
                        )
                    )
                }
            }

            if (!hasMain) {
                val raw = "${file.name}(1,1): error CS5001: Program does not contain a static 'Main' method suitable for an entry point"
                diagnostics.add(
                    Diagnostic(
                        severity = DiagnosticSeverity.ERROR,
                        language = "csharp",
                        file = file.name,
                        line = 1,
                        column = 1,
                        message = "Program does not contain a static 'Main' method suitable for an entry point",
                        rawMessage = raw
                    )
                )
            }
        }

        if (diagnostics.isNotEmpty()) {
            for (diag in diagnostics) {
                onLog(ConsoleMessage(diag.rawMessage, ConsoleMessageType.COMPILER_DIAGNOSTIC, diagnostic = diag))
            }
            return Pair(false, diagnostics)
        }

        return Pair(true, emptyList())
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

        val (buildSuccess, buildDiagnostics) = validateAndBuild(projectDir, onLog)
        if (!buildSuccess) {
            return@withContext ExecutionResult(
                state = ExecutionState.BUILD_FAILED,
                exitCode = 1,
                durationMillis = System.currentTimeMillis() - startTime,
                diagnostics = buildDiagnostics
            )
        }

        val timeoutMs = if (config.timeoutMillis > 0) config.timeoutMillis else 30000L
        val result = withTimeoutOrNull(timeoutMs) {
            try {
                val lines = mainFile.readLines()
                var inMain = false

                for (line in lines) {
                    if (isStopping.get()) break
                    val trimmed = line.trim()

                    if (trimmed.contains("Main(") || trimmed.contains("Main (")) {
                        inMain = true
                        continue
                    }

                    if (inMain) {
                        if (trimmed == "}") break

                        if (trimmed.startsWith("Console.WriteLine(") && trimmed.endsWith(");")) {
                            val inner = trimmed.substring(18, trimmed.length - 2).trim()
                            val msg = if (inner.startsWith("\"") && inner.endsWith("\"")) inner.substring(1, inner.length - 1) else inner
                            stdoutBuilder.append(msg).append("\n")
                            onLog(ConsoleMessage(msg, ConsoleMessageType.STDOUT))
                        } else if (trimmed.startsWith("Console.Write(") && trimmed.endsWith(");")) {
                            val inner = trimmed.substring(14, trimmed.length - 2).trim()
                            val msg = if (inner.startsWith("\"") && inner.endsWith("\"")) inner.substring(1, inner.length - 1) else inner
                            stdoutBuilder.append(msg)
                            onLog(ConsoleMessage(msg, ConsoleMessageType.STDOUT))
                        }
                    }
                }
                0
            } catch (e: Exception) {
                if (isStopping.get()) {
                    onLog(ConsoleMessage("Process stopped by user.", ConsoleMessageType.SYSTEM))
                    return@withTimeoutOrNull -2
                }
                val msg = "Unhandled exception: ${e.message}"
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
