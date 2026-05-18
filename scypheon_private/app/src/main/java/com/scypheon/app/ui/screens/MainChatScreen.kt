package com.scypheon.app.ui.screens


import com.scypheon.sdk.core.model.ScypheonConfig
import com.scypheon.sdk.core.engine.InitializationState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import com.scypheon.app.ui.screens.ImageAttachmentRow
import com.scypheon.app.ui.ChatMessageUiState
import com.scypheon.sdk.core.memory.DualMemoryManager
import com.scypheon.sdk.core.memory.Session
import com.scypheon.sdk.core.memory.ChatMessage
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import com.scypheon.sdk.core.model.ScypheonBackendDiagnostic
import com.scypheon.app.ui.components.ScypheonMemoryCard
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput

import com.scypheon.app.data.models.SystemHealth
import com.scypheon.app.data.models.OomDiagnostic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    messages: List<ChatMessageUiState>,
    isReady: Boolean,
    onSendMessage: (String, Uri?) -> Boolean,
    activeFeature: String?,
    onToggleFeature: (String) -> Unit,
    onOpenTelemetry: () -> Unit,
    onOpenGraphExplorer: () -> Unit,
    sessionHistory: List<Session> = emptyList(),
    onNewSession: () -> Unit = {},
    onLoadSession: (String) -> Unit = {},
    onOpenModelHub: () -> Unit = {},
    activeModelName: String = "no models selected",
    activeEngineType: String? = null,
    isLiveModeActive: Boolean = false,
    onToggleLiveMode: () -> Unit = {},
    userName: String = "",
    onSaveUserName: (String) -> Unit = {},
    error: String? = null,
    localModels: List<java.io.File> = emptyList(),
    isLocalModelPickerVisible: Boolean = false,
    onShowLocalModelPicker: () -> Unit = {},
    onHideLocalModelPicker: () -> Unit = {},
    onSelectLocalModel: (java.io.File) -> Unit = {},
    systemHealth: SystemHealth? = null,
    isDiagnosticVisible: Boolean = false,
    onShowDiagnostics: () -> Unit = {},
    onHideDiagnostics: () -> Unit = {},
    onDismissError: () -> Unit = {},
    systemWarning: String? = null,
    onDismissWarning: () -> Unit = {},
    onConfirmWarning: () -> Unit = {},
    config: ScypheonConfig = ScypheonConfig(),
    isConfigVisible: Boolean = false,
    onUpdateConfig: (ScypheonConfig) -> Unit = {},
    onToggleConfig: (Boolean) -> Unit = {},
    onResetHardware: () -> Unit = {},
    engineState: com.scypheon.sdk.core.engine.InitializationState = com.scypheon.sdk.core.engine.InitializationState.Idle,
    ragState: com.scypheon.sdk.core.memory.IVectorEngine.EngineState = com.scypheon.sdk.core.memory.IVectorEngine.EngineState.Initializing,
    diagnosticLogs: List<String> = emptyList(),
    isSandboxAlive: Boolean = true,
    isMemoryOptimized: Boolean = false,
    onDismissMemoryOptimization: () -> Unit = {},
    memoryStabilityState: com.scypheon.app.ui.MemoryStabilityState = com.scypheon.app.ui.MemoryStabilityState.IDLE,
    memoryWarningCooldown: Int = 0,
    onConfirmStabilityWarning: () -> Unit = {},
    isAiGenerating: Boolean = false,
    onStopGeneration: () -> Unit = {},
    onRetryMessage: (String, Uri?) -> Unit = { _, _ -> },
    oomDiagnostic: OomDiagnostic? = null,
    onDismissOom: () -> Unit = {},
    isMemoryInconsistent: Boolean = false,
    onResetMemory: () -> Unit = {},
    onIgnoreInconsistency: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var expandedMenu by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showAttachmentMenu by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    var showNamePrompt by remember { mutableStateOf(false) }
    var newNameInput by remember { mutableStateOf("") }

    val isOverlayVisible = showAttachmentMenu || isLocalModelPickerVisible || isDiagnosticVisible
    val blurRadius by animateDpAsState(
        targetValue = if (isOverlayVisible) 12.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "BackdropBlur"
    )

    LaunchedEffect(isReady, userName) {
        if (isReady && userName.isEmpty()) {
            showNamePrompt = true
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) attachedImageUri = uri
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = data?.getOrNull(0)
            if (!spokenText.isNullOrBlank()) {
                inputText = spokenText
            }
        }
    }

    // [v1.5.3-SAR] FIX: Disable gestures when drawer is closed to prevent
    // ripple ghosting artifacts during close animation.
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFFF8F9FA),
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        onNewSession()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F0FE), contentColor = Color(0xFF0A56D1)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat")
                    Spacer(Modifier.width(8.dp))
                    Text("New Chat")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Conversation History",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF5F6368),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn {
                    items(sessionHistory) { session ->
                        NavigationDrawerItem(
                            label = { 
                                Text(
                                    text = session.title, 
                                    maxLines = 1
                                ) 
                            },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    delay(50)
                                    onLoadSession(session.id)
                                    drawerState.close()
                                }
                            },
                            icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(Modifier.weight(1f))
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Model Hub") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            delay(50)
                            drawerState.close()
                            onOpenModelHub()
                        }
                    },
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFCFDFF), Color(0xFFF0F4FA))
                    )
                )
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                modifier = Modifier
                    .blur(if (memoryStabilityState == com.scypheon.app.ui.MemoryStabilityState.CRASHED) 16.dp else 0.dp)
                    .then(
                    if (!isReady) Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    } else Modifier
                ),
                topBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 1.dp),
                    color = Color.Transparent, // [v1.4.0-SAR] Transparent — global Solaris gradient flows through
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp, bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Segment, contentDescription = "Menu", tint = Color(0xFF1F1F1F), modifier = Modifier.size(28.dp))
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Scypheon Private",
                                    style = TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp,
                                        color = Color(0xFF1F1F1F)
                                    )
                                )
                                Surface(
                                    onClick = onShowLocalModelPicker,
                                    shape = CircleShape,
                                    color = Color.Black
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (activeModelName == "no models selected" || activeModelName.contains("(STANDBY)", ignoreCase = true)) "STANDBY" else activeModelName.uppercase(),
                                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 0.5.sp)
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { expandedMenu = true }) {
                                Icon(Icons.Default.Tune, contentDescription = "Settings", tint = Color(0xFF1F1F1F), modifier = Modifier.size(24.dp))
                                DropdownMenu(expanded = expandedMenu, onDismissRequest = { expandedMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("GraphRAG Oracle", fontWeight = FontWeight.Bold) },
                                        leadingIcon = { Icon(Icons.Default.AutoGraph, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            onOpenGraphExplorer()
                                            expandedMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Aegis Vault", fontWeight = FontWeight.Bold) },
                                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            onOpenTelemetry()
                                            expandedMenu = false
                                        }
                                    )
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    DropdownMenuItem(
                                        text = { Text("Scypheon Settings", fontWeight = FontWeight.Bold) },
                                        leadingIcon = { Icon(Icons.Default.SettingsInputComponent, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            onToggleConfig(true)
                                            expandedMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Neural Link Status", fontWeight = FontWeight.Bold) },
                                        leadingIcon = { Icon(Icons.Default.ShieldMoon, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            onShowDiagnostics()
                                            expandedMenu = false
                                        }
                                    )
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    DropdownMenuItem(
                                        text = { Text("Update Identifier", fontWeight = FontWeight.Medium) },
                                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF5F6368), modifier = Modifier.size(20.dp)) },
                                        onClick = {
                                            newNameInput = userName
                                            showNamePrompt = true
                                            expandedMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (oomDiagnostic != null) {
                        AlertDialog(
                            onDismissRequest = onDismissOom,
                            icon = { Icon(Icons.Default.Memory, contentDescription = null, tint = Color.Red, modifier = Modifier.size(32.dp)) },
                            title = { Text("Insufficient RAM for ${oomDiagnostic.backend}") },
                            text = {
                                Text(
                                    text = buildAnnotatedString {
                                        append("Your device does not have enough memory to run ")
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(oomDiagnostic.modelName) }
                                        append(" on the ")
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(oomDiagnostic.backend) }
                                        append(" backend.\n\n")
                                        
                                        withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                                            append("Model requires: %.2f GB\n".format(oomDiagnostic.requiredGB))
                                        }
                                        withStyle(SpanStyle(color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)) {
                                            append("Device available: %.2f GB".format(oomDiagnostic.availableGB))
                                        }
                                        
                                        append("\n\nThis model has been blacklisted for this hardware configuration to prevent recurring system crashes.")
                                    }
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = onDismissOom,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                                ) {
                                    Text("Understood")
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            containerColor = Color.White
                        )
                    }
                }
            },
        bottomBar = {
            if (isReady && activeModelName != "no models selected") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = Color.White,
                            border = BorderStroke(0.5.dp, Color(0xFF0A56D1).copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { 
                                    scope.launch { delay(50); showAttachmentMenu = true } 
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "Attachments", tint = Color(0xFF1F1F1F), modifier = Modifier.size(24.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = "Ask Scypheon Private...",
                                            style = TextStyle(color = Color(0xFF5F6368), fontSize = 16.sp)
                                        )
                                    }
                                    BasicTextField(
                                        value = inputText,
                                        onValueChange = { inputText = it },
                                        textStyle = TextStyle(color = Color(0xFF1F1F1F), fontSize = 16.sp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isAiGenerating) {
                                        // STOP BUTTON (Kill Switch)
                                        IconButton(
                                            onClick = onStopGeneration,
                                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Red)
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                    } else if (inputText.isBlank() && attachedImageUri == null) {
                                        IconButton(onClick = {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            }
                                            speechLauncher.launch(intent)
                                        }) {
                                            Icon(Icons.Outlined.Mic, contentDescription = "Mic", tint = Color(0xFF1F1F1F))
                                        }

                                        IconButton(onClick = { 
                                            scope.launch { delay(50); onToggleLiveMode() } 
                                        }) {
                                            Icon(
                                                Icons.Default.GraphicEq, 
                                                contentDescription = "Live Voice", 
                                                tint = if (isLiveModeActive) Color(0xFF007AFF) else Color(0xFF1F1F1F),
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    } else {
                                        // SEND BUTTON
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    delay(50)
                                                    val success = onSendMessage(inputText, attachedImageUri)
                                                    if (success) {
                                                        inputText = ""
                                                        attachedImageUri = null
                                                    }
                                                }
                                            },
                                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black)
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                        
                        // General AI Disclaimer Footer
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Scypheon generated content may be inaccurate. Verify important details.",
                            style = TextStyle(color = Color.Gray.copy(alpha = 0.7f), fontSize = 10.sp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .blur(blurRadius)
            ) {
                ScypheonMemoryCard(
                    isVisible = isMemoryOptimized,
                    onDismiss = onDismissMemoryOptimization
                )

                if (!isReady) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "PHOENIX TRIAGE",
                                style = TextStyle(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 2.sp,
                                    color = Color(0xFF007AFF)
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                            
                            val statusText = when(engineState) {
                                is com.scypheon.sdk.core.engine.InitializationState.Analyzing -> (engineState as com.scypheon.sdk.core.engine.InitializationState.Analyzing).step
                                is com.scypheon.sdk.core.engine.InitializationState.Trying -> "Initializing ${(engineState as com.scypheon.sdk.core.engine.InitializationState.Trying).backend}..."
                                is com.scypheon.sdk.core.engine.InitializationState.Loading -> "Loading ${(engineState as com.scypheon.sdk.core.engine.InitializationState.Loading).backend} (${((engineState as com.scypheon.sdk.core.engine.InitializationState.Loading).progress * 100).toInt()}%)..."
                                is com.scypheon.sdk.core.engine.InitializationState.Failed -> "Recovering from ${(engineState as com.scypheon.sdk.core.engine.InitializationState.Failed).backend} failure..."
                                is com.scypheon.sdk.core.engine.InitializationState.Success -> "Harmony Established"
                                else -> "Waking Neural Gateway..."
                            }

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F1F1F),
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            val currentProgress = (engineState as? com.scypheon.sdk.core.engine.InitializationState.Loading)?.progress
                            
                            if (currentProgress != null) {
                                LinearProgressIndicator(
                                    progress = currentProgress,
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = Color(0xFF007AFF),
                                    trackColor = Color(0xFFD1E4FF)
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = Color(0xFF007AFF),
                                    trackColor = Color(0xFFD1E4FF)
                                )
                            }
                            
                            Spacer(Modifier.height(48.dp))
                            
                            // Collapsible Diagnostics Console (Technical Transparency)
                            var showConsole by remember { mutableStateOf(false) }
                            Surface(
                                onClick = { showConsole = !showConsole },
                                color = Color.Black.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (showConsole) Icons.Default.KeyboardArrowUp else Icons.Default.Terminal,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("ENGINEERING CONSOLE", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                    Spacer(Modifier.weight(1f))
                                    Surface(
                                        color = if (isSandboxAlive) Color(0xFF00E676).copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            if (isSandboxAlive) "SANDBOX: ACTIVE" else "SANDBOX: CRASHED",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = TextStyle(
                                                fontSize = 8.sp, 
                                                fontWeight = FontWeight.Black,
                                                color = if (isSandboxAlive) Color(0xFF00E676) else Color.Red
                                            )
                                        )
                                    }
                                }
                            }
                            
                            AnimatedVisibility(visible = showConsole) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(Modifier.height(16.dp))
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().height(150.dp),
                                        color = Color.Black,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        val consoleScrollState = rememberScrollState()
                                        LaunchedEffect(diagnosticLogs.size) {
                                            consoleScrollState.animateScrollTo(consoleScrollState.maxValue)
                                        }
                                        Column(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .verticalScroll(consoleScrollState)
                                        ) {
                                            diagnosticLogs.forEach { log ->
                                                Text(
                                                    text = "> $log",
                                                    style = TextStyle(
                                                        color = Color(0xFF00E676),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp
                                                    )
                                                )
                                                Spacer(Modifier.height(2.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (activeModelName == "no models selected") {
                    BridgeConnectionHub(
                        userName = userName,
                        health = systemHealth,
                        onOpenModelHub = onOpenModelHub,
                        onRefresh = onResetHardware,
                        engineState = engineState,
                        lastError = error,
                        isSandboxAlive = isSandboxAlive
                    )
                } else if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val greetingBrush = remember {
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0A56D1), Color(0xFF00BCD4), Color(0xFF7B1FA2)),
                                start = Offset(0f, 0f),
                                end = Offset(400f, 400f)
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Welcome, $userName",
                                style = TextStyle(brush = greetingBrush, fontSize = 44.sp, fontWeight = FontWeight.Black)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        reverseLayout = true
                    ) {
                        items(messages.reversed()) { msg ->
                            ScypheonChatBubble(msg, onRetryMessage = onRetryMessage, enableThinking = config.enableThinking)
                        }
                    }
                }

                // Premium Neural Alert
                AnimatedVisibility(
                    visible = error != null && 
                             memoryStabilityState != com.scypheon.app.ui.MemoryStabilityState.CRASHED &&
                             oomDiagnostic == null &&
                             !isMemoryInconsistent,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                ) {
                    NeuralLinkAlert(
                        message = error ?: "",
                        onClickDetails = {
                            scope.launch {
                                delay(50)
                                onDismissError()
                                onShowDiagnostics()
                            }
                        },
                        onDismiss = {
                            scope.launch {
                                delay(50)
                                onDismissError()
                            }
                        }
                    )
                }

                if (systemWarning != null) {
                    AlertDialog(
                        onDismissRequest = onDismissWarning,
                        title = { Text("笞・・System Warning", fontWeight = FontWeight.Bold, color = Color(0xFFFFB300)) },
                        text = { Text(systemWarning) },
                        confirmButton = {
                            Button(
                                onClick = onConfirmWarning,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                            ) { Text("Force Load") }
                        },
                        dismissButton = {
                            TextButton(onClick = onDismissWarning) { Text("Cancel") }
                        }
                    )
                }
                
                if (isDiagnosticVisible) {
                    NeuralLinkStatusHub(
                        health = systemHealth,
                        onDismiss = onHideDiagnostics,
                        onOpenModelHub = {
                            scope.launch {
                                delay(50)
                                onHideDiagnostics()
                                onOpenModelHub()
                            }
                        }
                    )
                }
                
                if (isConfigVisible) {
                    ConfigurationsDialog(
                        config = config,
                        onUpdate = onUpdateConfig,
                        onDismiss = { onToggleConfig(false) },
                        onResetHardware = onResetHardware
                    )
                }

                // [MDRS 4.1] Stability Interceptor UI
                AnimatedVisibility(
                    visible = memoryStabilityState == com.scypheon.app.ui.MemoryStabilityState.WARNING_COOLDOWN || 
                              memoryStabilityState == com.scypheon.app.ui.MemoryStabilityState.READY_TO_FORCE,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).imePadding()
                ) {
                    StabilityWarningCard(
                        cooldown = memoryWarningCooldown,
                        isReady = memoryStabilityState == com.scypheon.app.ui.MemoryStabilityState.READY_TO_FORCE,
                        onConfirm = onConfirmStabilityWarning
                    )
                }

                }
            }

            // [MDRS 4.1] Polished Fatal Error State (Rendered outside Scaffold to avoid blur)
            AnimatedVisibility(
                visible = memoryStabilityState == com.scypheon.app.ui.MemoryStabilityState.CRASHED,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { awaitPointerEventScope { while(true) { awaitPointerEvent().changes.forEach { it.consume() } } } }
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        shape = RoundedCornerShape(32.dp),
                        color = Color.White,
                        shadowElevation = 24.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF5252).copy(alpha = 0.1f),
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            
                            Text(
                                text = "Fatal Memory Error",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1F1F1F)
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            Text(
                                text = "Your device ran out of memory. The neural sandbox has been isolated to prevent a system crash.",
                                textAlign = TextAlign.Center,
                                color = Color(0xFF5F6368),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp
                            )
                            
                            Spacer(Modifier.height(32.dp))
                            
                            Button(
                                onClick = onResetHardware,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Text("Restart Neural Engine", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // [SAR-1.1.4] Memory Integrity Warning Card (FTS Corruption)
            AnimatedVisibility(
                visible = isMemoryInconsistent,
                enter = scaleIn(initialScale = 0.9f) + fadeIn(),
                exit = scaleOut(targetScale = 0.9f) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        tonalElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.SdStorage,
                                contentDescription = null,
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Critical Memory Inconsistency",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F1F1F)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "The internal memory index is out of sync, likely due to an abrupt shutdown. Your neural context may be unstable.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = Color(0xFF5F6368)
                            )
                            
                            Spacer(Modifier.height(24.dp))
                            
                            Button(
                                onClick = onResetMemory,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("HARD RESET MEMORY (Recommended)", fontWeight = FontWeight.Black)
                                    Text("Deletes current history to restore 100% stability.", fontSize = 10.sp)
                                }
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            OutlinedButton(
                                onClick = onIgnoreInconsistency,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("CONTINUE REGARDLESS (Risky)", color = Color(0xFF5F6368), fontWeight = FontWeight.Bold)
                                    Text("May cause AI hallucinations or future crashes.", fontSize = 10.sp, color = Color(0xFF9E9E9E))
                                }
                            }
                        }
                    }
                }
            }

            if (showNamePrompt) {
                AlertDialog(
                    onDismissRequest = { if (userName.isNotEmpty()) showNamePrompt = false },
                    title = { Text(if (userName.isEmpty()) "Identify Yourself" else "Update Identifier", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("What should Scypheon call you?", fontSize = 14.sp, color = Color.Gray)
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = newNameInput,
                                onValueChange = { if (it.length <= 12) newNameInput = it },
                                placeholder = { Text("") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newNameInput.isNotBlank()) {
                                    onSaveUserName(newNameInput.trim())
                                    showNamePrompt = false
                                }
                            },
                            enabled = newNameInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("SECURE IDENTITY") }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(28.dp)
                )
            }

            if (showAttachmentMenu) {
                ModalBottomSheet(
                    onDismissRequest = { showAttachmentMenu = false },
                    sheetState = sheetState,
                    containerColor = Color.White,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                ) {
                    AttachmentMenuSheet(
                        onOptionSelected = { option ->
                            showAttachmentMenu = false
                            when (option) {
                                "Gallery" -> { imageLauncher.launch("image/*") }
                            }
                        }
                    )
                }
            }

            if (isLocalModelPickerVisible) {
                ModalBottomSheet(
                    onDismissRequest = onHideLocalModelPicker,
                    sheetState = sheetState,
                    containerColor = Color.White,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFFE0E0E0)) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Hotswap AI Engine",
                                    style = TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1F1F1F)
                                    )
                                )
                                Text(
                                    text = "Select found local models",
                                    style = TextStyle(fontSize = 12.sp, color = Color.Gray)
                                )
                            }
                            IconButton(onClick = onHideLocalModelPicker) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))

                        if (localModels.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No local models found.", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                items(localModels) { file ->
                                    val isActive = activeModelName.removeSuffix(" (STANDBY)") == file.name
                                    Surface(
                                        onClick = { onSelectLocalModel(file) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isActive) Color(0xFFF5F5F5) else Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (file.name.endsWith(".task") || file.name.endsWith(".litertlm")) Icons.Default.Bolt else Icons.Default.Psychology,
                                                contentDescription = null,
                                                tint = if (isActive) Color(0xFF1F1F1F) else Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.name,
                                                    style = TextStyle(
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF1F1F1F)
                                                    )
                                                )
                                                Text(
                                                    text = if (file.name.endsWith(".task") || file.name.endsWith(".litertlm")) "LiteRT-LM Elite Engine" else "Llama-Universal Engine",
                                                    style = TextStyle(fontSize = 11.sp, color = Color.Gray)
                                                )
                                            }
                                            if (isActive) {
                                                Icon(Icons.Default.Check, contentDescription = "Active", tint = Color(0xFF00E676))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        Spacer(Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = {
                                onHideLocalModelPicker()
                                onOpenModelHub()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFEEEEEE))
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Download Alternative Models from Hub", color = Color(0xFF1F1F1F))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NeuralLinkAlert(message: String, onClickDetails: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1F1F1F).copy(alpha = 0.9f),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .graphicsLayer {
                shadowElevation = 20f
                clip = true
            },
        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "NEURAL LINK ALERT", style = TextStyle(fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF00E676), letterSpacing = 1.sp))
                Text(text = message, color = Color.White, fontSize = 14.sp)
                TextButton(onClick = onClickDetails, contentPadding = PaddingValues(0.dp)) {
                    Text("Click here for more details", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun BridgeConnectionHub(
    userName: String,
    health: SystemHealth?,
    onOpenModelHub: () -> Unit,
    onRefresh: () -> Unit,
    engineState: InitializationState = InitializationState.Idle,
    lastError: String? = null,
    isSandboxAlive: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 虫 Pro Aesthetic: Glassmorphism with Drop Shadow
        Surface(
            shape = RoundedCornerShape(48.dp),
            color = Color.White, 
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp, 
                    shape = RoundedCornerShape(48.dp), 
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                    spotColor = Color.Black.copy(alpha = 0.3f)
                )
        ) {
            Column(
                modifier = Modifier.padding(32.dp), 
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.LinkOff, 
                    contentDescription = null, 
                    modifier = Modifier.size(64.dp), 
                    tint = if (!isSandboxAlive) Color(0xFFEA4335) else Color(0xFFFF9800)
                )
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    when {
                        !isSandboxAlive -> "Engine Crashed"
                        engineState is InitializationState.Loading -> "Neural Link Activating..."
                        engineState is InitializationState.Failed -> "Connection Failed"
                        else -> "Neural Connection Standby"
                    },
                    fontWeight = FontWeight.Black, 
                    fontSize = 24.sp, 
                    color = Color(0xFF1F1F1F), 
                    textAlign = TextAlign.Center
                )
                
                Text(
                    when {
                        !isSandboxAlive -> "Sandbox process terminated. Reset hardware diagnostics or try a different model."
                        engineState is InitializationState.Loading -> "Loading ${(engineState as InitializationState.Loading).backend}... ${((engineState as InitializationState.Loading).progress * 100).toInt()}%"
                        engineState is InitializationState.Failed -> {
                            val failed = engineState as InitializationState.Failed
                            "Backend: ${failed.backend} — ${failed.error}"
                        }
                        else -> "Interface online but AI engines are offline."
                    },
                    color = Color.Gray, 
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // [v1.5.3-SAR] Engine state progress bar
                if (engineState is InitializationState.Loading) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { (engineState as InitializationState.Loading).progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF0A56D1),
                        trackColor = Color(0xFFF1F3F4)
                    )
                }
                
                // [v1.5.3-SAR] Crash diagnostic detail
                if (lastError != null && (engineState is InitializationState.Failed || !isSandboxAlive)) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF3E0),
                        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "⚠ DIAGNOSTIC DETAIL",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color(0xFFE65100),
                                    letterSpacing = 1.sp
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                lastError,
                                fontSize = 11.sp,
                                color = Color(0xFF5D4037),
                                maxLines = 3,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(32.dp))
                
                HorizontalDivider(color = Color(0xFFF1F3F4))
                Spacer(Modifier.height(24.dp))
                
                Text(
                    "NEURAL COMPONENT STATUS", 
                    style = TextStyle(
                        fontWeight = FontWeight.Bold, 
                        fontSize = 11.sp, 
                        color = Color(0xFF5F6368), 
                        letterSpacing = 1.sp
                    )
                )
                
                Spacer(Modifier.height(16.dp))
                
                HealthCheckItem("Digital Twin Memory (RAG)", health?.isMemoryOk ?: false, health?.isPiggybacking ?: false)
                HealthCheckItem("Elite Reasoning Engine", health?.isEliteOk ?: false)
                HealthCheckItem("Universal Language Engine", health?.isUniversalOk ?: false)
                
                Spacer(Modifier.height(32.dp))
                
                Button(
                    onClick = onOpenModelHub,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F))
                ) {
                    Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("SECURE MODEL HUB", fontWeight = FontWeight.Bold)
                }

                // [v1.5.3-SAR] Quick action: Reset blacklists if crashed
                if (!isSandboxAlive || engineState is InitializationState.Failed) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF9800))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFFF9800))
                        Spacer(Modifier.width(8.dp))
                        Text("RESET HARDWARE DIAGNOSTICS", fontWeight = FontWeight.Bold, color = Color(0xFFFF9800), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun HealthCheckItem(label: String, isOk: Boolean, isShared: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                isOk -> Icons.Default.CheckCircle
                isShared -> Icons.Default.AutoAwesome
                else -> Icons.Default.Cancel
            },
            contentDescription = null,
            tint = when {
                isOk -> Color(0xFF00E676)
                isShared -> Color(0xFF0A56D1)
                else -> Color(0xFFEA4335)
            },
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label, 
            fontSize = 14.sp, 
            color = if (isOk || isShared) Color(0xFF1F1F1F) else Color(0xFFEA4335)
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = when {
                isOk -> "READY"
                isShared -> "SHARED"
                else -> "MISSING"
            }, 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Bold, 
            color = when {
                isOk -> Color(0xFF00E676)
                isShared -> Color(0xFF0A56D1)
                else -> Color(0xFFEA4335)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuralLinkStatusHub(
    health: SystemHealth?,
    onDismiss: () -> Unit,
    onOpenModelHub: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onOpenModelHub) { Text("GOTO HUB", color = Color(0xFF007AFF)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = Color.Gray) }
        },
        title = { Text("Neural Link Diagnostics", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Verify the status of your local AI components.", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                
                HealthDiagnosticRow("Embedding Model", health?.memoryPath ?: "N/A", health?.isMemoryOk ?: false, health?.isPiggybacking ?: false)
                HealthDiagnosticRow("Elite Engine (.task)", health?.elitePath ?: "N/A", health?.isEliteOk ?: false)
                HealthDiagnosticRow("Universal Engine (.gguf)", health?.universalPath ?: "N/A", health?.isUniversalOk ?: false)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun HealthDiagnosticRow(label: String, path: String, isOk: Boolean, isShared: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    isOk -> Icons.Default.RadioButtonChecked
                    isShared -> Icons.Default.AutoAwesome
                    else -> Icons.Default.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = when {
                    isOk -> Color(0xFF00E676)
                    isShared -> Color(0xFF0A56D1)
                    else -> Color(0xFFEA4335)
                },
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (isShared) {
                Spacer(Modifier.width(8.dp))
                Surface(color = Color(0xFF0A56D1).copy(alpha = 0.1f), shape = CircleShape) {
                    Text("SHARED", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A56D1), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
        Text(
            text = "Path: $path",
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 24.dp),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 22.sp,
    enableThinking: Boolean = true
) {
    val thoughtRegex = remember { Regex("<thought>(.*?)(?:</thought>|$)", RegexOption.DOT_MATCHES_ALL) }
    val thoughtMatch = remember(text) { thoughtRegex.find(text) }
    
    val contentWithoutThought = remember(text, thoughtMatch) {
        if (thoughtMatch != null) {
            // Remove the match completely if it exists
            text.replace(thoughtMatch.value, "").trim()
        } else {
            text
        }
    }

    Column(modifier = modifier) {
        if (thoughtMatch != null && enableThinking) {
            val thoughtContent = remember(thoughtMatch) { thoughtMatch.groupValues[1].trim() }
            if (thoughtContent.isNotEmpty()) {
                Surface(
                    color = Color(0xFFF8F9FA),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome, 
                                contentDescription = null, 
                                modifier = Modifier.size(14.dp), 
                                tint = Color(0xFF0A56D1)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Reasoning Process",
                                style = TextStyle(
                                    fontSize = 12.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = Color(0xFF0A56D1),
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = thoughtContent,
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = Color(0xFF5F6368),
                                lineHeight = 20.sp
                            )
                        )
                    }
                }
            }
        }

        val lines = remember(contentWithoutThought) { contentWithoutThought.split("\n") }
        lines.forEach { line ->
            when {
                line.startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(line.substring(4)),
                        style = TextStyle(fontSize = fontSize * 1.2f, fontWeight = FontWeight.Bold, color = color),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)) {
                        Text("窶｢ ", color = color, fontSize = fontSize)
                        Text(
                            text = parseInlineMarkdown(line.substring(2)),
                            color = color,
                            fontSize = fontSize,
                            lineHeight = lineHeight
                        )
                    }
                }
                line.startsWith("|") && line.contains("-") -> {
                    // Simple table detection - use Monospace
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.horizontalScroll(scrollState).background(Color(0xFFF8F9FA), RoundedCornerShape(4.dp)).padding(8.dp)) {
                        Text(
                            text = line,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize * 0.9f, color = color)
                        )
                    }
                }
                line.contains("|") -> {
                    // Possible table data
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.horizontalScroll(scrollState).background(Color(0xFFF8F9FA)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(
                            text = line,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize * 0.9f, color = color)
                        )
                    }
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(line),
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight
                    )
                }
            }
        }
    }
}

