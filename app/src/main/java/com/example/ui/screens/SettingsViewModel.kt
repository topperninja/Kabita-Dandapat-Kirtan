package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.domain.SettingsManager

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    val settingsManager = SettingsManager.getInstance(application)
}
