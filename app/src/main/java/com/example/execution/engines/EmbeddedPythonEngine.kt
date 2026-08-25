package com.example.execution.engines

import com.example.execution.ConsoleMessage
import com.example.execution.ConsoleMessageType
import com.example.execution.Diagnostic
import com.example.execution.DiagnosticSeverity
import com.example.execution.ExecutionResult
import com.example.execution.ExecutionState
import com.example.execution.RunConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*
import kotlin.random.Random

class EmbeddedPythonEngine {
    private val stdinChannel = Channel<String>(Channel.UNLIMITED)
    private val isRunning = AtomicBoolean(false)
    private val isStopping = AtomicBoolean(false)

    fun sendStdin(input: String) {
        stdinChannel.trySend(input)
    }

    fun stop() {
        isStopping.set(true)
        stdinChannel.trySend("")
    }

    suspend fun execute(
        mainFile: File,
        projectDir: File,
        config: RunConfig,
        onLog: (ConsoleMessage) -> Unit
    ): ExecutionResult = withContext(Dispatchers.IO) {
        isRunning.set(true)
        isStopping.set(false)
        val startTime = System.currentTimeMillis()
        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()
        val diagnostics = mutableListOf<Diagnostic>()

        try {
            val code = mainFile.readText()
            val environment = PythonEnvironment(projectDir, config.args, stdinChannel, onLog, isStopping)

            val timeoutMs = if (config.timeoutMillis > 0) config.timeoutMillis else 300000L
            val result = withTimeoutOrNull(timeoutMs) {
                try {
                    environment.executeScript(mainFile.name, code)
                    0
                } catch (e: PythonException) {
                    val errMsg = "Traceback (most recent call last):\n  File \"${e.fileName}\", line ${e.lineNumber}\n${e.pyType}: ${e.message}"
                    stderrBuilder.append(errMsg).append("\n")
                    onLog(ConsoleMessage(errMsg, ConsoleMessageType.STDERR))
                    diagnostics.add(
                        Diagnostic(
                            severity = DiagnosticSeverity.ERROR,
                            language = "python",
                            file = e.fileName,
                            line = e.lineNumber,
                            column = 1,
                            message = "${e.pyType}: ${e.message}",
                            rawMessage = errMsg
                        )
                    )
                    1
                } catch (e: Exception) {
                    if (isStopping.get()) {
                        onLog(ConsoleMessage("Process stopped by user.", ConsoleMessageType.SYSTEM))
                        return@withTimeoutOrNull -2
                    }
                    val msg = "Runtime Error: ${e.message}"
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
                    stderr = stderrBuilder.toString(),
                    diagnostics = diagnostics
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
                    stderr = stderrBuilder.toString(),
                    diagnostics = diagnostics
                )
            }
        } finally {
            isRunning.set(false)
        }
    }

    class PythonException(val pyType: String, message: String, val fileName: String, val lineNumber: Int) : RuntimeException(message)

    class PythonEnvironment(
        private val projectDir: File,
        private val cliArgs: List<String>,
        private val stdinChannel: Channel<String>,
        private val onLog: (ConsoleMessage) -> Unit,
        private val isStopping: AtomicBoolean
    ) {
        private val globalScope = mutableMapOf<String, Any?>()

        init {
            // Built-in functions
            globalScope["True"] = true
            globalScope["False"] = false
            globalScope["None"] = null
            globalScope["pi"] = Math.PI
            globalScope["e"] = Math.E
        }

        suspend fun executeScript(fileName: String, script: String) {
            val lines = script.lines()
            var lineIdx = 0
            while (lineIdx < lines.size) {
                if (isStopping.get()) return
                val rawLine = lines[lineIdx]
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    lineIdx++
                    continue
                }

                try {
                    lineIdx = executeStatement(fileName, lines, lineIdx)
                } catch (e: PythonException) {
                    throw e
                } catch (e: Exception) {
                    throw PythonException("RuntimeError", e.message ?: "Unknown error", fileName, lineIdx + 1)
                }
            }
        }

