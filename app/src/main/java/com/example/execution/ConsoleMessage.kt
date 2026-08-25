package com.example.execution

enum class ConsoleMessageType {
    STDOUT,
    STDERR,
    SYSTEM,
    ERROR,
    WARNING,
    INPUT_PROMPT,
    COMPILER_DIAGNOSTIC
}

data class ConsoleMessage(
    val text: String,
    val type: ConsoleMessageType,
    val timestamp: Long = System.currentTimeMillis(),
    val diagnostic: Diagnostic? = null
)

