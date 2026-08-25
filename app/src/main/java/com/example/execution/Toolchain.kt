package com.example.execution

import java.io.File

interface Toolchain {
    val id: String
    val name: String
    val description: String
    val isAvailable: Boolean
    
    suspend fun checkHealth(onLog: (ConsoleMessage) -> Unit): Boolean
    suspend fun build(projectDir: File, onLog: (ConsoleMessage) -> Unit): Boolean
    suspend fun execute(projectDir: File, onLog: (ConsoleMessage) -> Unit): Int
    fun stop()
}