fun parseInlineMarkdown(text: String): AnnotatedString {
    val regex = Regex("(\\*\\*.+?\\*\\*)|(\\*.+?\\*)|(`.+?`)")
    return buildAnnotatedString {
        var cursor = 0
        // Bold: **text**
        // Italic: *text*
        // Code: `text`
        
        val matches = regex.findAll(text)
        
        matches.forEach { match ->
            // Add previous plain text
            if (cursor <= match.range.first) {
                append(text.substring(cursor, match.range.first))
            }
            
            val matchValue = match.value
            when {
                matchValue.startsWith("**") && matchValue.length > 4 -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(matchValue.substring(2, matchValue.length - 2))
                    }
                }
                matchValue.startsWith("**") -> append(matchValue) // Fallback for incomplete bold
                
                matchValue.startsWith("*") && matchValue.length >= 2 -> {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(matchValue.substring(1, matchValue.length - 1))
                    }
                }
                matchValue.startsWith("*") -> append(matchValue)
                
                matchValue.startsWith("`") && matchValue.length >= 2 -> {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.LightGray.copy(alpha = 0.3f))) {
                        append(matchValue.substring(1, matchValue.length - 1))
                    }
                }
                else -> append(matchValue)
            }
            cursor = match.range.last + 1
        }
        
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

