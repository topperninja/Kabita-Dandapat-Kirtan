package com.example.execution.adapters

import android.content.Context
import com.example.execution.*
import com.example.execution.engines.EmbeddedJavaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class JavaAdapter : LanguageAdapter {
    override val id = "java"
    override val name = "Java"
    override val description = "Java 17 JDK & Runtime"
    override val version = "17.0.8"
    override val supportedExtensions = listOf(".java")
    override val isAvailable = true

    private val executor = ProcessExecutor()
    private val embeddedEngine = EmbeddedJavaEngine()
    private var usingNative = false

    private fun getCompilerPaths(context: Context): List<String> {
        val appToolchain = File(context.filesDir, "toolchains/java/bin/javac").absolutePath
        return listOf(
            appToolchain,
            "/data/data/com.termux/files/usr/bin/javac",
            "/system/bin/javac"
        )
    }

    private fun getExecutablePaths(context: Context): List<String> {
        val appToolchain = File(context.filesDir, "toolchains/java/bin/java").absolutePath
        return listOf(
            appToolchain,
            "/data/data/com.termux/files/usr/bin/java",
            "/system/bin/java"
        )
    }

    private fun getValidCompiler(context: Context): String? {
        return getCompilerPaths(context).firstOrNull { File(it).canExecute() }
    }

    private fun getValidExecutable(context: Context): String? {
        return getExecutablePaths(context).firstOrNull { File(it).canExecute() }
    }

    override suspend fun checkHealth(context: Context, onLog: (ConsoleMessage) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val nativeCompiler = getValidCompiler(context)
        val nativeRuntime = getValidExecutable(context)
        if (nativeCompiler != null && nativeRuntime != null) {
            onLog(ConsoleMessage("[$name] Found native Java toolchain.", ConsoleMessageType.SYSTEM))
            val exitCode = executor.execute(listOf(nativeRuntime, "-version"), context.filesDir, onLog)
            if (exitCode == 0) {
                onLog(ConsoleMessage("[$name] Native Java 17 toolchain is READY.", ConsoleMessageType.SYSTEM))
                return@withContext true
            }
        }

        onLog(ConsoleMessage("[$name] Running offline Java health check...", ConsoleMessageType.SYSTEM))
        val testFile = File(context.cacheDir, "Main.java").apply {
            writeText("public class Main { public static void main(String[] args) { System.out.println(\"CLASSMASTI_JAVA_OK\"); } }")
        }
        val result = embeddedEngine.execute(testFile, context.cacheDir, RunConfig(timeoutMillis = 5000L)) {}
        testFile.delete()
        if (result.state == ExecutionState.SUCCESS) {
            onLog(ConsoleMessage("[$name] Offline Java 17 toolchain is READY.", ConsoleMessageType.SYSTEM))
            return@withContext true
        }
        return@withContext false
    }

    override suspend fun checkHealth(onLog: (ConsoleMessage) -> Unit): Boolean {
        return true
    }

    override suspend fun build(context: Context, projectDir: File, onLog: (ConsoleMessage) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val nativeCompiler = getValidCompiler(context)
        if (nativeCompiler != null) {
            val javaFiles = projectDir.listFiles { _, name -> name.endsWith(".java") }?.toList() ?: emptyList()
            if (javaFiles.isEmpty()) {
                onLog(ConsoleMessage("Error: No .java files found.", ConsoleMessageType.ERROR))
                return@withContext false
            }
            val cmd = mutableListOf(nativeCompiler)
            cmd.addAll(javaFiles.map { it.name })
            onLog(ConsoleMessage("Compiling Java project...", ConsoleMessageType.SYSTEM))
            val exitCode = executor.execute(cmd, projectDir, onLog)
            return@withContext exitCode == 0
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
        val mainFile = File(projectDir, "Main.java")
        if (!mainFile.exists()) {
            val firstJava = projectDir.listFiles { _, name -> name.endsWith(".java") }?.firstOrNull()
            if (firstJava == null) {
                val err = "Error: No .java file found."
                onLog(ConsoleMessage(err, ConsoleMessageType.ERROR))
                return@withContext ExecutionResult(state = ExecutionState.RUNTIME_ERROR, exitCode = 1, durationMillis = 0, stderr = err)
            }
        }

        val nativeExecutable = getValidExecutable(context)
        if (nativeExecutable != null && File(projectDir, "Main.class").exists()) {
            usingNative = true
            val startTime = System.currentTimeMillis()
            val cmd = mutableListOf(nativeExecutable, "Main")
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
        val target = if (mainFile.exists()) mainFile else projectDir.listFiles { _, name -> name.endsWith(".java") }!!.first()
        return@withContext embeddedEngine.execute(target, projectDir, config, onLog)
    }

    override suspend fun execute(projectDir: File, onLog: (ConsoleMessage) -> Unit): Int {
        val mainFile = File(projectDir, "Main.java")
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
        projectDir.listFiles { _, name -> name.endsWith(".class") }?.forEach { it.delete() }
    }

    override fun parseDiagnostics(rawOutput: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val regex = Regex("""([^:\n]+):(\d+):(?:\s*(\d+):)?\s*(error|warning):\s*(.*)""")
        for (match in regex.findAll(rawOutput)) {
            val file = match.groupValues[1].trim()
            val line = match.groupValues[2].toIntOrNull() ?: 1
            val col = match.groupValues[3].toIntOrNull() ?: 1
            val severityStr = match.groupValues[4]
            val msg = match.groupValues[5].trim()
            diagnostics.add(
                Diagnostic(
                    severity = if (severityStr == "error") DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING,
                    language = "java",
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
