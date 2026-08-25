package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ads.BannerAdView
import com.example.ads.RewardedAdManager
import com.example.ui.theme.ClassMastiYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val rewardedAdManager = remember { RewardedAdManager.getInstance(context) }

    val fontSize by viewModel.settingsManager.fontSize.collectAsState()
    val wordWrap by viewModel.settingsManager.wordWrap.collectAsState()
    val showLineNumbers by viewModel.settingsManager.showLineNumbers.collectAsState()
    val autoCloseBrackets by viewModel.settingsManager.autoCloseBrackets.collectAsState()

    var showRewardDialog by remember { mutableStateOf(false) }
    var rewardStatusMessage by remember { mutableStateOf<String?>(null) }

    if (showRewardDialog) {
        AlertDialog(
            onDismissRequest = { showRewardDialog = false },
            icon = { Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = ClassMastiYellow) },
            title = { Text("Support ClassMasti") },
            text = { Text("Watch a sponsor video to support free offline coding tools and earn 50 Bonus Code Studio Credits.") },
            confirmButton = {
                Button(
                    onClick = {
                        showRewardDialog = false
                        if (activity != null && rewardedAdManager.isAdReady()) {
                            rewardedAdManager.showAd(
                                activity = activity,
                                onRewardEarned = { reward ->
                                    rewardStatusMessage = "Thank you for supporting ClassMasti! Earned ${reward.amount} ${reward.type.ifEmpty { "Credits" }}."
                                },
                                onAdClosed = {}
                            )
                        } else {
                            rewardStatusMessage = "Thank you for your support! Bonus credits applied."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClassMastiYellow)
                ) {
                    Text("Watch Sponsor Video", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRewardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (rewardStatusMessage != null) {
        AlertDialog(
            onDismissRequest = { rewardStatusMessage = null },
            title = { Text("Bonus Received") },
            text = { Text(rewardStatusMessage!!) },
            confirmButton = {
                TextButton(onClick = { rewardStatusMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Editor",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            
            ListItem(
                headlineContent = { Text("Font Size") },
                supportingContent = { Text("$fontSize sp") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (fontSize > 8) viewModel.settingsManager.updateFontSize(fontSize - 2) }) {
                            Text("-", style = MaterialTheme.typography.titleLarge)
                        }
                        IconButton(onClick = { if (fontSize < 32) viewModel.settingsManager.updateFontSize(fontSize + 2) }) {
                            Text("+", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            )
            HorizontalDivider()
            
            ListItem(
                headlineContent = { Text("Show Line Numbers") },
                trailingContent = {
                    Switch(
                        checked = showLineNumbers,
                        onCheckedChange = { viewModel.settingsManager.updateShowLineNumbers(it) }
                    )
                }
            )
            HorizontalDivider()
            
            ListItem(
                headlineContent = { Text("Auto-close Brackets & Quotes") },
                supportingContent = { Text("Automatically insert closing characters") },
                trailingContent = {
                    Switch(
                        checked = autoCloseBrackets,
                        onCheckedChange = { viewModel.settingsManager.updateAutoCloseBrackets(it) }
                    )
                }
            )
            HorizontalDivider()
            
            ListItem(
                headlineContent = { Text("Word Wrap") },
                supportingContent = { Text("Wrap long lines") },
                trailingContent = {
                    Switch(
                        checked = wordWrap,
                        onCheckedChange = { viewModel.settingsManager.updateWordWrap(it) },
                        enabled = false
                    )
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BannerAdView()
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Rewards & Community",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text("Support Development") },
                supportingContent = { Text("Watch a short sponsor video to earn free credits") },
                trailingContent = {
                    Button(
                        onClick = { showRewardDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ClassMastiYellow)
                    ) {
                        Icon(Icons.Default.Stars, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Earn Credits", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            
            ListItem(
                headlineContent = { Text("ClassMasti") },
                supportingContent = { Text("Version 1.0.0\nProfessional Offline Mobile IDE with Google Mobile Ads integration") }
            )
        }
    }
}
