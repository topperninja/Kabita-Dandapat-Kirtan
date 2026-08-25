package com.example.execution.adapters

import android.content.Context
import com.example.execution.*
import com.example.execution.engines.EmbeddedCSharpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CSharpAdapter : LanguageAdapter {
    override val id = "csharp"
    override val name = "C#"
    override val description = ".NET Core / C# Runtime"
    override val version = "8.0.0"
    override val supportedExtensions = listOf(".cs")
    override val isAvailable = true

    private val executor = ProcessExecutor()
    private val embeddedEngine = EmbeddedCSharpEngine()
    private var usingNative = false

    private fun getExecutablePaths(context: Context): List<String> {
        val appToolchain = File(context.filesDir, "toolchains/csharp/bin/dotnet").absolutePath
        return listOf(
            appToolchain,
            "/data/data/com.termux/files/usr/bin/dotnet",
            "/data/data/com.termux/files/usr/bin/mono",
            "/system/bin/dotnet"
        )
    }

    private fun getValidExecutable(context: Context): String? {
        return getExecutablePaths(context).firstOrNull { File(it).canExecute() }
    }

    override suspend fun checkHealth(context: Context, onLog: (ConsoleMessage) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val nativeBin = getValidExecutable(context)
        if (nativeBin != null) {
            val exitCode = executor.execute(listOf(nativeBin, "--version"), context.filesDir, onLog)
            if (exitCode == 0) {
                onLog(ConsoleMessage("[$name] Native .NET runtime is READY.", ConsoleMessageType.SYSTEM))
                return@withContext true
            }
        }

        onLog(ConsoleMessage("[$name] Running offline C# health check...", ConsoleMessageType.SYSTEM))
        val testFile = File(context.cacheDir, "Program.cs").apply {
            writeText("using System;\nclass Program { static void Main() { Console.WriteLine(\"CLASSMASTI_CSHARP_OK\"); } }")
        }
        val result = embeddedEngine.execute(testFile, context.cacheDir, RunConfig(timeoutMillis = 5000L)) {}
        testFile.delete()
        if (result.state == ExecutionState.SUCCESS) {
            onLog(ConsoleMessage("[$name] Offline C# runtime is READY.", ConsoleMessageType.SYSTEM))
            return@withContext true
        }
        return@withContext false
    }

    override suspend fun checkHealth(onLog: (ConsoleMessage) -> Unit): Boolean = true

    override suspend fun build(context: Context, projectDir: File, onLog: (ConsoleMessage) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val (success, _) = embeddedEngine.validateAndBuild(projectDir, onLog)
        return@withContext success
    }

    override suspend fun build(projectDir: File, onLog: (ConsoleMessage) -> Unit): Boolean {
        val (success, _) = embeddedEngine.validateAndBuild(projectDir, onLog)
        return success
    }

    override suspend fun run(
        context: Context,
        projectDir: File,
        config: RunConfig,
        onLog: (ConsoleMessage) -> Unit
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val mainFile = File(projectDir, "Program.cs")
        val target = if (mainFile.exists()) mainFile else projectDir.listFiles { _, name -> name.endsWith(".cs") }?.firstOrNull()
        if (target == null) {
            val err = "Error: No .cs source file found."
            onLog(ConsoleMessage(err, ConsoleMessageType.ERROR))
            return@withContext ExecutionResult(state = ExecutionState.RUNTIME_ERROR, exitCode = 1, durationMillis = 0, stderr = err)
        }

        val nativeBin = getValidExecutable(context)
        if (nativeBin != null) {
            usingNative = true
            val startTime = System.currentTimeMillis()
            val cmd = mutableListOf(nativeBin, "run")
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
        return@withContext embeddedEngine.execute(target, projectDir, config, onLog)
    }

    override suspend fun execute(projectDir: File, onLog: (ConsoleMessage) -> Unit): Int {
        val mainFile = File(projectDir, "Program.cs")
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
        val regex = Regex("""([^(]+)\((\d+),(\d+)\):\s*(error|warning)\s*([^:]+):\s*(.*)""")
        for (match in regex.findAll(rawOutput)) {
            val file = match.groupValues[1].trim()
            val line = match.groupValues[2].toIntOrNull() ?: 1
            val col = match.groupValues[3].toIntOrNull() ?: 1
            val sev = match.groupValues[4]
            val code = match.groupValues[5]
            val msg = match.groupValues[6].trim()
            diagnostics.add(
                Diagnostic(
                    severity = if (sev == "error") DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING,
                    language = "csharp",
                    file = file,
                    line = line,
                    column = col,
                    message = "$code: $msg",
                    rawMessage = match.value
                )
            )
        }
        return diagnostics
    }
}
