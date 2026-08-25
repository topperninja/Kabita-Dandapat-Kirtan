package com.example.execution

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

class ExecutionManager(private val registry: RuntimeRegistry) {
    private val _executionState = MutableStateFlow(ExecutionState.IDLE)
    val executionState: StateFlow<ExecutionState> = _executionState.asStateFlow()
    
    private val _consoleOutput = MutableStateFlow<List<ConsoleMessage>>(emptyList())
    val consoleOutput: StateFlow<List<ConsoleMessage>> = _consoleOutput.asStateFlow()

    private val _executionDuration = MutableStateFlow<Long>(0L)
    val executionDuration: StateFlow<Long> = _executionDuration.asStateFlow()

    private val _activeDiagnostics = MutableStateFlow<List<Diagnostic>>(emptyList())
    val activeDiagnostics: StateFlow<List<Diagnostic>> = _activeDiagnostics.asStateFlow()
    
    private var activeAdapter: LanguageAdapter? = null
    private var timerJob: Job? = null
    
    fun clearConsole() {
        _consoleOutput.value = emptyList()
        _activeDiagnostics.value = emptyList()
        _executionDuration.value = 0L
    }
    
    private fun log(message: ConsoleMessage) {
        val currentList = _consoleOutput.value.toMutableList()
        currentList.add(message)
        if (currentList.size > 1500) {
            currentList.removeAt(0)
        }
        _consoleOutput.value = currentList

        if (message.diagnostic != null) {
            _activeDiagnostics.value = _activeDiagnostics.value + message.diagnostic
        }
    }
    
    suspend fun executeProject(
        context: Context,
        toolchainId: String,
        projectDir: File,
        config: RunConfig = RunConfig()
    ) = withContext(Dispatchers.IO) {
        if (_executionState.value == ExecutionState.RUNNING || _executionState.value == ExecutionState.BUILDING) {
            log(ConsoleMessage("A process is already running. Please stop it first.", ConsoleMessageType.ERROR))
            return@withContext
        }
        
        clearConsole()
        _executionState.value = ExecutionState.PREPARING
        
        val adapter = registry.getAdapter(toolchainId)
        if (adapter == null) {
            _executionState.value = ExecutionState.TOOLCHAIN_MISSING
            log(ConsoleMessage("Language adapter for '$toolchainId' is not registered.", ConsoleMessageType.ERROR))
            return@withContext
        }
        
        activeAdapter = adapter
        val startTime = System.currentTimeMillis()

        // Start live timer
        val coroutineScope = CoroutineScope(Dispatchers.Default)
        timerJob = coroutineScope.launch {
            while (_executionState.value == ExecutionState.PREPARING ||
                   _executionState.value == ExecutionState.BUILDING ||
                   _executionState.value == ExecutionState.RUNNING) {
                _executionDuration.value = System.currentTimeMillis() - startTime
                delay(100)
            }
        }

        try {
            _executionState.value = ExecutionState.BUILDING
            val buildSuccess = adapter.build(context, projectDir) { msg -> log(msg) }
            
            if (!buildSuccess) {
                _executionState.value = ExecutionState.BUILD_FAILED
                _executionDuration.value = System.currentTimeMillis() - startTime
                timerJob?.cancel()
                log(ConsoleMessage("BUILD FAILED", ConsoleMessageType.ERROR))
                activeAdapter = null
                return@withContext
            }
            
            _executionState.value = ExecutionState.RUNNING
            val result = adapter.run(context, projectDir, config) { msg -> log(msg) }
            
            timerJob?.cancel()
            _executionDuration.value = result.durationMillis
            _executionState.value = result.state
            
            when (result.state) {
                ExecutionState.SUCCESS -> {
                    log(ConsoleMessage("Process finished with exit code 0", ConsoleMessageType.SYSTEM))
                }
                ExecutionState.STOPPED -> {
                    log(ConsoleMessage("Process stopped by user.", ConsoleMessageType.SYSTEM))
                }
                ExecutionState.TIMEOUT -> {
                    log(ConsoleMessage("Process terminated due to timeout.", ConsoleMessageType.ERROR))
                }
                ExecutionState.OUTPUT_LIMIT -> {
                    log(ConsoleMessage("Process terminated: output limit exceeded.", ConsoleMessageType.ERROR))
                }
                else -> {
                    log(ConsoleMessage("Process finished with exit code ${result.exitCode}", ConsoleMessageType.ERROR))
                }
            }
        } catch (e: Exception) {
            timerJob?.cancel()
            _executionState.value = ExecutionState.INTERNAL_ERROR
            log(ConsoleMessage("Internal error: ${e.message}", ConsoleMessageType.ERROR))
        } finally {
            activeAdapter = null
        }
    }

    fun sendStdin(input: String) {
        log(ConsoleMessage(input, ConsoleMessageType.STDOUT))
        activeAdapter?.sendStdin(input)
    }
    
    fun stopExecution() {
        activeAdapter?.stop()
        timerJob?.cancel()
        _executionState.value = ExecutionState.STOPPED
    }
}
