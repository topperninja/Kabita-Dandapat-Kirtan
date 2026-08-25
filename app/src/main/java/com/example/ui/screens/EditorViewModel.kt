package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Project
import com.example.domain.FileSystemManager
import com.example.domain.SettingsManager
import com.example.domain.ToolchainManager
import com.example.execution.ExecutionManager
import com.example.execution.RunConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val fileSystem = FileSystemManager(application)
    private val toolchainManager = ToolchainManager.getInstance(application)
    val settingsManager = SettingsManager.getInstance(application)
    val executionManager = ExecutionManager(toolchainManager.runtimeRegistry)

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files

    private val _currentFile = MutableStateFlow<File?>(null)
    val currentFile: StateFlow<File?> = _currentFile

    private val _fileContent = MutableStateFlow("")
    val fileContent: StateFlow<String> = _fileContent

    fun loadProject(projectId: Long) {
        viewModelScope.launch {
            val p = db.projectDao().getProjectById(projectId)
            _project.value = p
            p?.let {
                val dir = File(it.path)
                refreshFiles(dir)
                val firstFile = _files.value.firstOrNull { file -> file.isFile }
                firstFile?.let { f -> openFile(f) }
            }
        }
    }

    fun isProjectToolchainInstalled(): Boolean {
        val langName = _project.value?.language ?: return false
        val langDef = toolchainManager.supportedLanguages.find { it.name.equals(langName, ignoreCase = true) || it.id.equals(langName, ignoreCase = true) }
        return langDef?.let { toolchainManager.isInstalled(it.id) } ?: true
    }
    
    fun getProjectLanguageId(): String? {
        val langName = _project.value?.language ?: return null
        return toolchainManager.supportedLanguages.find { 
            it.name.equals(langName, ignoreCase = true) || it.id.equals(langName, ignoreCase = true) 
        }?.id ?: langName.lowercase()
    }
    
    fun runProject() {
        saveCurrentFile()
        val langId = getProjectLanguageId() ?: return
        val projectDir = _project.value?.path?.let { File(it) } ?: return
        
        viewModelScope.launch {
            executionManager.executeProject(
                context = getApplication(),
                toolchainId = langId,
                projectDir = projectDir,
                config = RunConfig(timeoutMillis = 30000L)
            )
        }
    }

    fun sendStdin(input: String) {
        executionManager.sendStdin(input)
    }
    
    fun stopExecution() {
        executionManager.stopExecution()
    }

    fun refreshFiles(dir: File) {
        _files.value = fileSystem.listFiles(dir)
    }

    fun openFile(file: File) {
        _currentFile.value = file
        _fileContent.value = fileSystem.readFile(file)
    }

    fun updateContent(content: String) {
        _fileContent.value = content
    }

    fun saveCurrentFile() {
        _currentFile.value?.let {
            fileSystem.writeFile(it, _fileContent.value)
            
            _project.value?.let { p ->
                viewModelScope.launch {
                    db.projectDao().updateProject(p.copy(lastModified = System.currentTimeMillis()))
                }
            }
        }
    }
    
    fun createFile(name: String) {
        _project.value?.let { p ->
            val dir = File(p.path)
            fileSystem.createFile(dir, name)
            refreshFiles(dir)
        }
    }
}
