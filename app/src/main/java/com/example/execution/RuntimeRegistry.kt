package com.example.execution

import com.example.execution.adapters.*

class RuntimeRegistry {
    private val adapters = mapOf<String, LanguageAdapter>(
        "python" to PythonAdapter(),
        "java" to JavaAdapter(),
        "cpp" to CppAdapter(),
        "c" to CAdapter(),
        "javascript" to JavaScriptAdapter(),
        "csharp" to CSharpAdapter()
    )

    fun getAdapter(id: String): LanguageAdapter? = adapters[id]

    fun getToolchain(id: String): Toolchain? = adapters[id]

    fun getAllAdapters(): List<LanguageAdapter> = adapters.values.toList()

    fun getAllToolchains(): List<Toolchain> = adapters.values.toList()
}
