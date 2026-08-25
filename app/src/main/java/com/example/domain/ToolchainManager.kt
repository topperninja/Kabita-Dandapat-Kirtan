package com.example.domain

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.example.execution.ConsoleMessage
import com.example.execution.ConsoleMessageType
import com.example.execution.RuntimeRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LanguageDef(
    val id: String,
    val name: String,
    val description: String,
    val templateFiles: List<String>,
    val sizeMb: Int,
    val version: String = "1.0.0"
)

data class ToolchainInfo(
    val id: String,
    val name: String,
    val version: String,
    val abi: String,
    val isInstalled: Boolean,
    val isHealthy: Boolean,
    val executablePath: String? = null
)

class ToolchainManager private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("toolchains", Context.MODE_PRIVATE)
    
    val runtimeRegistry = RuntimeRegistry()

    val deviceAbi: String
        get() = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    val supportedLanguages = listOf(
        LanguageDef("web", "Web", "HTML/CSS/JS Engine", listOf("index.html", "style.css", "script.js"), 0, "HTML5/ES2024"),
        LanguageDef("python", "Python", "Python 3 Offline Runtime", listOf("main.py"), 45, "3.11.4"),
        LanguageDef("java", "Java", "Java 17 JDK & Runtime", listOf("Main.java"), 120, "17.0.8"),
        LanguageDef("cpp", "C++", "C++17 GCC/Clang Toolchain", listOf("main.cpp"), 85, "17.0.0"),
        LanguageDef("c", "C", "C11 GCC/Clang Toolchain", listOf("main.c"), 75, "11.0.0"),
        LanguageDef("javascript", "JavaScript", "Node.js / JS Runtime", listOf("index.js"), 30, "18.17.0"),
        LanguageDef("csharp", "C#", ".NET Core Runtime", listOf("Program.cs"), 105, "8.0.0")
    )

    private val _installedToolchains = MutableStateFlow<Set<String>>(
        prefs.getStringSet("installed", setOf("web", "python", "java", "cpp", "c", "javascript", "csharp")) ?: setOf("web", "python", "java", "cpp", "c", "javascript", "csharp")
    )
    val installedToolchains: StateFlow<Set<String>> = _installedToolchains.asStateFlow()

    init {
        // Ensure private toolchain directory exists
        val toolchainDir = File(context.filesDir, "toolchains")
        if (!toolchainDir.exists()) {
            toolchainDir.mkdirs()
        }
    }

    fun isInstalled(id: String): Boolean {
        return _installedToolchains.value.contains(id)
    }

    suspend fun checkAndInstallToolchain(id: String): Boolean = withContext(Dispatchers.IO) {
        if (id == "web") {
            markInstalled(id)
            return@withContext true
        }

        val adapter = runtimeRegistry.getAdapter(id) ?: return@withContext false
        val isHealthy = adapter.checkHealth(context) {}
        if (isHealthy) {
            markInstalled(id)
            return@withContext true
        }
        return@withContext false
    }

    suspend fun repairToolchain(id: String, onLog: (ConsoleMessage) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val adapter = runtimeRegistry.getAdapter(id) ?: return@withContext false
        onLog(ConsoleMessage("[$id] Verifying toolchain health on ABI: $deviceAbi...", ConsoleMessageType.SYSTEM))
        val healthy = adapter.checkHealth(context, onLog)
        if (healthy) {
            markInstalled(id)
            onLog(ConsoleMessage("[$id] Toolchain successfully verified and repaired.", ConsoleMessageType.SYSTEM))
            return@withContext true
        } else {
            onLog(ConsoleMessage("[$id] Toolchain repair failed.", ConsoleMessageType.ERROR))
            return@withContext false
        }
    }

    private fun markInstalled(id: String) {
        val updated = _installedToolchains.value + id
        prefs.edit { putStringSet("installed", updated) }
        _installedToolchains.value = updated
    }

    fun uninstallToolchain(id: String) {
        if (id == "web") return
        val updated = _installedToolchains.value - id
        prefs.edit { putStringSet("installed", updated) }
        _installedToolchains.value = updated
    }

    companion object {
        @Volatile
        private var INSTANCE: ToolchainManager? = null

        fun getInstance(context: Context): ToolchainManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ToolchainManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
