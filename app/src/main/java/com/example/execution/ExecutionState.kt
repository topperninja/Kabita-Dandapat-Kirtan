package com.example.execution

enum class ExecutionState {
    IDLE,
    PREPARING,
    BUILDING,
    RUNNING,
    WAITING_FOR_INPUT,
    SUCCESS,
    BUILD_FAILED,
    RUNTIME_ERROR,
    STOPPING,
    STOPPED,
    TIMEOUT,
    OUTPUT_LIMIT,
    TOOLCHAIN_MISSING,
    INTERNAL_ERROR
}

