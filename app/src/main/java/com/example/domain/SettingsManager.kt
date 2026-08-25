package com.example.domain

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("classmasti_settings", Context.MODE_PRIVATE)

    private val _fontSize = MutableStateFlow(prefs.getInt("font_size", 14))
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    private val _wordWrap = MutableStateFlow(prefs.getBoolean("word_wrap", false))
    val wordWrap: StateFlow<Boolean> = _wordWrap.asStateFlow()

    private val _showLineNumbers = MutableStateFlow(prefs.getBoolean("show_line_numbers", true))
    val showLineNumbers: StateFlow<Boolean> = _showLineNumbers.asStateFlow()

    private val _autoCloseBrackets = MutableStateFlow(prefs.getBoolean("auto_close_brackets", true))
    val autoCloseBrackets: StateFlow<Boolean> = _autoCloseBrackets.asStateFlow()

    fun updateFontSize(size: Int) {
        prefs.edit().putInt("font_size", size).apply()
        _fontSize.value = size
    }

    fun updateWordWrap(enabled: Boolean) {
        prefs.edit().putBoolean("word_wrap", enabled).apply()
        _wordWrap.value = enabled
    }

    fun updateShowLineNumbers(enabled: Boolean) {
        prefs.edit().putBoolean("show_line_numbers", enabled).apply()
        _showLineNumbers.value = enabled
    }

    fun updateAutoCloseBrackets(enabled: Boolean) {
        prefs.edit().putBoolean("auto_close_brackets", enabled).apply()
        _autoCloseBrackets.value = enabled
    }

    companion object {
        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