fun formatForExternalApp(text: String): String {
    // 1. Remove systemic tokens
    var cleaned = text.replace("<start_of_turn>", "")
        .replace("<end_of_turn>", "")
        .replace("[SYSTEM:", "")
        .replace("]", "")
        .trim()
    
    // 2. WhatsApp Format: **bold** -> *bold*, *italic* -> _italic_
    // This part is for the "Smart Copy" logic.
    val whatsappCleaned = cleaned
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "*$1*") // Bold
        .replace(Regex("(?<!\\*)\\*(.*?)\\*(?!\\*)"), "_$1_") // Italic
    
    return whatsappCleaned
}

@Composable
fun ScypheonChatBubble(
    msg: ChatMessageUiState,
    onRetryMessage: (String, Uri?) -> Unit = { _, _ -> },
    enableThinking: Boolean = true
) {
    val context = LocalContext.current
    val isError = msg.text.startsWith("Error:")
    val alignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = alignment
    ) {
        if (msg.isUser) {
            val userBubbleBrush = remember {
                Brush.linearGradient(
                    colors = listOf(Color(0xFF007AFF), Color(0xFF5856D6)),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            }
            Surface(
                shape = RoundedCornerShape(24.dp, 24.dp, 4.dp, 24.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .padding(start = 64.dp)
                    .background(
                        userBubbleBrush,
                        shape = RoundedCornerShape(24.dp, 24.dp, 4.dp, 24.dp)
                    )
                    .graphicsLayer {
                        shadowElevation = 8f
                        shape = RoundedCornerShape(24.dp, 24.dp, 4.dp, 24.dp)
                        clip = true
                    }
            ) {
                MarkdownText(
                    text = msg.text,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.Start) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = "AI",
                                tint = Color(0xFF0A56D1),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    if (msg.isLoading && msg.text == "Processing...") {
                        // [v1.5.0-SAR] Premium prefill animation — bouncing dots instead of spinner
                        ThinkingDotsIndicator()
                    } else {
                        Column {
                            // Unified Bubble for Streaming and Thinking
                            Surface(
                                shape = RoundedCornerShape(4.dp, 24.dp, 24.dp, 24.dp),
                                color = Color.White,
                                border = BorderStroke(0.5.dp, Color(0xFF0A56D1).copy(alpha = 0.08f)),
                                shadowElevation = 4.dp,
                                modifier = Modifier.padding(end = 48.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    if (msg.status == com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_SYSTEM) {
                                        Surface(
                                            color = Color(0xFFE8F0FE),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        ) {
                                            Text(
                                                "PHOENIX SYSTEM INSIGHT",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A56D1))
                                            )
                                        }
                                    } else if (msg.status == com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED) {
                                        Surface(
                                            color = Color(0xFFFFEBEE),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        ) {
                                            Text(
                                                "SYNC FAILURE - EXCLUDED FROM CONTEXT",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFFD32F2F))
                                            )
                                        }
                                    }

                                    // [v1.5.0-SAR] Typewriter Buffer for streaming — smooth character reveal
                                    if (msg.isLoading) {
                                        TypewriterBuffer(
                                            targetText = msg.text,
                                            isStreaming = true,
                                            color = Color(0xFF1F1F1F),
                                            fontSize = 16.sp,
                                            lineHeight = 22.sp,
                                            enableThinking = enableThinking
                                        )
                                    } else {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            MarkdownText(
                                                text = msg.text,
                                                color = if (msg.status == com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED) Color(0xFFD32F2F) else Color(0xFF1F1F1F),
                                                fontSize = 16.sp,
                                                lineHeight = 22.sp,
                                                modifier = Modifier.weight(1f, fill = false),
                                                enableThinking = enableThinking
                                            )
                                        }
                                    }
                                    
                                    if (!msg.isLoading) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (!msg.isContextEligible) {
                                                Icon(
                                                    Icons.Default.Block,
                                                    contentDescription = "Hidden from Context",
                                                    tint = Color.Gray.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }

                                            if (msg.status == com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED) {
                                                IconButton(
                                                    onClick = { 
                                                        // Note: In a real implementation, we'd need to find the user message 
                                                        // that preceded this failure. For now, we assume retry is for the current bubble text
                                                        // But typically retry means re-sending the last USER message.
                                                        // To keep it simple, we'll let the ViewModel handle the last prompt retry.
                                                        onRetryMessage(msg.text, null) 
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Refresh,
                                                        contentDescription = "Retry",
                                                        tint = Color(0xFFD32F2F),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                            }

                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("Scypheon AI", formatForExternalApp(msg.text))
                                                    clipboard.setPrimaryClip(clip)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = "Copy",
                                                    tint = Color(0xFF5F6368),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (msg.hardwareStatus != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFF1F3F4),
                                            modifier = Modifier.alpha(0.8f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color(0xFF5F6368))
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = msg.hardwareStatus.uppercase(),
                                                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5F6368))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentMenuSheet(onOptionSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Quick Attachments", 
            fontWeight = FontWeight.Bold, 
            fontSize = 18.sp, 
            color = Color(0xFF1F1F1F),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AttachmentOption("Camera", Icons.Default.PhotoCamera, Color(0xFF4285F4), onOptionSelected)
            AttachmentOption("Gallery", Icons.Default.Collections, Color(0xFFEA4335), onOptionSelected)
            AttachmentOption("File", Icons.Default.AttachFile, Color(0xFFFBBC05), onOptionSelected)
            AttachmentOption("Cloud", Icons.Default.CloudQueue, Color(0xFF34A853), onOptionSelected)
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun AttachmentOption(label: String, icon: ImageVector, color: Color, onClick: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = { onClick(label) },
            shape = CircleShape,
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, color = Color(0xFF5F6368), fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationsDialog(
    config: ScypheonConfig,
    onDismiss: () -> Unit,
    onUpdate: (ScypheonConfig) -> Unit,
    onResetHardware: () -> Unit
) {
    // 孱・・BUFFERED STATE: Changes only apply on SAVE
    var tempMaxTokens by remember { mutableIntStateOf(config.maxTokens) }
    var tempCtxWindow by remember { mutableIntStateOf(config.contextWindow) }
    var tempTopK by remember { mutableIntStateOf(config.topK) }
    var tempTopP by remember { mutableFloatStateOf(config.topP) }
    var tempTemp by remember { mutableFloatStateOf(config.temperature) }
    var tempBackend by remember { mutableIntStateOf(config.selectedBackendMode) }
    var tempThinking by remember { mutableStateOf(config.enableThinking) }
    var tempOnlineSearch by remember { mutableStateOf(config.enableOnlineSearch) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        onUpdate(config.copy(
                            maxTokens = tempMaxTokens,
                            contextWindow = tempCtxWindow,
                            topK = tempTopK,
                            topP = tempTopP,
                            temperature = tempTemp,
                            selectedBackendMode = tempBackend,
                            enableThinking = tempThinking,
                            enableOnlineSearch = tempOnlineSearch
                        ))
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A56D1))
                ) {
                    Text("SAVE", fontWeight = FontWeight.Bold)
                }
            }
        },
        title = { 
            Text("Configurations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) 
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // [v1.4.0-SAR] Discrete Power-of-Two Sliders
                // We use index-based sliding to ensure the UI only ever shows valid architectural values.
                val maxTokenSteps = listOf(128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768)
                val ctxSteps = listOf(512, 1024, 2048, 4096, 8192, 16384, 32768)

                PremiumConfigRow(
                    label = "Max tokens",
                    currentValue = maxTokenSteps.indexOf(tempMaxTokens).coerceAtLeast(0).toFloat(),
                    valueRange = 0f..(maxTokenSteps.size - 1).toFloat(),
                    onValueChange = { index -> 
                        tempMaxTokens = maxTokenSteps[index.toInt()]
                    },
                    displayValue = tempMaxTokens.toString(),
                    isInteger = true,
                    steps = maxTokenSteps.size - 2
                )

                PremiumConfigRow(
                    label = "Context Window",
                    currentValue = ctxSteps.indexOf(tempCtxWindow).coerceAtLeast(0).toFloat(),
                    valueRange = 0f..(ctxSteps.size - 1).toFloat(),
                    onValueChange = { index -> 
                        tempCtxWindow = ctxSteps[index.toInt()]
                    },
                    displayValue = tempCtxWindow.toString(),
                    isInteger = true,
                    steps = ctxSteps.size - 2
                )

                PremiumConfigRow(
                    label = "TopK",
                    currentValue = tempTopK.toFloat(),
                    valueRange = 1f..100f,
                    onValueChange = { tempTopK = it.toInt() },
                    displayValue = tempTopK.toString(),
                    isInteger = true
                )

                PremiumConfigRow(
                    label = "TopP",
                    currentValue = tempTopP,
                    valueRange = 0f..1f,
                    onValueChange = { tempTopP = it },
                    displayValue = String.format("%.2f", tempTopP)
                )

                PremiumConfigRow(
                    label = "Temperature",
                    currentValue = tempTemp,
                    valueRange = 0f..2f,
                    onValueChange = { tempTemp = it },
                    displayValue = String.format("%.2f", tempTemp)
                )

                HorizontalDivider(modifier = Modifier.alpha(0.5f))

                // ACCELERATOR SECTION
                Column {
                    Text("Accelerator", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    ExclusiveAcceleratorSelector(
                        selectedMode = tempBackend,
                        onModeSelected = { tempBackend = it }
                    )
                }

                // AI REASONING SWITCH
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Enable AI Reasoning", fontWeight = FontWeight.Bold)
                        Text("Display <thought> process blocks", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = tempThinking,
                        onCheckedChange = { tempThinking = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0A56D1))
                    )
                }

                // [SAR] ONLINE SEARCH SWITCH
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Online Discovery", fontWeight = FontWeight.Bold)
                        Text("Allow tools to access internet (Wiki/FDA)", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = tempOnlineSearch,
                        onCheckedChange = { tempOnlineSearch = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0A56D1))
                    )
                }

                if (config.backendDiagnostics.isNotEmpty()) {
                    HardwareDiagnosticsPanel(config.backendDiagnostics, onResetHardware)
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

@Composable
fun PremiumConfigRow(
    label: String,
    currentValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    displayValue: String,
    isInteger: Boolean = false,
    steps: Int = 0
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isInteger) valueRange.start.toInt().toString() else String.format("%.1f", valueRange.start),
                fontSize = 12.sp,
                color = Color.Gray
            )
            
            Slider(
                value = currentValue,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF0A56D1),
                    activeTrackColor = Color(0xFF0A56D1),
                    inactiveTrackColor = Color(0xFF0A56D1).copy(alpha = 0.2f)
                )
            )

            OutlinedTextField(
                value = displayValue,
                onValueChange = { input ->
                    val clean = input.replace(",", ".")
                    clean.toFloatOrNull()?.let { 
                        if (it in valueRange) onValueChange(it)
                    }
                },
                modifier = Modifier.width(80.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    textAlign = TextAlign.Center, 
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0A56D1)
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
        }
    }
}

