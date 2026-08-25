package com.example.execution

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

data class Diagnostic(
    val severity: DiagnosticSeverity,
    val language: String,
    val file: String,
    val line: Int,
    val column: Int = 1,
    val endLine: Int = line,
    val endColumn: Int = column,
    val message: String,
    val rawMessage: String
)
