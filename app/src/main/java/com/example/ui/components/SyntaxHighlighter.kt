package com.example.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.editor.LanguageDefinition

class SyntaxHighlighter(private val language: LanguageDefinition?) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            buildAnnotatedString {
                val str = text.text
                append(str)
                
                if (language != null) {
                    // Keywords
                    if (language.keywords.isNotEmpty()) {
                        val keywordPattern = "\\b(${language.keywords.joinToString("|")})\\b".toRegex()
                        keywordPattern.findAll(str).forEach { match ->
                            addStyle(SpanStyle(color = language.keywordColor), match.range.first, match.range.last + 1)
                        }
                    }
                    
                    // Functions
                    language.functionPattern.findAll(str).forEach { match ->
                        addStyle(SpanStyle(color = language.functionColor), match.range.first, match.range.last + 1)
                    }

                    // Numbers
                    language.numberPattern.findAll(str).forEach { match ->
                        addStyle(SpanStyle(color = language.numberColor), match.range.first, match.range.last + 1)
                    }

                    // Strings
                    language.stringPattern.findAll(str).forEach { match ->
                        addStyle(SpanStyle(color = language.stringColor), match.range.first, match.range.last + 1)
                    }

                    // Comments
                    language.commentPattern.findAll(str).forEach { match ->
                        addStyle(SpanStyle(color = language.commentColor), match.range.first, match.range.last + 1)
                    }
                }
            },
            OffsetMapping.Identity
        )
    }
}