@Composable
fun ExclusiveAcceleratorSelector(
    selectedMode: Int,
    onModeSelected: (Int) -> Unit
) {
    val modes = listOf("AUTO", "CPU", "OPENCL", "VULKAN")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        modes.forEachIndexed { index, name ->
            val isSelected = selectedMode == index
            Surface(
                onClick = { onModeSelected(index) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) Color.White else Color.Transparent,
                shadowElevation = if (isSelected) 2.dp else 0.dp
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF0A56D1),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF0A56D1) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HardwareDiagnosticsPanel(
    diagnostics: List<ScypheonBackendDiagnostic>,
    onReset: () -> Unit
) {
    Surface(
        color = Color(0xFFFFEBEE),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Hardware Blocked", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F), fontSize = 12.sp)
            }
            diagnostics.forEach { diag ->
                Text(
                    "${diag.backend} failure detected at ${diag.timestamp}. Fallback active.",
                    fontSize = 11.sp,
                    color = Color(0xFFD32F2F),
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            TextButton(
                onClick = onReset,
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("RESET HARDWARE BLACKLIST", color = Color(0xFFD32F2F), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun BlinkingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "ButterflyCursor")
    
    // [v1.4.0-SAR] Butterfly Smooth: Bounce + Glow + Alpha
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )
    
    val bounceY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bounce"
    )

    Box(
        modifier = Modifier
            .padding(start = 6.dp, bottom = 2.dp)
            .offset(y = bounceY.dp)
            .size(width = 10.dp, height = 10.dp)
            .graphicsLayer {
                this.alpha = alpha
            }
            .shadow(elevation = 4.dp, shape = CircleShape, clip = false)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1F1F1F), Color(0xFF1F1F1F).copy(alpha = 0.3f), Color.Transparent)
                ), 
                CircleShape
            )
    )
}

