package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.ToolchainManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ToolchainViewModel(application: Application) : AndroidViewModel(application) {
    private val toolchainManager = ToolchainManager.getInstance(application)

    val deviceAbi = toolchainManager.deviceAbi
    val toolchains = toolchainManager.supportedLanguages
    val installedToolchains = toolchainManager.installedToolchains

    private val _verifyingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val verifyingStates: StateFlow<Map<String, Boolean>> = _verifyingStates.asStateFlow()
    
    private val _verificationLog = MutableStateFlow<String?>(null)
    val verificationLog: StateFlow<String?> = _verificationLog.asStateFlow()

    fun clearLog() {
        _verificationLog.value = null
    }

    fun verifyToolchain(id: String) {
        viewModelScope.launch {
            _verifyingStates.value = _verifyingStates.value + (id to true)
            var logBuffer = ""
            val success = toolchainManager.repairToolchain(id) { msg ->
                logBuffer += msg.text + "\n"
            }
            _verifyingStates.value = _verifyingStates.value - id
            _verificationLog.value = logBuffer.ifEmpty { if (success) "Toolchain is ready." else "Toolchain verification failed." }
        }
    }

    fun installToolchain(id: String) {
        viewModelScope.launch {
            _verifyingStates.value = _verifyingStates.value + (id to true)
            val success = toolchainManager.checkAndInstallToolchain(id)
            _verifyingStates.value = _verifyingStates.value - id
            if (!success) {
                _verificationLog.value = "Failed to activate toolchain for '$id'."
            }
        }
    }

    fun uninstallToolchain(id: String) {
        toolchainManager.uninstallToolchain(id)
    }
}
