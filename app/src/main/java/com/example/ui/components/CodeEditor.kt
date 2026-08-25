package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.editor.LanguageDefinition
import com.example.editor.SmartEditorEngine

@Composable
fun CodeEditor(
    content: String,
    onContentChange: (String) -> Unit,
    language: LanguageDefinition?,
    fontSize: Int = 14,
    showLineNumbers: Boolean = true,
    autoCloseBrackets: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scrollStateV = rememberScrollState()
    val scrollStateH = rememberScrollState()
    val engine = remember { SmartEditorEngine() }
    
    // Internal state handling selection/cursor natively
    var textFieldValue by remember(content) { 
        mutableStateOf(TextFieldValue(text = content, selection = TextRange(content.length)))
    }
    
    val lineCount = remember(textFieldValue.text) { textFieldValue.text.count { it == '\n' } + 1 }
    
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            // Line numbers
            if (showLineNumbers) {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollStateV)
                        .padding(end = 8.dp)
                        .width(IntrinsicSize.Min)
                ) {
                    for (i in 1..lineCount) {
                        Text(
                            text = i.toString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Editor
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    val processedValue = if (autoCloseBrackets) {
                        engine.processTextChange(textFieldValue, newValue, language)
                    } else {
                        newValue
                    }
                    textFieldValue = processedValue
                    if (processedValue.text != content) {
                        onContentChange(processedValue.text)
                    }
                },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.35).sp // Match with line numbers approx
                ),
                visualTransformation = SyntaxHighlighter(language),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollStateV)
                    .horizontalScroll(scrollStateH)
            )
        }
        
        // Coding Toolbar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            val symbols = listOf("{", "}", "(", ")", "[", "]", "<", ">", ";", ":", "\"", "'", "=", "+", "-", "_", "/", "*", "&", "|", "\\")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(symbols) { symbol ->
                    Text(
                        text = symbol,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            val oldText = textFieldValue.text
                            val selectionStart = textFieldValue.selection.start
                            val selectionEnd = textFieldValue.selection.end
                            
                            val newText = oldText.substring(0, selectionStart) + symbol + oldText.substring(selectionEnd)
                            val rawValue = TextFieldValue(newText, TextRange(selectionStart + symbol.length))
                            val processedValue = engine.processTextChange(textFieldValue, rawValue, language)
                            
                            textFieldValue = processedValue
                            onContentChange(processedValue.text)
                        }.padding(4.dp)
                    )
                }
            }
        }
    }
}
