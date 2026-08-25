package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.editor.LanguageRegistry
import com.example.ui.components.CodeEditor
import com.example.ui.components.FileTree
import com.example.ui.components.HtmlPreview
import com.example.execution.ExecutionState
import com.example.execution.ConsoleMessageType
import kotlinx.coroutines.launch
import java.io.File
import com.example.ui.theme.ClassMastiYellow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

import android.app.Activity
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.ui.platform.LocalContext
import com.example.ads.InterstitialAdManager
import com.example.ads.RewardedAdManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: Long,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val interstitialAdManager = remember { InterstitialAdManager.getInstance(context) }
    val rewardedAdManager = remember { RewardedAdManager.getInstance(context) }

    val project by viewModel.project.collectAsState()
    val files by viewModel.files.collectAsState()
    val currentFile by viewModel.currentFile.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    
    val executionState by viewModel.executionManager.executionState.collectAsState()
    val consoleOutput by viewModel.executionManager.consoleOutput.collectAsState()
    val executionDuration by viewModel.executionManager.executionDuration.collectAsState()

    var showPreview by remember { mutableStateOf(false) }
    var webConsoleLogs by remember { mutableStateOf(listOf<String>()) }
    var stdinInput by remember { mutableStateOf("") }
    var showRewardDialog by remember { mutableStateOf(false) }
    var rewardStatusMessage by remember { mutableStateOf<String?>(null) }

    val handleBackWithInterstitial = {
        if (activity != null && interstitialAdManager.isAdReady()) {
            interstitialAdManager.showAd(activity) {
                onNavigateBack()
            }
        } else {
            onNavigateBack()
        }
    }

    if (showRewardDialog) {
        AlertDialog(
            onDismissRequest = { showRewardDialog = false },
            icon = { Icon(Icons.Default.Stars, contentDescription = null, tint = ClassMastiYellow) },
            title = { Text("Unlock Pro Coding Tools") },
            text = {
                Text("Watch a sponsor video to unlock Offline Performance Profiling and AI Optimization Tips for this project session.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRewardDialog = false
                        if (activity != null && rewardedAdManager.isAdReady()) {
                            rewardedAdManager.showAd(
                                activity = activity,
                                onRewardEarned = { reward ->
                                    rewardStatusMessage = "Unlocked ${reward.type.ifEmpty { "Pro Profiler" }}! Offline optimization active."
                                },
                                onAdClosed = {}
                            )
                        } else {
                            rewardStatusMessage = "Offline optimization active for this session!"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClassMastiYellow)
                ) {
                    Text("Watch Video", color = Color.Black, fontWeight = FontWeight.Bold)
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
            title = { Text("Reward Granted") },
            text = { Text(rewardStatusMessage!!) },
            confirmButton = {
                TextButton(onClick = { rewardStatusMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val scaffoldContent = @Composable {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(project?.name ?: "Editor", style = MaterialTheme.typography.titleMedium)
                            Text(
                                project?.language ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        if (isCompact) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        } else {
                            IconButton(onClick = handleBackWithInterstitial) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showRewardDialog = true }) {
                            Icon(Icons.Default.Stars, contentDescription = "Pro Tools", tint = ClassMastiYellow)
                        }
                        IconButton(onClick = { viewModel.saveCurrentFile() }) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                        
                        val isRunning = executionState == ExecutionState.RUNNING || executionState == ExecutionState.BUILDING || executionState == ExecutionState.PREPARING
                        
                        if (isRunning) {
                            Button(
                                onClick = { viewModel.stopExecution() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onError)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (executionState == ExecutionState.BUILDING) "Building..." else "Stop",
                                    color = MaterialTheme.colorScheme.onError
                                )
                            }
                        } else if (project?.language == "Web" || project?.language == "HTML/CSS/JS Engine") {
                            Button(
                                onClick = { 
                                    viewModel.saveCurrentFile()
                                    webConsoleLogs = emptyList()
                                    showPreview = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ClassMastiYellow)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Preview", tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Preview", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    showPreview = true
                                    viewModel.runProject()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ClassMastiYellow)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Run", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Row(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (!isCompact) {
                    Box(
                        modifier = Modifier
                            .weight(0.25f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        FileTree(
                            files = files,
                            currentFile = currentFile,
                            onFileSelected = { viewModel.openFile(it) }
                        )
                    }
                }

                if (!isCompact && showPreview) {
                    // Split view on tablets / expanded screens
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                    ) {
                        currentFile?.let { file ->
                            val language = remember(file.name) { LanguageRegistry.getLanguageByExtension(file.name) }
                            val fontSize by viewModel.settingsManager.fontSize.collectAsState()
                            val showLineNumbers by viewModel.settingsManager.showLineNumbers.collectAsState()
                            val autoCloseBrackets by viewModel.settingsManager.autoCloseBrackets.collectAsState()

                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (language != null) {
                                    Text(
                                        text = language.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalDivider()
                            CodeEditor(
                                content = fileContent,
                                onContentChange = { viewModel.updateContent(it) },
                                language = language,
                                fontSize = fontSize,
                                showLineNumbers = showLineNumbers,
                                autoCloseBrackets = autoCloseBrackets,
                                modifier = Modifier.fillMaxSize()
                            )
                        } ?: run {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Text("Select a file to edit")
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(if (isCompact) 1f else if (showPreview) 0.35f else 0.75f)
                        .fillMaxHeight()
                ) {
                    if (showPreview) {
                        val isWeb = project?.language == "Web" || project?.language == "HTML/CSS/JS Engine"
                        
                        if (isWeb) {
                            val indexHtml = files.find { it.name == "index.html" }?.readText() ?: ""
                            val styleCss = files.find { it.name == "style.css" }?.readText() ?: ""
                            val scriptJs = files.find { it.name == "script.js" }?.readText() ?: ""

                            var html = indexHtml
                            if (html.contains("<link rel=\"stylesheet\" href=\"style.css\">")) {
                                html = html.replace("<link rel=\"stylesheet\" href=\"style.css\">", "<style>\n$styleCss\n</style>")
                            } else if (!html.contains("<style>") && html.contains("</head>")) {
                                html = html.replace("</head>", "<style>\n$styleCss\n</style>\n</head>")
                            }
                            
                            if (html.contains("<script src=\"script.js\"></script>")) {
                                html = html.replace("<script src=\"script.js\"></script>", "<script>\n$scriptJs\n</script>")
                            } else if (!html.contains("<script>") && html.contains("</body>")) {
                                html = html.replace("</body>", "<script>\n$scriptJs\n</script>\n</body>")
                            }

                            Box(modifier = Modifier.weight(0.6f)) {
                                HtmlPreview(
                                    htmlContent = html,
                                    onConsoleMessage = { log -> webConsoleLogs = webConsoleLogs + log }
                                )
                            }
                        }
                        
                        val consoleWeight = if (isWeb) 0.4f else 1.0f
                        Column(modifier = Modifier.weight(consoleWeight).fillMaxWidth().background(Color(0xFF121212))) {
                            val listState = rememberLazyListState()
                            val totalLogs = if (isWeb) webConsoleLogs.size else consoleOutput.size
                            
                            LaunchedEffect(totalLogs) {
                                if (totalLogs > 0) {
                                    listState.animateScrollToItem(totalLogs - 1)
                                }
                            }

                            // Terminal Header Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E1E1E))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(
                                        "Terminal",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val timerText = if (executionDuration > 0) {
                                        val secs = executionDuration / 1000.0
                                        String.format("%.2fs", secs)
                                    } else ""
                                    
                                    val stateBadgeColor = when (executionState) {
                                        ExecutionState.RUNNING, ExecutionState.BUILDING, ExecutionState.PREPARING -> ClassMastiYellow
                                        ExecutionState.SUCCESS -> Color(0xFF4CAF50)
                                        ExecutionState.BUILD_FAILED, ExecutionState.RUNTIME_ERROR, ExecutionState.TIMEOUT -> MaterialTheme.colorScheme.error
                                        else -> Color.Gray
                                    }
                                    
                                    if (executionState != ExecutionState.IDLE) {
                                        Surface(
                                            color = stateBadgeColor.copy(alpha = 0.2f),
                                            shape = MaterialTheme.shapes.extraSmall
                                        ) {
                                            Text(
                                                text = "${executionState.name.lowercase()} $timerText",
                                                color = stateBadgeColor,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Row {
                                    if (executionState == ExecutionState.RUNNING || executionState == ExecutionState.BUILDING) {
                                        IconButton(onClick = { viewModel.stopExecution() }) {
                                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    IconButton(onClick = { 
                                        viewModel.executionManager.clearConsole()
                                        webConsoleLogs = emptyList() 
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.LightGray)
                                    }
                                    IconButton(onClick = { showPreview = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFF2C2C2C))

                            // Terminal Output Stream
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (isWeb) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        items(webConsoleLogs) { log ->
                                            Text(
                                                text = log,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.White
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        items(consoleOutput) { msg ->
                                            val color = when (msg.type) {
                                                ConsoleMessageType.STDERR, ConsoleMessageType.ERROR, ConsoleMessageType.COMPILER_DIAGNOSTIC -> MaterialTheme.colorScheme.error
                                                ConsoleMessageType.SYSTEM -> ClassMastiYellow
                                                ConsoleMessageType.WARNING -> Color(0xFFFFA000)
                                                ConsoleMessageType.INPUT_PROMPT -> Color(0xFF64B5F6)
                                                else -> Color.White
                                            }
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                                color = color,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }

                            // Interactive Stdin Input Bar
                            if (executionState == ExecutionState.RUNNING || executionState == ExecutionState.WAITING_FOR_INPUT) {
                                HorizontalDivider(color = Color(0xFF2C2C2C))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E1E1E))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    TextField(
                                        value = stdinInput,
                                        onValueChange = { stdinInput = it },
                                        placeholder = { Text("Enter input to stdin...", color = Color.Gray, style = MaterialTheme.typography.bodySmall) },
                                        modifier = Modifier.weight(1f),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = ClassMastiYellow,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                    )
                                    IconButton(
                                        onClick = {
                                            if (stdinInput.isNotEmpty()) {
                                                viewModel.sendStdin(stdinInput)
                                                stdinInput = ""
                                            }
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send stdin", tint = ClassMastiYellow)
                                    }
                                }
                            }
                        }
                    } else {
                        currentFile?.let { file ->
                            val language = remember(file.name) { LanguageRegistry.getLanguageByExtension(file.name) }
                            val fontSize by viewModel.settingsManager.fontSize.collectAsState()
                            val showLineNumbers by viewModel.settingsManager.showLineNumbers.collectAsState()
                            val autoCloseBrackets by viewModel.settingsManager.autoCloseBrackets.collectAsState()

                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (language != null) {
                                    Text(
                                        text = language.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalDivider()
                            CodeEditor(
                                content = fileContent,
                                onContentChange = { viewModel.updateContent(it) },
                                language = language,
                                fontSize = fontSize,
                                showLineNumbers = showLineNumbers,
                                autoCloseBrackets = autoCloseBrackets,
                                modifier = Modifier.fillMaxSize()
                            )
                        } ?: run {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Text("Select a file to edit")
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCompact) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text("Explorer", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider()
                    FileTree(
                        files = files,
                        currentFile = currentFile,
                        onFileSelected = {
                            viewModel.openFile(it)
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            scaffoldContent()
        }
    } else {
        scaffoldContent()
    }
}
