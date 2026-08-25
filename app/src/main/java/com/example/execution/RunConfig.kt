package com.example.execution

data class RunConfig(
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val timeoutMillis: Long = 30000L,
    val maxOutputBytes: Int = 1024 * 1024 * 5 // 5 MB
)
