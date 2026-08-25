package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.LanguageDef
import com.example.ui.theme.ClassMastiYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolchainScreen(
    onNavigateBack: () -> Unit,
    viewModel: ToolchainViewModel = viewModel()
) {
    val installed by viewModel.installedToolchains.collectAsState()
    val verifyingStates by viewModel.verifyingStates.collectAsState()
    val verificationLog by viewModel.verificationLog.collectAsState()
    val toolchains = viewModel.toolchains
    val deviceAbi = viewModel.deviceAbi

    if (verificationLog != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearLog() },
            title = { Text("Toolchain Status") },
            text = { 
                Text(
                    text = verificationLog!!,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                ) 
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearLog() }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Toolchains") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Execution Engine Status",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Surface(
                                color = ClassMastiYellow,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "ABI: $deviceAbi",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Real offline execution pipeline enabled. All language runtimes and compilers run 100% locally on your device without any external network dependency.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            items(toolchains) { lang ->
                ToolchainCard(
                    lang = lang,
                    isInstalled = installed.contains(lang.id),
                    isVerifying = verifyingStates[lang.id] == true,
                    onVerify = { viewModel.verifyToolchain(lang.id) },
                    onInstall = { viewModel.installToolchain(lang.id) },
                    onUninstall = { viewModel.uninstallToolchain(lang.id) }
                )
            }
        }
    }
}

@Composable
fun ToolchainCard(
    lang: LanguageDef,
    isInstalled: Boolean,
    isVerifying: Boolean,
    onVerify: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(lang.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("v${lang.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(lang.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                when {
                    isVerifying -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    isInstalled -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onVerify) {
                                Icon(Icons.Default.Refresh, contentDescription = "Verify health", tint = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.Default.CheckCircle, contentDescription = "Installed", tint = Color(0xFF4CAF50))
                            if (lang.id != "web") {
                                IconButton(onClick = onUninstall) {
                                    Icon(Icons.Default.Delete, contentDescription = "Uninstall", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    else -> {
                        Button(onClick = onInstall, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Activate")
                        }
                    }
                }
            }
        }
    }
}
