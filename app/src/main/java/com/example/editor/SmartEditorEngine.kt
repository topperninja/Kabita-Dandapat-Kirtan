package com.example.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

class SmartEditorEngine {
    
    private val brackets = mapOf(
        '(' to ')',
        '[' to ']',
        '{' to '}'
    )
    
    private val quotes = listOf('"', '\'', '`')

    fun processTextChange(oldValue: TextFieldValue, newValue: TextFieldValue, lang: LanguageDefinition?): TextFieldValue {
        val oldText = oldValue.text
        val newText = newValue.text
        
        // Only process single character insertions
        if (newText.length == oldText.length + 1 && newValue.selection.start == oldValue.selection.start + 1) {
            val insertedChar = newText[newValue.selection.start - 1]
            
            // Handle brackets
            if (brackets.containsKey(insertedChar)) {
                val closingChar = brackets[insertedChar]!!
                val updatedText = newText.substring(0, newValue.selection.start) + closingChar + newText.substring(newValue.selection.start)
                return TextFieldValue(updatedText, selection = newValue.selection) // Cursor stays between
            }
            
            // Handle quotes
            if (quotes.contains(insertedChar)) {
                // Check if we're skipping over an existing closing quote
                if (oldValue.selection.start < oldText.length && oldText[oldValue.selection.start] == insertedChar) {
                    return TextFieldValue(oldText, selection = TextRange(oldValue.selection.start + 1))
                }
                
                val updatedText = newText.substring(0, newValue.selection.start) + insertedChar + newText.substring(newValue.selection.start)
                return TextFieldValue(updatedText, selection = newValue.selection) // Cursor stays between
            }
            
            // Handle closing bracket skip-over
            if (brackets.values.contains(insertedChar)) {
                if (oldValue.selection.start < oldText.length && oldText[oldValue.selection.start] == insertedChar) {
                    return TextFieldValue(oldText, selection = TextRange(oldValue.selection.start + 1))
                }
            }
            
            // Handle Enter key for smart indent
            if (insertedChar == '\n') {
                return handleSmartIndent(oldText, newValue.selection.start - 1)
            }
        }

        return newValue
    }

    private fun handleSmartIndent(text: String, newlineIndex: Int): TextFieldValue {
        // Find previous line indentation
        var lastNewline = text.lastIndexOf('\n', newlineIndex - 1)
        if (lastNewline == -1) lastNewline = 0 else lastNewline += 1
        
        var indent = ""
        while (lastNewline < newlineIndex && (text[lastNewline] == ' ' || text[lastNewline] == '\t')) {
            indent += text[lastNewline]
            lastNewline++
        }
        
        // Increase indent if previous char was {
        val prevCharIndex = newlineIndex - 1
        if (prevCharIndex >= 0 && text[prevCharIndex] == '{') {
            val increasedIndent = indent + "    " // 4 spaces
            
            // Check if next char is } to push it down
            if (newlineIndex + 1 < text.length && text[newlineIndex + 1] == '}') {
                val updatedText = text.substring(0, newlineIndex + 1) + increasedIndent + "\n" + indent + text.substring(newlineIndex + 1)
                return TextFieldValue(updatedText, selection = TextRange(newlineIndex + 1 + increasedIndent.length))
            }
            
            val updatedText = text.substring(0, newlineIndex + 1) + increasedIndent + text.substring(newlineIndex + 1)
            return TextFieldValue(updatedText, selection = TextRange(newlineIndex + 1 + increasedIndent.length))
        }

        val updatedText = text.substring(0, newlineIndex + 1) + indent + text.substring(newlineIndex + 1)
        return TextFieldValue(updatedText, selection = TextRange(newlineIndex + 1 + indent.length))
    }
}
