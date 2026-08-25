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

class EmbeddedJavaEngine {
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
        val javaFiles = projectDir.listFiles { _, name -> name.endsWith(".java") }?.toList() ?: emptyList()
        if (javaFiles.isEmpty()) {
            onLog(ConsoleMessage("Error: No .java files found.", ConsoleMessageType.ERROR))
            return Pair(false, emptyList())
        }

        val diagnostics = mutableListOf<Diagnostic>()

        for (file in javaFiles) {
            val lines = file.readLines()
            var hasClass = false
            var openBraces = 0

            for ((idx, line) in lines.withIndex()) {
                val trimmed = line.trim()
                val lineNum = idx + 1

                if (trimmed.startsWith("class ") || trimmed.contains(" class ")) {
                    hasClass = true
                }

                // Check for missing semicolon on simple statement lines
                if (trimmed.isNotEmpty() &&
                    !trimmed.startsWith("//") &&
                    !trimmed.startsWith("/*") &&
                    !trimmed.startsWith("*") &&
                    !trimmed.endsWith("{") &&
                    !trimmed.endsWith("}") &&
                    !trimmed.endsWith(";") &&
                    !trimmed.startsWith("public") &&
                    !trimmed.startsWith("private") &&
                    !trimmed.startsWith("protected") &&
                    !trimmed.startsWith("if") &&
                    !trimmed.startsWith("else") &&
                    !trimmed.startsWith("for") &&
                    !trimmed.startsWith("while") &&
                    !trimmed.startsWith("import") &&
                    !trimmed.startsWith("package") &&
                    !trimmed.startsWith("@")
                ) {
                    val col = line.length
                    val raw = "${file.name}:$lineNum: error: ';' expected\n$line\n${" ".repeat(maxOf(0, col - 1))}^"
                    diagnostics.add(
                        Diagnostic(
                            severity = DiagnosticSeverity.ERROR,
                            language = "java",
                            file = file.name,
                            line = lineNum,
                            column = col,
                            message = "';' expected",
                            rawMessage = raw
                        )
                    )
                }

                // Count braces
                for (c in trimmed) {
                    if (c == '{') openBraces++
                    if (c == '}') openBraces--
                }
            }

            if (!hasClass) {
                val raw = "${file.name}:1: error: class, interface, or enum expected"
                diagnostics.add(
                    Diagnostic(
                        severity = DiagnosticSeverity.ERROR,
                        language = "java",
                        file = file.name,
                        line = 1,
                        column = 1,
                        message = "class, interface, or enum expected",
                        rawMessage = raw
                    )
                )
            }

            if (openBraces != 0) {
                val raw = "${file.name}:${lines.size}: error: reached end of file while parsing"
                diagnostics.add(
                    Diagnostic(
                        severity = DiagnosticSeverity.ERROR,
                        language = "java",
                        file = file.name,
                        line = lines.size,
                        column = 1,
                        message = "reached end of file while parsing",
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
                val env = JavaEnvironment(projectDir, config.args, stdinChannel, onLog, isStopping)
                env.executeJava(mainFile)
                0
            } catch (e: Exception) {
                if (isStopping.get()) {
                    onLog(ConsoleMessage("Process stopped by user.", ConsoleMessageType.SYSTEM))
                    return@withTimeoutOrNull -2
                }
                val err = "Exception in thread \"main\" ${e::class.java.name}: ${e.message}"
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

    class JavaEnvironment(
        private val projectDir: File,
        private val cliArgs: List<String>,
        private val stdinChannel: Channel<String>,
        private val onLog: (ConsoleMessage) -> Unit,
        private val isStopping: AtomicBoolean
    ) {
        private val variables = mutableMapOf<String, Any?>()

        suspend fun executeJava(file: File) {
            val lines = file.readLines()
            var inMain = false
            var mainBraces = 0

            for ((_, line) in lines.withIndex()) {
                if (isStopping.get()) break
                val trimmed = line.trim()

                if (trimmed.contains("public static void main")) {
                    inMain = true
                    for (c in trimmed) {
                        if (c == '{') mainBraces++
                        if (c == '}') mainBraces--
                    }
                    continue
                }

                if (inMain) {
                    for (c in trimmed) {
                        if (c == '{') mainBraces++
                        if (c == '}') mainBraces--
                    }

                    if (mainBraces <= 0 && trimmed.contains("}")) {
                        inMain = false
                        break
                    }

                    executeJavaStatement(trimmed)
                }
            }
        }

        private suspend fun executeJavaStatement(line: String) {
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*")) return

            // System.out.println / print
            if (line.contains("System.out.println(") || line.contains("System.out.print(")) {
                val isPrintln = line.contains("System.out.println(")
                val prefix = if (isPrintln) "System.out.println(" else "System.out.print("
                val idx = line.indexOf(prefix)
                val endIdx = line.lastIndexOf(")")
                if (idx != -1 && endIdx > idx) {
                    val inner = line.substring(idx + prefix.length, endIdx)
                    val output = evaluateJavaExpr(inner)?.toString() ?: ""
                    onLog(ConsoleMessage(output, ConsoleMessageType.STDOUT))
                }
                return
            }

            // Scanner reading
            if (line.contains("scanner.nextLine()") || line.contains("scanner.next()")) {
                val userVal = stdinChannel.receive()
                if (line.contains("=")) {
                    val varName = line.split("=")[0].trim().split(" ").last()
                    variables[varName] = userVal
                }
                return
            }

            // Variable assignment (int x = 5; String s = "test";)
            if (line.endsWith(";")) {
                val clean = line.substring(0, line.length - 1).trim()
                if (clean.contains("=") && !clean.contains("==")) {
                    val parts = clean.split("=", limit = 2)
                    val left = parts[0].trim().split(" ").last()
                    val right = parts[1].trim()
                    variables[left] = evaluateJavaExpr(right)
                }
            }
        }

        private fun evaluateJavaExpr(expr: String): Any? {
            val trimmed = expr.trim()
            if (trimmed.isEmpty()) return ""

            // String literal
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                return trimmed.substring(1, trimmed.length - 1)
            }

            // Variable
            if (variables.containsKey(trimmed)) {
                return variables[trimmed]
            }

            // Integer or Double
            trimmed.toIntOrNull()?.let { return it }
            trimmed.toDoubleOrNull()?.let { return it }

            // Concatenation: "Java " + "works" or "Count: " + count
            if (trimmed.contains("+")) {
                val parts = trimmed.split("+")
                return parts.joinToString("") { evaluateJavaExpr(it.trim())?.toString() ?: "" }
            }

            // Math calls
            if (trimmed.startsWith("Math.sqrt(")) {
                val inner = trimmed.substring(10, trimmed.length - 1)
                val v = evaluateJavaExpr(inner)?.toString()?.toDoubleOrNull() ?: 0.0
                return kotlin.math.sqrt(v)
            }

            return trimmed
        }
    }
}
