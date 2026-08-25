package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ads.BannerAdView
import com.example.ads.NativeAdCard
import com.example.ads.RewardedInterstitialAdManager
import com.example.data.Project
import com.example.domain.ToolchainManager
import com.example.ui.theme.ClassMastiYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageWorkspaceScreen(
    navController: NavController,
    languageId: String,
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val toolchainManager = remember { ToolchainManager.getInstance(context) }
    val language = toolchainManager.supportedLanguages.find { it.id == languageId }
    
    val allProjects by viewModel.projects.collectAsState(initial = emptyList())
    val languageProjects = allProjects.filter { it.language == language?.name || it.language == languageId }

    var showTurboDialog by remember { mutableStateOf(false) }
    var turboStatusText by remember { mutableStateOf<String?>(null) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }

    val rewardedInterstitialManager = remember { RewardedInterstitialAdManager.getInstance(context) }

    if (showTurboDialog) {
        AlertDialog(
            onDismissRequest = { showTurboDialog = false },
            icon = { Icon(Icons.Default.Bolt, contentDescription = null, tint = ClassMastiYellow) },
            title = { Text("Turbo Compiler Optimization") },
            text = {
                Text(
                    "Watch a short sponsored message to unlock Turbo Local Compiler Optimization for all ${language?.name ?: ""} projects (grants 10 Instant Turbo Execution Boosts)."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTurboDialog = false
                        if (activity != null && rewardedInterstitialManager.isAdReady()) {
                            rewardedInterstitialManager.showAd(
                                activity = activity,
                                onRewardEarned = { reward ->
                                    turboStatusText = "Unlocked ${reward.amount} Turbo Boosts successfully!"
                                },
                                onAdClosed = {}
                            )
                        } else {
                            turboStatusText = "Turbo compiler prewarm activated locally!"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClassMastiYellow)
                ) {
                    Text("Watch & Unlock", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTurboDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (turboStatusText != null) {
        AlertDialog(
            onDismissRequest = { turboStatusText = null },
            title = { Text("Turbo Status") },
            text = { Text(turboStatusText!!) },
            confirmButton = {
                TextButton(onClick = { turboStatusText = null }) {
                    Text("OK")
                }
            }
        )
    }

    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project") },
            text = { Text("Are you sure you want to delete '${projectToDelete?.name}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val target = projectToDelete
                        if (target != null) {
                            viewModel.deleteProject(target)
                        }
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${language?.name ?: "Language"} Projects", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showTurboDialog = true }) {
                        Icon(Icons.Default.Bolt, contentDescription = "Turbo Optimization", tint = ClassMastiYellow)
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate("new_project/$languageId")
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Project")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (languageProjects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No ${language?.name ?: ""} projects yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create your first project to start coding offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BannerAdView()
                    Spacer(modifier = Modifier.height(16.dp))
                    NativeAdCard()
                }
            }
        } else {
            val halfProjects = languageProjects.size / 2
            val firstHalf = languageProjects.take(halfProjects)
            val secondHalf = languageProjects.drop(halfProjects)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(firstHalf) { project ->
                    var showMenu by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate("editor/${project.id}")
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${project.language} • Local Storage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Open in Editor") },
                                        onClick = {
                                            showMenu = false
                                            navController.navigate("editor/${project.id}")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMenu = false
                                            projectToDelete = project
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BannerAdView()
                    }
                }

                items(secondHalf) { project ->
                    var showMenu by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate("editor/${project.id}")
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${project.language} • Local Storage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Open in Editor") },
                                        onClick = {
                                            showMenu = false
                                            navController.navigate("editor/${project.id}")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMenu = false
                                            projectToDelete = project
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    NativeAdCard()
                }
            }
        }
    }
}