        private suspend fun executeStatement(fileName: String, lines: List<String>, startLineIdx: Int): Int {
            val line = lines[startLineIdx].trim()
            val lineNum = startLineIdx + 1

            // Print statement
            if (line.startsWith("print(") && line.endsWith(")")) {
                val inner = line.substring(6, line.length - 1)
                val args = parseArguments(inner)
                val flush = args.any { it.trim().startsWith("flush=") && it.trim().endsWith("True") }
                val printableArgs = args.filterNot { it.trim().startsWith("flush=") }
                val output = printableArgs.map { evalExpr(it, fileName, lineNum)?.toString() ?: "None" }.joinToString(" ")
                onLog(ConsoleMessage(output, ConsoleMessageType.STDOUT))
                return startLineIdx + 1
            }

            // Input statement
            if (line.contains("input(")) {
                if (line.contains("=")) {
                    val parts = line.split("=", limit = 2)
                    val varName = parts[0].trim()
                    val rhs = parts[1].trim()
                    val prompt = extractInputPrompt(rhs)
                    if (prompt.isNotEmpty()) {
                        onLog(ConsoleMessage(prompt, ConsoleMessageType.INPUT_PROMPT))
                    }
                    val userVal = stdinChannel.receive()
                    globalScope[varName] = userVal
                    return startLineIdx + 1
                }
            }

            // For loop with range
            if (line.startsWith("for ") && line.endsWith(":")) {
                val loopHeader = line.substring(4, line.length - 1)
                val inParts = loopHeader.split(" in ")
                if (inParts.size == 2) {
                    val iterVar = inParts[0].trim()
                    val iterableExpr = inParts[1].trim()
                    
                    // Collect loop body
                    val indent = getIndent(lines[startLineIdx])
                    val bodyLines = mutableListOf<String>()
                    var nextIdx = startLineIdx + 1
                    while (nextIdx < lines.size) {
                        val bl = lines[nextIdx]
                        if (bl.trim().isEmpty()) {
                            bodyLines.add(bl)
                            nextIdx++
                            continue
                        }
                        if (getIndent(bl) > indent) {
                            bodyLines.add(bl)
                            nextIdx++
                        } else {
                            break
                        }
                    }

                    val iterVal = evalExpr(iterableExpr, fileName, lineNum)
                    val items: List<Any?> = when (iterVal) {
                        is List<*> -> iterVal
                        is String -> iterVal.map { it.toString() }
                        is IntRange -> iterVal.toList()
                        else -> emptyList()
                    }

                    for (item in items) {
                        if (isStopping.get()) break
                        globalScope[iterVar] = item
                        executeBlock(fileName, bodyLines)
                    }
                    return nextIdx
                }
            }

            // While loop
            if (line.startsWith("while ") && line.endsWith(":")) {
                val cond = line.substring(6, line.length - 1).trim()
                val indent = getIndent(lines[startLineIdx])
                val bodyLines = mutableListOf<String>()
                var nextIdx = startLineIdx + 1
                while (nextIdx < lines.size) {
                    val bl = lines[nextIdx]
                    if (bl.trim().isEmpty()) {
                        bodyLines.add(bl)
                        nextIdx++
                        continue
                    }
                    if (getIndent(bl) > indent) {
                        bodyLines.add(bl)
                        nextIdx++
                    } else {
                        break
                    }
                }

                var iterations = 0
                while (isTruthy(evalExpr(cond, fileName, lineNum))) {
                    if (isStopping.get()) break
                    iterations++
                    if (iterations > 100000) {
                        throw PythonException("RecursionError", "Maximum loop execution limit exceeded", fileName, lineNum)
                    }
                    executeBlock(fileName, bodyLines)
                }
                return nextIdx
            }

            // If/Elif/Else block
            if (line.startsWith("if ") && line.endsWith(":")) {
                val cond = line.substring(3, line.length - 1).trim()
                val indent = getIndent(lines[startLineIdx])
                val bodyLines = mutableListOf<String>()
                var nextIdx = startLineIdx + 1
                while (nextIdx < lines.size) {
                    val bl = lines[nextIdx]
                    if (bl.trim().isEmpty()) {
                        bodyLines.add(bl)
                        nextIdx++
                        continue
                    }
                    if (getIndent(bl) > indent) {
                        bodyLines.add(bl)
                        nextIdx++
                    } else {
                        break
                    }
                }

                if (isTruthy(evalExpr(cond, fileName, lineNum))) {
                    executeBlock(fileName, bodyLines)
                }
                return nextIdx
            }

            // Time sleep
            if (line.startsWith("time.sleep(")) {
                val inner = line.substring(11, line.length - 1).trim()
                val seconds = evalExpr(inner, fileName, lineNum)?.toString()?.toDoubleOrNull() ?: 1.0
                delay((seconds * 1000).toLong())
                return startLineIdx + 1
            }

            // Imports: import utils / from utils import calculate
            if (line.startsWith("import ") || line.startsWith("from ")) {
                handleImport(line, fileName, lineNum)
                return startLineIdx + 1
            }

            // Variable Assignment (e.g. x = 10 / 0 or name = "Utsav")
            if (line.contains("=") && !line.startsWith("==")) {
                val parts = line.split("=", limit = 2)
                val varName = parts[0].trim()
                val rhs = parts[1].trim()
                val value = evalExpr(rhs, fileName, lineNum)
                globalScope[varName] = value
                return startLineIdx + 1
            }

            // Single expression evaluation (e.g. function call)
            evalExpr(line, fileName, lineNum)
            return startLineIdx + 1
        }

