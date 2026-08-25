package com.example.execution.adapters

import android.content.Context
import com.example.execution.*
import com.example.execution.engines.EmbeddedJsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class JavaScriptAdapter : LanguageAdapter {
    override val id = "javascript"
    override val name = "JavaScript"
    override val description = "Node.js / JavaScript Runtime"
    override val version = "18.17.0"
    override val supportedExtensions = listOf(".js", ".mjs", ".cjs")
    override val isAvailable = true

    private val executor = ProcessExecutor()
    private val embeddedEngine = EmbeddedJsEngine()
    private var usingNative = false

    private fun getExecutablePaths(context: Context): List<String> {
        val appToolchain = File(context.filesDir, "toolchains/javascript/bin/node").absolutePath
        return listOf(
            appToolchain,
            "/data/data/com.termux/files/usr/bin/node",
            "/system/bin/node"
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
                onLog(ConsoleMessage("[$name] Native Node.js runtime is READY.", ConsoleMessageType.SYSTEM))
                return@withContext true
            }
        }

        onLog(ConsoleMessage("[$name] Running offline JavaScript health check...", ConsoleMessageType.SYSTEM))
        val testFile = File(context.cacheDir, "test.js").apply {
            writeText("console.log('CLASSMASTI_JS_OK');")
        }
        val result = embeddedEngine.execute(testFile, context.cacheDir, RunConfig(timeoutMillis = 5000L)) {}
        testFile.delete()
        if (result.state == ExecutionState.SUCCESS) {
            onLog(ConsoleMessage("[$name] Offline JavaScript runtime is READY.", ConsoleMessageType.SYSTEM))
            return@withContext true
        }
        return@withContext false
    }

    override suspend fun checkHealth(onLog: (ConsoleMessage) -> Unit): Boolean = true

    override suspend fun build(context: Context, projectDir: File, onLog: (ConsoleMessage) -> Unit): Boolean = true

    override suspend fun build(projectDir: File, onLog: (ConsoleMessage) -> Unit): Boolean = true

    override suspend fun run(
        context: Context,
        projectDir: File,
        config: RunConfig,
        onLog: (ConsoleMessage) -> Unit
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val mainFile = File(projectDir, "index.js")
        val target = if (mainFile.exists()) mainFile else projectDir.listFiles { _, name -> name.endsWith(".js") }?.firstOrNull()
        if (target == null) {
            val err = "Error: No .js file found."
            onLog(ConsoleMessage(err, ConsoleMessageType.ERROR))
            return@withContext ExecutionResult(state = ExecutionState.RUNTIME_ERROR, exitCode = 1, durationMillis = 0, stderr = err)
        }

        val nativeBin = getValidExecutable(context)
        if (nativeBin != null) {
            usingNative = true
            val startTime = System.currentTimeMillis()
            val cmd = mutableListOf(nativeBin, target.name)
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
        val mainFile = File(projectDir, "index.js")
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
        return emptyList()
    }
}