/**
 * [v1.5.0-SAR] Typewriter Buffer: Smooth character-by-character text reveal.
 *
 * Problem: LLM tokens arrive in bursts (fast-pause-fast), causing the UI to "stutter".
 * Solution: Buffer incoming text and drain it smoothly at adaptive speed.
 *
 * - Normal speed: ~25ms per char (fast, natural typing feel)
 * - Catching up (buffer > 50 chars behind): ~8ms per char (speed burst)
 * - Buffer empty (waiting for LLM): holds at current position (no stutter)
 * - Only active during streaming (isStreaming=true). Once complete, shows full text instantly.
 */
@Composable
fun TypewriterBuffer(
    targetText: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1F1F1F),
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 22.sp,
    enableThinking: Boolean = true,
    onDisplayedTextUpdate: ((String) -> Unit)? = null
) {
    // The number of characters currently visible to the user
    var displayedLength by remember { mutableIntStateOf(0) }
    // Track if we've ever started displaying (to handle initial prefill)
    var hasStartedDisplaying by remember { mutableStateOf(false) }

    // When streaming ends, immediately show full text (for scroll-back, no animation needed)
    if (!isStreaming) {
        LaunchedEffect(Unit) {
            displayedLength = targetText.length
            hasStartedDisplaying = true
        }
    }

    // Adaptive character drip during streaming
    LaunchedEffect(targetText, isStreaming) {
        if (!isStreaming) {
            displayedLength = targetText.length
            return@LaunchedEffect
        }
        
        hasStartedDisplaying = true
        
        // Drip loop: reveal characters one at a time at adaptive speed
        while (displayedLength < targetText.length) {
            val remaining = targetText.length - displayedLength
            
            // Adaptive speed based on buffer depth
            val delayMs = when {
                remaining > 80 -> 5L    // Way behind — sprint to catch up
                remaining > 40 -> 10L   // Behind — fast catch up
                remaining > 15 -> 18L   // Normal buffer — natural typing speed
                remaining > 5 -> 30L    // Buffer getting thin — slow down to buy time
                else -> 45L             // Almost empty — slow drip, LLM will refill soon
            }
            
            displayedLength++
            onDisplayedTextUpdate?.invoke(targetText.substring(0, displayedLength))
            delay(delayMs)
        }
    }

    // Compute the visible portion of text
    val visibleText = if (hasStartedDisplaying && displayedLength > 0) {
        targetText.substring(0, displayedLength.coerceAtMost(targetText.length))
    } else {
        ""
    }

    Row(verticalAlignment = Alignment.Bottom) {
        if (visibleText.isNotEmpty()) {
            MarkdownText(
                text = visibleText,
                color = color,
                fontSize = fontSize,
                lineHeight = lineHeight,
                modifier = modifier.weight(1f, fill = false),
                enableThinking = enableThinking
            )
        }
        
        // Show cursor while streaming and still revealing text
        if (isStreaming) {
            BlinkingCursor()
        }
    }
}

