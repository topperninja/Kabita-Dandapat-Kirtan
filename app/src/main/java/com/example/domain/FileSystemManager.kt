package com.example.domain

import android.content.Context
import java.io.File

class FileSystemManager(private val context: Context) {
    
    fun getProjectsDir(): File {
        val dir = File(context.filesDir, "projects")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createProject(name: String): File {
        val projectDir = File(getProjectsDir(), name.replace(Regex("[^a-zA-Z0-9_-]"), "_"))
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        return projectDir
    }

    fun listFiles(dir: File): List<File> {
        return dir.listFiles()?.toList()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
    }

    fun readFile(file: File): String {
        return if (file.exists() && file.isFile) file.readText() else ""
    }

    fun writeFile(file: File, content: String) {
        file.writeText(content)
    }

    fun createFile(parent: File, name: String): File? {
        val file = File(parent, name)
        return if (file.createNewFile()) file else null
    }

    fun createFolder(parent: File, name: String): File? {
        val dir = File(parent, name)
        return if (dir.mkdirs()) dir else null
    }

    fun deleteFile(file: File): Boolean {
        return if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }
}