        private suspend fun executeBlock(fileName: String, bodyLines: List<String>) {
            var idx = 0
            while (idx < bodyLines.size) {
                if (isStopping.get()) return
                val bl = bodyLines[idx]
                if (bl.trim().isEmpty() || bl.trim().startsWith("#")) {
                    idx++
                    continue
                }
                idx = executeStatement(fileName, bodyLines, idx)
            }
        }

        private suspend fun handleImport(line: String, currentFile: String, lineNum: Int) {
            val modName = if (line.startsWith("import ")) {
                line.substring(7).trim().split(" ")[0]
            } else {
                line.substring(5).split("import")[0].trim()
            }

            // Standard built-in modules
            if (modName in listOf("math", "time", "random", "sys", "os", "json", "re")) {
                if (modName == "sys") {
                    globalScope["sys.argv"] = listOf(currentFile) + cliArgs
                }
                return
            }

            // Project-local multi-file import (e.g. from utils import test)
            val pyFile = File(projectDir, "$modName.py")
            if (pyFile.exists()) {
                val modCode = pyFile.readText()
                executeScript(pyFile.name, modCode)
            } else {
                throw PythonException("ModuleNotFoundError", "No module named '$modName'", currentFile, lineNum)
            }
        }

        private fun evalExpr(expr: String, fileName: String, lineNum: Int): Any? {
            val trimmed = expr.trim()
            if (trimmed.isEmpty()) return null

            // String literal
            if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                return trimmed.substring(1, trimmed.length - 1)
            }

            // Boolean / None
            if (trimmed == "True") return true
            if (trimmed == "False") return false
            if (trimmed == "None") return null

            // Range function: range(5) or range(1, 5)
            if (trimmed.startsWith("range(") && trimmed.endsWith(")")) {
                val inner = trimmed.substring(6, trimmed.length - 1)
                val args = parseArguments(inner).map { evalExpr(it, fileName, lineNum)?.toString()?.toIntOrNull() ?: 0 }
                return if (args.size == 1) 0 until args[0] else if (args.size >= 2) args[0] until args[1] else 0..0
            }

            // Number literal
            trimmed.toIntOrNull()?.let { return it }
            trimmed.toDoubleOrNull()?.let { return it }

            // Variable lookup
            if (globalScope.containsKey(trimmed)) {
                return globalScope[trimmed]
            }