/**
 * [v1.5.0-SAR] Thinking Dots Indicator — Premium prefill animation.
 * 
 * Replaces the boring CircularProgressIndicator during prefill with
 * animated bouncing dots that feel more "alive" and premium.
 * Pattern: ● ● ● with staggered bounce animation.
 */
@Composable
fun ThinkingDotsIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "ThinkingDots")
    
    Row(
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val delay = index * 200 // Stagger each dot

            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 500,
                        delayMillis = delay,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "Dot$index"
            )
            
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 500,
                        delayMillis = delay,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "DotAlpha$index"
            )

            Box(
                modifier = Modifier
                    .offset(y = offsetY.dp)
                    .size(8.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(
                        Color(0xFF0A56D1),
                        CircleShape
                    )
            )
        }
    }
}
@Composable
fun StabilityWarningCard(
    cooldown: Int,
    isReady: Boolean,
    onConfirm: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(24.dp)),
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFFFB300).copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = "Warning: Model May Be Unstable",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1F1F1F)
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = "MemoryGatekeeper has detected extreme memory pressure. Proceeding with this inference may cause system-wide instability or a fatal crash on your current hardware configuration.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5F6368),
                lineHeight = 18.sp
            )
            
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = onConfirm,
                enabled = isReady,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReady) Color(0xFF1F1F1F) else Color(0xFFE0E0E0),
                    contentColor = if (isReady) Color.White else Color(0xFF9E9E9E)
                )
            ) {
                if (isReady) {
                    Text("UNDERSTOOD & PROCEED", fontWeight = FontWeight.Bold)
                } else {
                    Text("WAITING FOR STABILIZATION ($cooldown s)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
