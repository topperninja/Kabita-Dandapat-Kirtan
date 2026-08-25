package com.example.execution

data class ExecutionResult(
    val state: ExecutionState,
    val exitCode: Int,
    val durationMillis: Long,
    val stdout: String = "",
    val stderr: String = "",
    val diagnostics: List<Diagnostic> = emptyList(),
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = System.currentTimeMillis()
)