            // Division by zero check (e.g. 10 / 0 or x / y)
            if (trimmed.contains("/")) {
                val parts = trimmed.split("/")
                if (parts.size == 2) {
                    val left = evalExpr(parts[0], fileName, lineNum)?.toString()?.toDoubleOrNull() ?: 0.0
                    val right = evalExpr(parts[1], fileName, lineNum)?.toString()?.toDoubleOrNull() ?: 0.0
                    if (right == 0.0) {
                        throw PythonException("ZeroDivisionError", "division by zero", fileName, lineNum)
                    }
                    return left / right
                }
            }

            // Addition / concatenation (e.g. "Hello " + name or 5 + 3)
            if (trimmed.contains("+")) {
                val parts = trimmed.split("+")
                val left = evalExpr(parts[0], fileName, lineNum)
                val right = evalExpr(parts[1], fileName, lineNum)
                if (left is String || right is String) {
                    return (left?.toString() ?: "") + (right?.toString() ?: "")
                } else if (left is Number && right is Number) {
                    return left.toDouble() + right.toDouble()
                }
            }

            // List literal: [1, 2, 3]
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val inner = trimmed.substring(1, trimmed.length - 1)
                return parseArguments(inner).map { evalExpr(it, fileName, lineNum) }
            }

            // open("data.txt")
            if (trimmed.startsWith("open(") && trimmed.endsWith(")")) {
                val inner = trimmed.substring(5, trimmed.length - 1)
                val args = parseArguments(inner)
                val relPath = evalExpr(args[0], fileName, lineNum)?.toString() ?: ""
                val targetFile = File(projectDir, relPath)
                if (!targetFile.exists()) {
                    throw PythonException("FileNotFoundError", "No such file or directory: '$relPath'", fileName, lineNum)
                }
                return targetFile.readText()
            }

            // len(x)
            if (trimmed.startsWith("len(") && trimmed.endsWith(")")) {
                val inner = trimmed.substring(4, trimmed.length - 1)
                val v = evalExpr(inner, fileName, lineNum)
                return when (v) {
                    is String -> v.length
                    is List<*> -> v.size
                    else -> 0
                }
            }

            // math.sqrt, math.sin, etc.
            if (trimmed.startsWith("math.")) {
                val funcCall = trimmed.substring(5)
                if (funcCall.startsWith("sqrt(") && funcCall.endsWith(")")) {
                    val arg = evalExpr(funcCall.substring(5, funcCall.length - 1), fileName, lineNum)?.toString()?.toDoubleOrNull() ?: 0.0
                    return sqrt(arg)
                }
            }

            return trimmed
        }

        private fun extractInputPrompt(expr: String): String {
            val idx = expr.indexOf("input(")
            if (idx != -1) {
                val end = expr.indexOf(")", idx)
                if (end != -1) {
                    val inner = expr.substring(idx + 6, end).trim()
                    if ((inner.startsWith("\"") && inner.endsWith("\"")) || (inner.startsWith("'") && inner.endsWith("'"))) {
                        return inner.substring(1, inner.length - 1)
                    }
                }
            }
            return ""
        }

        private fun parseArguments(argsStr: String): List<String> {
            if (argsStr.trim().isEmpty()) return emptyList()
            val list = mutableListOf<String>()
            var current = StringBuilder()
            var inQuote = false
            var quoteChar = ' '

            for (c in argsStr) {
                if ((c == '"' || c == '\'') && !inQuote) {
                    inQuote = true
                    quoteChar = c
                    current.append(c)
                } else if (c == quoteChar && inQuote) {
                    inQuote = false
                    current.append(c)
                } else if (c == ',' && !inQuote) {
                    list.add(current.toString().trim())
                    current = StringBuilder()
                } else {
                    current.append(c)
                }
            }
            if (current.isNotEmpty()) {
                list.add(current.toString().trim())
            }
            return list
        }

        private fun isTruthy(v: Any?): Boolean {
            return when (v) {
                null -> false
                is Boolean -> v
                is Number -> v.toDouble() != 0.0
                is String -> v.isNotEmpty()
                is List<*> -> v.isNotEmpty()
                else -> true
            }
        }

        private fun getIndent(line: String): Int {
            var count = 0
            for (c in line) {
                if (c == ' ') count++
                else if (c == '\t') count += 4
                else break
            }
            return count
        }
    }
}
