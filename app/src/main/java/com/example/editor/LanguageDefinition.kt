package com.example.editor

import androidx.compose.ui.graphics.Color

data class LanguageDefinition(
    val name: String,
    val extensions: List<String>,
    val keywords: List<String>,
    val keywordColor: Color = Color(0xFFC678DD), // Purple
    val stringPattern: Regex = "(\"[^\"]*\"|'[^']*'|`[^`]*`)".toRegex(),
    val stringColor: Color = Color(0xFF98C379), // Green
    val commentPattern: Regex,
    val commentColor: Color = Color(0xFF5C6370), // Gray
    val numberPattern: Regex = "\\b\\d+(\\.\\d+)?\\b".toRegex(),
    val numberColor: Color = Color(0xFFD19A66), // Orange
    val functionPattern: Regex = "\\b[a-zA-Z_]\\w*(?=\\s*\\()".toRegex(),
    val functionColor: Color = Color(0xFF61AFEF), // Blue
    val operatorColor: Color = Color(0xFF56B6C2) // Cyan
)

object LanguageRegistry {
    val languages = listOf(
        LanguageDefinition(
            name = "JavaScript",
            extensions = listOf("js", "mjs", "cjs"),
            keywords = listOf("function", "var", "let", "const", "if", "else", "for", "while", "return", "class", "import", "export", "new", "async", "await"),
            commentPattern = "(//.*|/\\*[\\s\\S]*?\\*/)".toRegex()
        ),
        LanguageDefinition(
            name = "HTML",
            extensions = listOf("html", "htm"),
            keywords = listOf(), // Handled differently for tags
            stringPattern = "(\"[^\"]*\"|'[^']*')".toRegex(),
            commentPattern = "(<!--[\\s\\S]*?-->)".toRegex()
        ),
        LanguageDefinition(
            name = "Python",
            extensions = listOf("py"),
            keywords = listOf("def", "class", "if", "elif", "else", "while", "for", "in", "import", "from", "return", "True", "False", "None", "and", "or", "not"),
            commentPattern = "(#.*)".toRegex()
        ),
        LanguageDefinition(
            name = "C++",
            extensions = listOf("cpp", "cc", "cxx", "h", "hpp"),
            keywords = listOf("int", "float", "double", "char", "void", "class", "struct", "if", "else", "for", "while", "return", "using", "namespace", "include"),
            commentPattern = "(//.*|/\\*[\\s\\S]*?\\*/)".toRegex()
        ),
        LanguageDefinition(
            name = "C",
            extensions = listOf("c", "h"),
            keywords = listOf("int", "float", "double", "char", "void", "struct", "typedef", "if", "else", "for", "while", "return", "include", "switch", "case"),
            commentPattern = "(//.*|/\\*[\\s\\S]*?\\*/)".toRegex()
        ),
        LanguageDefinition(
            name = "C#",
            extensions = listOf("cs"),
            keywords = listOf("public", "private", "protected", "class", "interface", "namespace", "using", "if", "else", "for", "while", "return", "int", "void", "static", "new", "string", "bool"),
            commentPattern = "(//.*|/\\*[\\s\\S]*?\\*/)".toRegex()
        ),
        LanguageDefinition(
            name = "Java",
            extensions = listOf("java"),
            keywords = listOf("public", "private", "protected", "class", "interface", "extends", "implements", "if", "else", "for", "while", "return", "int", "void", "static", "new"),
            commentPattern = "(//.*|/\\*[\\s\\S]*?\\*/)".toRegex()
        )
    )

    fun getLanguageByExtension(filename: String): LanguageDefinition? {
        val ext = filename.substringAfterLast('.', "")
        return languages.find { it.extensions.contains(ext) }
    }
}
