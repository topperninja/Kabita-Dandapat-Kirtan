package com.example.execution.adapters

import android.content.Context
import com.example.execution.*
import com.example.execution.engines.EmbeddedPythonEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PythonAdapter : LanguageAdapter {
    override val id = "python"
    override val name = "Python 3"
    override val description = "Python 3 Offline Runtime"
    override val version = "3.11.4"
    override val supportedExtensions = listOf(".py")
    override val isAvailable = true

    private val executor = ProcessExecutor()
    private val embeddedEngine = EmbeddedPythonEngine()
    private var usingNative = false

    private fun getExecutablePaths(context: Context): List<String> {
        val appToolchain = File(context.filesDir, "toolchains/python/bin/python3").absolutePath
        return listOf(
            appToolchain,
            "/data/data/com.termux/files/usr/bin/python",
            "/data/data/com.termux/files/usr/bin/python3",
            "/system/bin/python",
            "/system/bin/python3"
        )
    }

    private fun getValidExecutable(context: Context): String? {
        return getExecutablePaths(context).firstOrNull { File(it).canExecute() }
    }

    override suspend fun checkHealth(context: Context, onLog: (ConsoleMessage) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val nativeBin = getValidExecutable(context)
        if (nativeBin != null) {
            onLog(ConsoleMessage("[$name] Found native runtime: $nativeBin", ConsoleMessageType.SYSTEM))
            val exitCode = executor.execute(listOf(nativeBin, "--version"), context.filesDir, onLog)
            if (exitCode == 0) {
                onLog(ConsoleMessage("[$name] Native Python 3 runtime is READY.", ConsoleMessageType.SYSTEM))
                return@withContext true
            }
        }
        
        // Embedded engine check
        onLog(ConsoleMessage("[$name] Running embedded Python 3 health check...", ConsoleMessageType.SYSTEM))
        val testFile = File(context.cacheDir, "health_test.py").apply {
            writeText("print('CLASSMASTI_PYTHON_OK')")
        }
        val result = embeddedEngine.execute(testFile, context.cacheDir, RunConfig(timeoutMillis = 5000L)) {}
        testFile.delete()
        if (result.state == ExecutionState.SUCCESS) {
            onLog(ConsoleMessage("[$name] Offline Python 3 runtime is READY.", ConsoleMessageType.SYSTEM))
            return@withContext true
        }
        return@withContext false
    }

    override suspend fun checkHealth(onLog: (ConsoleMessage) -> Unit): Boolean {
        // Fallback for parameterless call
        return true
    }

    override suspend fun build(context: Context, projectDir: File, onLog: (ConsoleMessage) -> Unit): Boolean {
        return true
    }

    override suspend fun build(projectDir: File, onLog: (ConsoleMessage) -> Unit): Boolean {
        return true
    }

    override suspend fun run(
        context: Context,
        projectDir: File,
        config: RunConfig,
        onLog: (ConsoleMessage) -> Unit
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val mainFile = File(projectDir, "main.py")
        if (!mainFile.exists()) {
            val err = "Error: main.py not found in project directory."
            onLog(ConsoleMessage(err, ConsoleMessageType.ERROR))
            return@withContext ExecutionResult(
                state = ExecutionState.RUNTIME_ERROR,
                exitCode = 1,
                durationMillis = 0,
                stderr = err
            )
        }

        val nativeBin = getValidExecutable(context)
        if (nativeBin != null) {
            usingNative = true
            val startTime = System.currentTimeMillis()
            val cmd = mutableListOf(nativeBin, "main.py")
            cmd.addAll(config.args)
            val exitCode = executor.execute(
                command = cmd,
                workingDir = projectDir,
                onLog = onLog,
                timeoutMillis = config.timeoutMillis,
                env = config.env,
                maxOutputBytes = config.maxOutputBytes
            )
            val elapsed = System.currentTimeMillis() - startTime
            return@withContext ExecutionResult(
                state = if (exitCode == 0) ExecutionState.SUCCESS else ExecutionState.RUNTIME_ERROR,
                exitCode = exitCode,
                durationMillis = elapsed
            )
        }

        usingNative = false
        return@withContext embeddedEngine.execute(mainFile, projectDir, config, onLog)
    }

    override suspend fun execute(projectDir: File, onLog: (ConsoleMessage) -> Unit): Int {
        val mainFile = File(projectDir, "main.py")
        if (!mainFile.exists()) return -1
        val result = embeddedEngine.execute(mainFile, projectDir, RunConfig(), onLog)
        return result.exitCode
    }

    override fun sendStdin(input: String) {
        if (usingNative) {
            executor.sendStdin(input)
        } else {
            embeddedEngine.sendStdin(input)
        }
    }

    override fun stop() {
        executor.stop()
        embeddedEngine.stop()
    }

    override fun clean(projectDir: File) {}

    override fun parseDiagnostics(rawOutput: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val regex = Regex("""File "([^"]+)", line (\d+)(?:, in .*)?\n\s*(.*)""")
        for (match in regex.findAll(rawOutput)) {
            val file = match.groupValues[1]
            val line = match.groupValues[2].toIntOrNull() ?: 1
            val msg = match.groupValues[3]
            diagnostics.add(
                Diagnostic(
                    severity = DiagnosticSeverity.ERROR,
                    language = "python",
                    file = file,
                    line = line,
                    column = 1,
                    message = msg,
                    rawMessage = match.value
                )
            )
        }
        return diagnostics
    }
}
