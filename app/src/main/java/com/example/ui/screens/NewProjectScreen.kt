package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ads.BannerAdView
import com.example.ads.InterstitialAdManager
import com.example.domain.ToolchainManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(
    navController: NavController,
    languageId: String,
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val toolchainManager = remember { ToolchainManager.getInstance(context) }
    val language = toolchainManager.supportedLanguages.find { it.id == languageId }
    val interstitialAdManager = remember { InterstitialAdManager.getInstance(context) }

    var projectName by remember { mutableStateOf("My ${language?.name ?: ""} Project") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Project", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                label = { Text("Project Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                BannerAdView()
            }

            Button(
                onClick = {
                    viewModel.createProject(projectName, languageId) { projectId ->
                        if (activity != null && interstitialAdManager.isAdReady()) {
                            interstitialAdManager.showAd(activity) {
                                navController.navigate("editor/$projectId") {
                                    popUpTo("workspace/$languageId")
                                }
                            }
                        } else {
                            navController.navigate("editor/$projectId") {
                                popUpTo("workspace/$languageId")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Create Project")
            }
        }
    }
}
