package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Project
import com.example.domain.FileSystemManager
import com.example.domain.ToolchainManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val fileSystem = FileSystemManager(application)
    val toolchainManager = ToolchainManager.getInstance(application)

    val projects: StateFlow<List<Project>> = db.projectDao().getAllProjects()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val toolchains = toolchainManager.supportedLanguages
    val installedToolchains: StateFlow<Set<String>> = toolchainManager.installedToolchains

    fun createProject(name: String, languageId: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val dir = fileSystem.createProject(name)
            
            // Create template files
            val langDef = toolchainManager.supportedLanguages.find { it.id == languageId }
            val languageName = langDef?.name ?: languageId

            langDef?.templateFiles?.forEach { fileName ->
                val file = fileSystem.createFile(dir, fileName)
                file?.let {
                    if (fileName.endsWith(".html")) {
                        fileSystem.writeFile(it, "<!DOCTYPE html>\n<html>\n<head>\n  <link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n  <h1>Hello Classmasti</h1>\n  <script src=\"script.js\"></script>\n</body>\n</html>")
                    } else if (fileName.endsWith(".cpp") || fileName.endsWith(".java") || fileName.endsWith(".py") || fileName.endsWith(".js")) {
                        fileSystem.writeFile(it, "// $fileName\n")
                    }
                }
            }

            val project = Project(
                name = name,
                path = dir.absolutePath,
                language = languageName, // Save human-readable name in DB
                lastModified = System.currentTimeMillis()
            )
            val id = db.projectDao().insertProject(project)
            onCreated(id)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            try {
                fileSystem.deleteFile(File(project.path))
            } catch (e: Exception) {
                // ignore
            }
            db.projectDao().deleteProject(project)
        }
    }
}
