package com.example.execution.adapters

import android.content.Context
import com.example.execution.*
import com.example.execution.engines.EmbeddedCppEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CAdapter : LanguageAdapter {
    override val id = "c"
    override val name = "C"
    override val description = "C11 GCC/Clang Toolchain"
    override val version = "11.0.0"
    override val supportedExtensions = listOf(".c", ".h")
    override val isAvailable = true

    private val executor = ProcessExecutor()
    private val embeddedEngine = EmbeddedCppEngine()
    private var usingNative = false

    private fun getCompilerPaths(context: Context): List<String> {
        val appToolchain = File(context.filesDir, "toolchains/c/bin/clang").absolutePath
        return listOf(
            appToolchain,
            "/data/data/com.termux/files/usr/bin/clang",
            "/data/data/com.termux/files/usr/bin/gcc",
            "/system/bin/clang"
        )
    }

    private fun getValidCompiler(context: Context): String? {
        return getCompilerPaths(context).firstOrNull { File(it).canExecute() }
    }

    override suspend fun checkHealth(context: Context, onLog: (ConsoleMessage) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val nativeCompiler = getValidCompiler(context)
        if (nativeCompiler != null) {
            val exitCode = executor.execute(listOf(nativeCompiler, "--version"), context.filesDir, onLog)
            if (exitCode == 0) {
                onLog(ConsoleMessage("[$name] Native Clang/C compiler is READY.", ConsoleMessageType.SYSTEM))
                return@withContext true
            }
        }

        onLog(ConsoleMessage("[$name] Running offline C health check...", ConsoleMessageType.SYSTEM))
        val testFile = File(context.cacheDir, "main.c").apply {
            writeText("#include <stdio.h>\nint main() { printf(\"CLASSMASTI_C_OK\\n\"); return 0; }")
        }
        val result = embeddedEngine.execute(testFile, context.cacheDir, RunConfig(timeoutMillis = 5000L)) {}
        testFile.delete()
        if (result.state == ExecutionState.SUCCESS) {
            onLog(ConsoleMessage("[$name] Offline C11 toolchain is READY.", ConsoleMessageType.SYSTEM))
            return@withContext true
        }
        return@withContext false
    }

    override suspend fun checkHealth(onLog: (ConsoleMessage) -> Unit): Boolean = true

    override suspend fun build(context: Context, projectDir: File, onLog: (ConsoleMessage) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val nativeCompiler = getValidCompiler(context)
        if (nativeCompiler != null) {
            val cFiles = projectDir.listFiles { _, name -> name.endsWith(".c") }?.toList() ?: emptyList()
            if (cFiles.isEmpty()) {
                onLog(ConsoleMessage("Error: No .c source files found.", ConsoleMessageType.ERROR))
                return@withContext false
            }
            val outFile = File(projectDir, "a.out")
            val cmd = mutableListOf(nativeCompiler, "-std=c11")
            cmd.addAll(cFiles.map { it.name })
            cmd.add("-o")
            cmd.add(outFile.name)
            onLog(ConsoleMessage("Building C project...", ConsoleMessageType.SYSTEM))
            val exitCode = executor.execute(cmd, projectDir, onLog)
            return@withContext exitCode == 0 && outFile.exists()
        }

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
        val mainFile = File(projectDir, "main.c")
        val outFile = File(projectDir, "a.out")
        val nativeCompiler = getValidCompiler(context)

        if (nativeCompiler != null && outFile.exists()) {
            usingNative = true
            val startTime = System.currentTimeMillis()
            val cmd = mutableListOf(outFile.absolutePath)
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
        val target = if (mainFile.exists()) mainFile else projectDir.listFiles { _, name -> name.endsWith(".c") }?.firstOrNull()
        if (target == null) {
            val err = "Error: No .c source file found."
            onLog(ConsoleMessage(err, ConsoleMessageType.ERROR))
            return@withContext ExecutionResult(state = ExecutionState.RUNTIME_ERROR, exitCode = 1, durationMillis = 0, stderr = err)
        }
        return@withContext embeddedEngine.execute(target, projectDir, config, onLog)
    }

    override suspend fun execute(projectDir: File, onLog: (ConsoleMessage) -> Unit): Int {
        val mainFile = File(projectDir, "main.c")
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

    override fun clean(projectDir: File) {
        File(projectDir, "a.out").delete()
    }

    override fun parseDiagnostics(rawOutput: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val regex = Regex("""([^:\n]+):(\d+):(\d+):\s*(error|warning|fatal error):\s*(.*)""")
        for (match in regex.findAll(rawOutput)) {
            val file = match.groupValues[1].trim()
            val line = match.groupValues[2].toIntOrNull() ?: 1
            val col = match.groupValues[3].toIntOrNull() ?: 1
            val sev = match.groupValues[4]
            val msg = match.groupValues[5].trim()
            diagnostics.add(
                Diagnostic(
                    severity = if (sev.contains("error")) DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING,
                    language = "c",
                    file = file,
                    line = line,
                    column = col,
                    message = msg,
                    rawMessage = match.value
                )
            )
        }
        return diagnostics
    }
}
