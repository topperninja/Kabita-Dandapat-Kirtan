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

class EmbeddedCppEngine {
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
        val cppFiles = projectDir.listFiles { _, name -> name.endsWith(".cpp") || name.endsWith(".cc") || name.endsWith(".c") }?.toList() ?: emptyList()
        if (cppFiles.isEmpty()) {
            onLog(ConsoleMessage("Error: No .cpp or .c source files found.", ConsoleMessageType.ERROR))
            return Pair(false, emptyList())
        }

        val diagnostics = mutableListOf<Diagnostic>()

        for (file in cppFiles) {
            val lines = file.readLines()
            var hasMain = false
            var openBraces = 0

            for ((idx, line) in lines.withIndex()) {
                val trimmed = line.trim()
                val lineNum = idx + 1

                if (trimmed.contains("main(") || trimmed.contains("main (")) {
                    hasMain = true
                }

                // Check for missing semicolon
                if (trimmed.isNotEmpty() &&
                    !trimmed.startsWith("#") &&
                    !trimmed.startsWith("//") &&
                    !trimmed.startsWith("/*") &&
                    !trimmed.startsWith("*") &&
                    !trimmed.endsWith("{") &&
                    !trimmed.endsWith("}") &&
                    !trimmed.endsWith(";") &&
                    !trimmed.startsWith("if") &&
                    !trimmed.startsWith("else") &&
                    !trimmed.startsWith("for") &&
                    !trimmed.startsWith("while")
                ) {
                    val col = line.length
                    val raw = "${file.name}:$lineNum:$col: error: expected ';' before end of line\n$line\n${" ".repeat(maxOf(0, col - 1))}^"
                    diagnostics.add(
                        Diagnostic(
                            severity = DiagnosticSeverity.ERROR,
                            language = "cpp",
                            file = file.name,
                            line = lineNum,
                            column = col,
                            message = "expected ';' before end of line",
                            rawMessage = raw
                        )
                    )
                }

                for (c in trimmed) {
                    if (c == '{') openBraces++
                    if (c == '}') openBraces--
                }
            }

            if (file.name == "main.cpp" && !hasMain) {
                val raw = "${file.name}:1:1: error: 'main' function was not declared in this scope"
                diagnostics.add(
                    Diagnostic(
                        severity = DiagnosticSeverity.ERROR,
                        language = "cpp",
                        file = file.name,
                        line = 1,
                        column = 1,
                        message = "'main' function was not declared in this scope",
                        rawMessage = raw
                    )
                )
            }

            if (openBraces != 0) {
                val raw = "${file.name}:${lines.size}:1: error: expected '}' at end of input"
                diagnostics.add(
                    Diagnostic(
                        severity = DiagnosticSeverity.ERROR,
                        language = "cpp",
                        file = file.name,
                        line = lines.size,
                        column = 1,
                        message = "expected '}' at end of input",
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
                val env = CppEnvironment(projectDir, stdinChannel, onLog, isStopping)
                env.executeCpp(mainFile)
                0
            } catch (e: Exception) {
                if (isStopping.get()) {
                    onLog(ConsoleMessage("Process stopped by user.", ConsoleMessageType.SYSTEM))
                    return@withTimeoutOrNull -2
                }
                val err = "Runtime error (SIGSEGV / Exception): ${e.message}"
                stderrBuilder.append(err).append("\n")
                onLog(ConsoleMessage(err, ConsoleMessageType.STDERR))
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

    class CppEnvironment(
        private val projectDir: File,
        private val stdinChannel: Channel<String>,
        private val onLog: (ConsoleMessage) -> Unit,
        private val isStopping: AtomicBoolean
    ) {
        private val variables = mutableMapOf<String, Any?>()

        suspend fun executeCpp(file: File) {
            val lines = file.readLines()
            var inMain = false

            for (line in lines) {
                if (isStopping.get()) break
                val trimmed = line.trim()

                if (trimmed.contains("main(") || trimmed.contains("main (")) {
                    inMain = true
                    continue
                }

                if (inMain) {
                    if (trimmed == "return 0;" || trimmed == "return 0 ;" || (trimmed.startsWith("return ") && trimmed.endsWith(";"))) {
                        break
                    }
                    if (trimmed == "}") {
                        break
                    }

                    executeCppStatement(trimmed)
                }
            }
        }

        private suspend fun executeCppStatement(line: String) {
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) return

            // std::cout << ... << std::endl; or cout << ...;
            if (line.contains("cout") && line.contains("<<")) {
                val expr = line.substring(line.indexOf("<<") + 2).removeSuffix(";").trim()
                val parts = expr.split("<<").map { it.trim() }
                val output = StringBuilder()
                for (p in parts) {
                    if (p == "std::endl" || p == "endl" || p == "'\\n'" || p == "\"\\n\"") {
                        // handled line
                    } else {
                        output.append(evaluateCppExpr(p)?.toString() ?: "")
                    }
                }
                onLog(ConsoleMessage(output.toString(), ConsoleMessageType.STDOUT))
                return
            }

            // printf("...")
            if (line.startsWith("printf(") && line.endsWith(");")) {
                val inner = line.substring(7, line.length - 2)
                val str = if (inner.startsWith("\"") && inner.endsWith("\"")) inner.substring(1, inner.length - 1) else inner
                onLog(ConsoleMessage(str.replace("\\n", ""), ConsoleMessageType.STDOUT))
                return
            }

            // std::cin >> var;
            if (line.contains("cin") && line.contains(">>")) {
                val varName = line.substring(line.indexOf(">>") + 2).removeSuffix(";").trim()
                val userVal = stdinChannel.receive()
                variables[varName] = userVal
                return
            }

            // Variable assignment: int a = 5; double b = 3.14;
            if (line.endsWith(";")) {
                val clean = line.substring(0, line.length - 1).trim()
                if (clean.contains("=") && !clean.contains("==")) {
                    val parts = clean.split("=", limit = 2)
                    val left = parts[0].trim().split(" ").last()
                    val right = parts[1].trim()
                    variables[left] = evaluateCppExpr(right)
                }
            }
        }

        private fun evaluateCppExpr(expr: String): Any? {
            val trimmed = expr.trim()
            if (trimmed.isEmpty()) return ""

            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                return trimmed.substring(1, trimmed.length - 1).replace("\\n", "")
            }

            if (variables.containsKey(trimmed)) {
                return variables[trimmed]
            }

            trimmed.toIntOrNull()?.let { return it }
            trimmed.toDoubleOrNull()?.let { return it }

            if (trimmed.contains("+")) {
                val parts = trimmed.split("+")
                return parts.joinToString("") { evaluateCppExpr(it.trim())?.toString() ?: "" }
            }

            return trimmed
        }
    }
}
