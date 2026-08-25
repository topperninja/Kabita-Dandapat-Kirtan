package com.example.execution

import android.content.Context
import java.io.File

interface LanguageAdapter : Toolchain {
    val version: String
    val supportedExtensions: List<String>
    
    suspend fun checkHealth(context: Context, onLog: (ConsoleMessage) -> Unit): Boolean
    suspend fun build(context: Context, projectDir: File, onLog: (ConsoleMessage) -> Unit): Boolean
    suspend fun run(
        context: Context,
        projectDir: File,
        config: RunConfig,
        onLog: (ConsoleMessage) -> Unit
    ): ExecutionResult
    
    fun sendStdin(input: String)
    fun clean(projectDir: File)
    fun parseDiagnostics(rawOutput: String): List<Diagnostic>
}
