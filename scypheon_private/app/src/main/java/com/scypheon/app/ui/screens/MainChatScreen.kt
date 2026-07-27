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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import com.scypheon.app.core.ui.util.contrastTextColor
import com.scypheon.app.ui.screens.ImageAttachmentRow
import com.scypheon.app.ui.ChatMessageUiState
import com.scypheon.app.ui.NO_MODEL_SELECTED
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
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction

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
    sessionHistory: List<com.scypheon.sdk.core.memory.Session> = emptyList(),
    onNewSession: () -> Unit = {},
    onLoadSession: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    onArchiveSession: (String) -> Unit = {},
    onUnarchiveSession: (String) -> Unit = {},
    onOpenModelHub: () -> Unit = {},
    activeModelName: String = NO_MODEL_SELECTED,
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
    showNoModelWarningDialog: Boolean = false,
    onDismissNoModelWarning: () -> Unit = {},
    onConfirmNoModelWarning: () -> Unit = {},
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
    onIgnoreInconsistency: () -> Unit = {},
    
    // Dynamic Context Scaling
    pendingContextScalingTokens: Int? = null,
    pendingContextScalingReqRamMb: Long = 0L,
    isRamCriticalForScaling: Boolean = false,
    onApproveContextScaling: (Int) -> Unit = {},
    onRejectContextScaling: (Boolean) -> Unit = {}
) {
    val isDark = when (config.themeMode) {
        com.scypheon.sdk.core.model.ThemeMode.DARK -> true
        com.scypheon.sdk.core.model.ThemeMode.LIGHT -> false
        com.scypheon.sdk.core.model.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    
    var showArchiveDialog by remember { mutableStateOf(false) }

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
                drawerContainerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF8F9FA),
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
                    color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF5F6368),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                val activeSessions = remember(sessionHistory) { sessionHistory.filter { !it.isArchived } }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(activeSessions, key = { it.id }) { session ->
                        var showMenu by remember { mutableStateOf(false) }
                        
                        NavigationDrawerItem(
                            label = { 
                                Text(
                                    text = session.title, 
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false)
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
                            badge = {
                                Box {
                                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                        modifier = Modifier
                                            .background(if (isDark) Color(0xFF2C2C2E) else Color.White)
                                            .border(0.5.dp, (if (isDark) Color(0xFF404040) else Color(0xFFE0E0E0)), RoundedCornerShape(8.dp)),
                                        properties = androidx.compose.ui.window.PopupProperties(focusable = true)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodyMedium) },
                                            onClick = {
                                                showMenu = false
                                                onDeleteSession(session.id)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp)) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Archive", color = if (isDark) Color.White else Color(0xFF1F1F1F), style = MaterialTheme.typography.bodyMedium) },
                                            onClick = {
                                                showMenu = false
                                                onArchiveSession(session.id)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = "Archive", tint = if (isDark) Color.LightGray else Color.Gray, modifier = Modifier.size(18.dp)) }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }
                
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Archived Chats") },
                    selected = false,
                    onClick = {
                        scope.launch {
                            delay(50)
                            drawerState.close()
                            showArchiveDialog = true
                        }
                    },
                    icon = { Icon(Icons.Default.Archive, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
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
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        val bgColors = remember(isDark) {
            if (isDark) listOf(Color(0xFF0F0F10), Color(0xFF151517))
            else listOf(Color(0xFFFCFDFF), Color(0xFFF0F4FA))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(bgColors))
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
                            .statusBarsPadding()
                            .padding(top = 12.dp, bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val primaryContentColor = if (isDark) Color.White else Color(0xFF1F1F1F)
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.AutoMirrored.Filled.Segment, contentDescription = "Menu", tint = primaryContentColor, modifier = Modifier.size(28.dp))
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy((-4).dp)
                            ) {
                                Text(
                                    text = "Scypheon Private",
                                    style = TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp,
                                        color = primaryContentColor
                                    )
                                )
                                Surface(
                                    onClick = onShowLocalModelPicker,
                                    shape = CircleShape,
                                    color = if (isDark) Color(0xFF2C2C2E) else Color.Black
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (activeModelName == NO_MODEL_SELECTED || activeModelName.contains("(STANDBY)", ignoreCase = true)) "STANDBY" else activeModelName.uppercase(),
                                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 0.5.sp)
                                        )
                                    }
                                }
                            }

                            Box {
                                val rotationAngle by animateFloatAsState(
                                    targetValue = if (expandedMenu) 90f else 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.7f,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "TuneRotation"
                                )
                                IconButton(onClick = { expandedMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Settings",
                                        tint = primaryContentColor,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .graphicsLayer { rotationZ = rotationAngle }
                                    )
                                }

                                val transitionState = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply {
                                    targetState = expandedMenu
                                }
                                if (transitionState.currentState || transitionState.targetState) {
                                    androidx.compose.ui.window.Popup(
                                        alignment = Alignment.TopEnd,
                                        offset = androidx.compose.ui.unit.IntOffset(-16, 56),
                                        onDismissRequest = { expandedMenu = false },
                                        properties = androidx.compose.ui.window.PopupProperties(focusable = true)
                                    ) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visibleState = transitionState,
                                            enter = scaleIn(
                                                animationSpec = spring(
                                                    dampingRatio = 0.8f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                initialScale = 0.85f
                                            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                                            exit = scaleOut(
                                                animationSpec = spring(
                                                    dampingRatio = 0.85f,
                                                    stiffness = Spring.StiffnessMedium
                                                ),
                                                targetScale = 0.85f
                                            ) + fadeOut(animationSpec = tween(durationMillis = 150))
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .width(220.dp)
                                                    .shadow(16.dp, RoundedCornerShape(22.dp))
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E7EB),
                                                        shape = RoundedCornerShape(22.dp)
                                                    ),
                                                shape = RoundedCornerShape(22.dp),
                                                color = if (isDark) Color(0xF418181A) else Color(0xF4FCFDFF)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(10.dp)
                                                ) {
                                                    CustomPopupItem(
                                                        text = "GraphRAG Oracle",
                                                        icon = Icons.Default.AutoGraph,
                                                        iconColor = Color(0xFF007AFF),
                                                        isDark = isDark,
                                                        onClick = {
                                                            expandedMenu = false
                                                            onOpenGraphExplorer()
                                                        }
                                                    )
                                                    
                                                    Spacer(Modifier.height(2.dp))
                                                    
                                                    CustomPopupItem(
                                                        text = "Aegis Vault",
                                                        icon = Icons.Default.VpnKey,
                                                        iconColor = Color(0xFF7B1FA2),
                                                        isDark = isDark,
                                                        onClick = {
                                                            expandedMenu = false
                                                            onOpenTelemetry()
                                                        }
                                                    )
                                                    
                                                    Spacer(Modifier.height(4.dp))
                                                    HorizontalDivider(color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E7EB), thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))
                                                    Spacer(Modifier.height(4.dp))
                                                    
                                                    CustomPopupItem(
                                                        text = "Scypheon Settings",
                                                        icon = Icons.Default.SettingsInputComponent,
                                                        iconColor = Color(0xFFFF5722),
                                                        isDark = isDark,
                                                        onClick = {
                                                            expandedMenu = false
                                                            onToggleConfig(true)
                                                        }
                                                    )
                                                    
                                                    Spacer(Modifier.height(2.dp))
                                                    
                                                    CustomPopupItem(
                                                        text = "Neural Link Status",
                                                        icon = Icons.Default.ShieldMoon,
                                                        iconColor = Color(0xFF00E676),
                                                        isDark = isDark,
                                                        onClick = {
                                                            expandedMenu = false
                                                            onShowDiagnostics()
                                                        }
                                                    )
                                                    
                                                    Spacer(Modifier.height(4.dp))
                                                    HorizontalDivider(color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E7EB), thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))
                                                    Spacer(Modifier.height(4.dp))
                                                    
                                                    CustomPopupItem(
                                                        text = "Update Identifier",
                                                        icon = Icons.Default.Badge,
                                                        iconColor = Color(0xFF5F6368),
                                                        isDark = isDark,
                                                        onClick = {
                                                            expandedMenu = false
                                                            newNameInput = userName
                                                            showNamePrompt = true
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
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
            if (activeModelName != NO_MODEL_SELECTED) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (pendingContextScalingTokens != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .border(1.dp, if (isRamCriticalForScaling) Color.Red.copy(alpha = 0.5f) else Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isRamCriticalForScaling) Color(0xFFFFF0F0) else Color(0xFFF8FAFC))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isRamCriticalForScaling) Icons.Default.Warning else Icons.Default.Memory,
                                            contentDescription = null,
                                            tint = if (isRamCriticalForScaling) Color.Red else Color(0xFF0A56D1),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (isRamCriticalForScaling) "Critical RAM Warning" else "Context Limit Exceeded",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (isRamCriticalForScaling) Color.Red else Color(0xFF1F2937)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = if (isRamCriticalForScaling) {
                                            "Your prompt is too large. Expanding the context window requires ~${pendingContextScalingReqRamMb}MB of RAM, which your device currently does not have. Proceeding will likely crash the OS."
                                        } else {
                                            "Your prompt exceeds the current context window limit. Expanding to $pendingContextScalingTokens tokens requires ~${pendingContextScalingReqRamMb}MB of RAM. Proceed?"
                                        },
                                        fontSize = 14.sp,
                                        color = Color(0xFF4B5563)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { onRejectContextScaling(true) }) {
                                            Text("Truncate", color = Color(0xFF6B7280))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        if (isRamCriticalForScaling) {
                                            Button(
                                                onClick = { onRejectContextScaling(false) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                            ) {
                                                Text("Abort")
                                            }
                                        } else {
                                            Button(
                                                onClick = { onApproveContextScaling(pendingContextScalingTokens) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A56D1))
                                            ) {
                                                Text("Expand & Proceed")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = if (isDark) Color(0xFF1E1E22) else Color.White,
                            border = BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFF0A56D1).copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                            shadowElevation = 4.dp
                        ) {
                            Column {
                                if (attachedImageUri != null) {
<<<<<<< Updated upstream
                                    Surface(
                                        color = Color(0xFFF1F3F4),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 0.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color(0xFF0A56D1), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Attachment Ready",
                                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F1F1F))
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            IconButton(
                                                onClick = { attachedImageUri = null },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                            }
=======
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                                    
                                    val mimeType = remember(attachedImageUri) {
                                        attachedImageUri?.let { context.contentResolver.getType(it) } ?: ""
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 0.dp)
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                            .background(Color(0xFFF1F3F4))
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onLongPress = {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                        attachedImageUri = null
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (mimeType.startsWith("image/")) {
                                            androidx.compose.foundation.Image(
                                                painter = coil.compose.rememberAsyncImagePainter(attachedImageUri),
                                                contentDescription = "Attached Image Preview",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            val icon = when {
                                                mimeType.startsWith("video/") -> androidx.compose.material.icons.Icons.Default.OndemandVideo
                                                mimeType.startsWith("audio/") -> androidx.compose.material.icons.Icons.Default.Audiotrack
                                                mimeType == "application/pdf" -> androidx.compose.material.icons.Icons.Default.PictureAsPdf
                                                mimeType.startsWith("text/") -> androidx.compose.material.icons.Icons.Default.Description
                                                else -> androidx.compose.material.icons.Icons.AutoMirrored.Filled.InsertDriveFile
                                            }
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = "Attached File",
                                                tint = Color(0xFF0A56D1),
                                                modifier = Modifier.size(32.dp)
                                            )
>>>>>>> Stashed changes
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                IconButton(onClick = { 
                                    scope.launch { delay(50); showAttachmentMenu = true } 
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "Attachments", tint = if (isDark) Color.White else Color(0xFF1F1F1F), modifier = Modifier.size(24.dp))
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
                                            style = TextStyle(color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF5F6368), fontSize = 16.sp)
                                        )
                                    }
                                    BasicTextField(
                                        value = inputText,
                                        onValueChange = { inputText = it },
                                        textStyle = TextStyle(color = if (isDark) Color.White else Color(0xFF1F1F1F), fontSize = 16.sp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp) {
                                                    scope.launch {
                                                        delay(50)
                                                        val success = onSendMessage(inputText, attachedImageUri)
                                                        if (success) {
                                                            inputText = ""
                                                            attachedImageUri = null
                                                        }
                                                    }
                                                    true
                                                } else {
                                                    false
                                                }
                                            },
                                        keyboardOptions = KeyboardOptions(
                                            imeAction = ImeAction.Send
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSend = {
                                                scope.launch {
                                                    delay(50)
                                                    val success = onSendMessage(inputText, attachedImageUri)
                                                    if (success) {
                                                        inputText = ""
                                                        attachedImageUri = null
                                                    }
                                                }
                                            }
                                        )
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
                                            Icon(Icons.Outlined.Mic, contentDescription = "Mic", tint = if (isDark) Color.White else Color(0xFF1F1F1F))
                                        }

                                        IconButton(onClick = { 
                                            scope.launch { delay(50); onToggleLiveMode() } 
                                        }) {
                                            Icon(
                                                Icons.Default.GraphicEq, 
                                                contentDescription = "Live Voice", 
                                                tint = if (isLiveModeActive) Color(0xFF007AFF) else (if (isDark) Color.White else Color(0xFF1F1F1F)),
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
                                            colors = IconButtonDefaults.iconButtonColors(containerColor = if (isDark) Color.White else Color.Black)
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Send", tint = if (isDark) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                                        }
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
                                    progress = { currentProgress },
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
                } else if (activeModelName == NO_MODEL_SELECTED) {
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
                    val reversedMessages = remember(messages) { messages.reversed() }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        reverseLayout = true
                    ) {
                        items(reversedMessages, key = { it.id }) { msg ->
                            ScypheonChatBubble(msg, config = config, onRetryMessage = onRetryMessage, enableThinking = config.enableThinking)
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
                        title = { 
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = Color(0xFFD97706) // Darker, more readable amber
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                                Text("System Warning", fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                            }
                        },
                        text = { Text(systemWarning) },
                        confirmButton = {
                            Button(
                                onClick = onConfirmWarning,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD97706),
                                    contentColor = Color.White
                                )
                            ) { Text("Force Load") }
                        },
                        dismissButton = {
                            TextButton(onClick = onDismissWarning) { Text("Cancel") }
                        }
                    )
                }

                if (showNoModelWarningDialog) {
                    AlertDialog(
                        onDismissRequest = onDismissNoModelWarning,
                        icon = {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color(0xFF0A56D1),
                                modifier = Modifier.size(36.dp)
                            )
                        },
                        title = {
                            Text(
                                "Model Download Required",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        text = {
                            Text(
                                "To interact with the AI assistant or start Live Mode, you must first download an AI model. Would you like to open the Model Hub to get started?",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = onConfirmNoModelWarning,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A56D1))
                            ) {
                                Text("Go to Model Hub", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = onDismissNoModelWarning) {
                                Text("Cancel", color = Color.Gray)
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
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
                
                AnimatedVisibility(
                    visible = isConfigVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    com.scypheon.app.ui.screens.SettingsScreen(
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
                    containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
                    dragHandle = null,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    AttachmentMenuSheet(
                        isDark = isDark,
                        onOptionSelected = { option ->
                            showAttachmentMenu = false
                            when (option) {
                                "Gallery" -> { imageLauncher.launch("image/*") }
                                "Camera" -> { imageLauncher.launch("image/*") }
                                "File" -> { imageLauncher.launch("*/*") }
                                "Cloud" -> { imageLauncher.launch("*/*") }
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("No local models found.", color = Color.Gray)
                                Button(
                                    onClick = {
                                        onHideLocalModelPicker()
                                        onOpenModelHub()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open Model Hub")
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                items(localModels, key = { it.absolutePath }) { file ->
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
        
        if (showArchiveDialog) {
            val archivedSessions = remember(sessionHistory) { sessionHistory.filter { it.isArchived } }
            ArchivedChatsDialog(
                archivedSessions = archivedSessions,
                onDismiss = { showArchiveDialog = false },
                onUnarchive = onUnarchiveSession,
                onDelete = onDeleteSession,
                onLoadSession = onLoadSession,
                isDark = isDark
            )
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
fun AnnotatedString.appendCursor(color: Color, alpha: Float): AnnotatedString {
    val cursorColor = if (color == Color.Unspecified) Color(0xFF1F1F1F) else color
    return buildAnnotatedString {
        append(this@appendCursor)
        withStyle(
            SpanStyle(
                color = cursorColor.copy(alpha = alpha),
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
        ) {
            append("·")
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 22.sp,
    enableThinking: Boolean = true,
    isDark: Boolean = false,
    showCursor: Boolean = false,
    revealTimes: List<Long>? = null,
    tickerTime: Long = 0L,
    isStreaming: Boolean = false
) {
    var thoughtContent: String? = null
    var contentWithoutThought = text
    var isThinkingDone = false
    
    if (enableThinking) {
        val startIdx = text.indexOf("<thought>")
        if (startIdx != -1) {
            val endIdx = text.indexOf("</thought>")
            if (endIdx != -1) {
                thoughtContent = text.substring(startIdx + 9, endIdx).trim()
                contentWithoutThought = text.removeRange(startIdx, endIdx + 10).trim()
                isThinkingDone = true
            } else {
                thoughtContent = text.substring(startIdx + 9).trim()
                contentWithoutThought = text.substring(0, startIdx).trim()
                isThinkingDone = false
            }
        }
    }

    var isExpanded by remember(isThinkingDone) { mutableStateOf(!isThinkingDone) }

    val infiniteTransition = rememberInfiniteTransition(label = "MarkdownCursor")
    val cursorAlpha by if (showCursor) {
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "CursorAlpha"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Column(modifier = modifier) {
        if (thoughtContent != null && thoughtContent.isNotEmpty()) {
            val isThinkingActive = !isThinkingDone
            val surfaceColor = if (isDark) {
                if (isThinkingActive) Color(0xFF1E2A3A) else Color(0xFF2C2C2C)
            } else {
                if (isThinkingActive) Color(0xFFF0F4FA) else Color(0xFFF8F9FA)
            }
            val borderColor = if (isDark) {
                if (isThinkingActive) Color(0xFF2A3F5C) else Color(0xFF404040)
            } else {
                if (isThinkingActive) Color(0xFFD6E4F8) else Color(0xFFE0E0E0)
            }
            val primaryTextColor = if (isDark) Color(0xFF8AB4F8) else Color(0xFF0A56D1)
            
            Surface(
                color = surfaceColor,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { isExpanded = !isExpanded }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = com.scypheon.app.ui.components.ScypheonIcons.ScypheonReasoningIcon,
                            contentDescription = "Reasoning Icon",
                            modifier = Modifier.size(16.dp),
                            tint = primaryTextColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isThinkingActive) "Thinking..." else "Reasoning Process",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryTextColor,
                                letterSpacing = 0.5.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        val rotation by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle",
                            tint = Color.Gray,
                            modifier = Modifier.graphicsLayer(rotationZ = rotation).size(20.dp)
                        )
                    }
                    
                    androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            androidx.compose.material3.Divider(color = if (isDark) Color(0xFF404040) else Color(0xFFE0E0E0), thickness = 0.5.dp, modifier = Modifier.padding(bottom = 8.dp))
                            Text(
                                text = thoughtContent,
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = Color(0xFF5F6368),
                                    lineHeight = 20.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        val blocks = remember(contentWithoutThought) { parseMarkdown(contentWithoutThought) }
        val lastTextBlockIndex = blocks.indexOfLast { 
            it is MarkdownBlock.Paragraph || it is MarkdownBlock.ListItem || it is MarkdownBlock.OrderedListItem || it is MarkdownBlock.Header 
        }

        var currentOffset = 0

        blocks.forEachIndexed { index, block ->
            val blockText = when (block) {
                is MarkdownBlock.Header -> block.text
                is MarkdownBlock.ListItem -> block.text
                is MarkdownBlock.OrderedListItem -> block.text
                is MarkdownBlock.Paragraph -> block.text
                is MarkdownBlock.Table -> ""
            }
            
            val absoluteStartIdx = if (blockText.isNotEmpty()) {
                val found = contentWithoutThought.indexOf(blockText, currentOffset)
                if (found != -1) {
                    currentOffset = found + blockText.length
                    found
                } else {
                    currentOffset
                }
            } else {
                currentOffset
            }

            val showCursorInThisBlock = showCursor && index == lastTextBlockIndex

            when (block) {
                is MarkdownBlock.Header -> {
                    val scale = when (block.level) {
                        1 -> 1.5f
                        2 -> 1.35f
                        3 -> 1.2f
                        4 -> 1.1f
                        5 -> 1.0f
                        else -> 0.9f
                    }
                    val weight = if (block.level <= 5) FontWeight.Bold else FontWeight.SemiBold
                    val topPadding = if (block.level == 1) 12.dp else 8.dp
                    
                    val baseAnnotated = parseInlineMarkdown(
                        text = block.text,
                        absoluteStartIdx = absoluteStartIdx,
                        isStreaming = isStreaming,
                        revealTimes = revealTimes,
                        currentTime = tickerTime,
                        settleDuration = 200L,
                        isDark = isDark
                    )
                    val finalAnnotated = if (showCursorInThisBlock) {
                        baseAnnotated.appendCursor(color = color, alpha = cursorAlpha)
                    } else {
                        baseAnnotated
                    }

                    Text(
                        text = finalAnnotated,
                        style = TextStyle(
                            fontSize = fontSize * scale,
                            fontWeight = weight,
                            color = color,
                            lineHeight = lineHeight * scale
                        ),
                        modifier = Modifier.padding(top = topPadding, bottom = 4.dp)
                    )
                }
                is MarkdownBlock.ListItem -> {
                    Row(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                        Text("• ", color = color, fontSize = fontSize)
                        val baseAnnotated = parseInlineMarkdown(
                            text = block.text,
                            absoluteStartIdx = absoluteStartIdx,
                            isStreaming = isStreaming,
                            revealTimes = revealTimes,
                            currentTime = tickerTime,
                            settleDuration = 200L,
                            isDark = isDark
                        )
                        val finalAnnotated = if (showCursorInThisBlock) {
                            baseAnnotated.appendCursor(color = color, alpha = cursorAlpha)
                        } else {
                            baseAnnotated
                        }
                        Text(
                            text = finalAnnotated,
                            color = color,
                            fontSize = fontSize,
                            lineHeight = lineHeight
                        )
                    }
                }
                is MarkdownBlock.OrderedListItem -> {
                    Row(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                        Text("${block.number}. ", color = color, fontSize = fontSize)
                        val baseAnnotated = parseInlineMarkdown(
                            text = block.text,
                            absoluteStartIdx = absoluteStartIdx,
                            isStreaming = isStreaming,
                            revealTimes = revealTimes,
                            currentTime = tickerTime,
                            settleDuration = 200L,
                            isDark = isDark
                        )
                        val finalAnnotated = if (showCursorInThisBlock) {
                            baseAnnotated.appendCursor(color = color, alpha = cursorAlpha)
                        } else {
                            baseAnnotated
                        }
                        Text(
                            text = finalAnnotated,
                            color = color,
                            fontSize = fontSize,
                            lineHeight = lineHeight
                        )
                    }
                }
                is MarkdownBlock.Table -> {
                    val numCols = maxOf(block.headers?.size ?: 0, block.rows.maxOfOrNull { it.size } ?: 0)
                    if (numCols > 0) {
                        val colWidths = remember(block.headers, block.rows) {
                            val widths = IntArray(numCols) { 0 }
                            if (block.headers != null) {
                                for (j in 0 until numCols) {
                                    if (j < block.headers.size) {
                                        widths[j] = maxOf(widths[j], block.headers[j].length)
                                    }
                                }
                            }
                            for (row in block.rows) {
                                for (j in 0 until numCols) {
                                    if (j < row.size) {
                                        widths[j] = maxOf(widths[j], row[j].length)
                                    }
                                }
                            }
                            widths
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .horizontalScroll(scrollState)
                                .background(
                                    if (isDark) Color(0xFF1E1E1E) else Color(0xFFF8F9FA),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            // Headers
                            if (block.headers != null) {
                                Row(
                                    modifier = Modifier
                                        .background(if (isDark) Color(0xFF2D2D2D) else Color(0xFFECEFF1))
                                        .padding(vertical = 8.dp)
                                ) {
                                    for (colIndex in 0 until numCols) {
                                        val headerText = if (colIndex < block.headers.size) block.headers[colIndex] else ""
                                        val cellWidth = (colWidths[colIndex] * 8 + 32).coerceIn(80, 250).dp
                                        Box(
                                            modifier = Modifier
                                                .width(cellWidth)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(
                                                text = parseInlineMarkdown(headerText),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = fontSize * 0.9f,
                                                color = if (isDark) Color.White else Color(0xFF1F1F1F)
                                            )
                                        }
                                    }
                                }
                                androidx.compose.material3.Divider(
                                    color = if (isDark) Color(0xFF444444) else Color(0xFFCCCCCC),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            
                            // Rows
                            block.rows.forEachIndexed { rowIndex, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (rowIndex % 2 == 1) {
                                                if (isDark) Color(0xFF252525) else Color(0xFFF1F3F4)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .padding(vertical = 6.dp)
                                ) {
                                    for (colIndex in 0 until numCols) {
                                        val cellText = if (colIndex < row.size) row[colIndex] else ""
                                        val cellWidth = (colWidths[colIndex] * 8 + 32).coerceIn(80, 250).dp
                                        Box(
                                            modifier = Modifier
                                                .width(cellWidth)
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(
                                                text = parseInlineMarkdown(cellText),
                                                fontSize = fontSize * 0.9f,
                                                color = color
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    if (block.text.isNotEmpty()) {
                        val baseAnnotated = parseInlineMarkdown(
                            text = block.text,
                            absoluteStartIdx = absoluteStartIdx,
                            isStreaming = isStreaming,
                            revealTimes = revealTimes,
                            currentTime = tickerTime,
                            settleDuration = 200L,
                            isDark = isDark
                        )
                        val finalAnnotated = if (showCursorInThisBlock) {
                            baseAnnotated.appendCursor(color = color, alpha = cursorAlpha)
                        } else {
                            baseAnnotated
                        }
                        Text(
                            text = finalAnnotated,
                            color = color,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    } else {
                        if (showCursorInThisBlock) {
                            Text(
                                text = buildAnnotatedString {
                                    val cursorColor = if (color == Color.Unspecified) Color(0xFF1F1F1F) else color
                                    withStyle(SpanStyle(color = cursorColor.copy(alpha = cursorAlpha), fontWeight = FontWeight.Black, fontSize = 20.sp)) {
                                        append("·")
                                    }
                                },
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

sealed interface MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock
    data class ListItem(val text: String) : MarkdownBlock
    data class OrderedListItem(val number: String, val text: String) : MarkdownBlock
    data class Table(val headers: List<String>?, val rows: List<List<String>>) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

fun parseMarkdown(content: String): List<MarkdownBlock> {
    val lines = content.split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    val currentTableRows = mutableListOf<String>()

    fun isSeparatorRow(line: String): Boolean {
        val cleaned = line.replace("|", "").replace("-", "").replace(":", "").trim()
        return cleaned.isEmpty() && line.contains("-")
    }

    fun parseTableRow(line: String): List<String> {
        val parts = line.split("|")
        val startIdx = if (line.startsWith("|")) 1 else 0
        val endIdx = if (line.endsWith("|")) parts.size - 1 else parts.size
        val result = mutableListOf<String>()
        for (i in startIdx until endIdx) {
            result.add(parts[i].trim())
        }
        return result
    }

    fun flushTable() {
        if (currentTableRows.isNotEmpty()) {
            if (currentTableRows.size >= 2 && isSeparatorRow(currentTableRows[1])) {
                val headers = parseTableRow(currentTableRows[0])
                val dataRows = mutableListOf<List<String>>()
                for (i in 2 until currentTableRows.size) {
                    if (!isSeparatorRow(currentTableRows[i])) {
                        dataRows.add(parseTableRow(currentTableRows[i]))
                    }
                }
                blocks.add(MarkdownBlock.Table(headers, dataRows))
            } else {
                // Not a complete table yet (no separator row), render as regular paragraphs
                for (rowLine in currentTableRows) {
                    blocks.add(MarkdownBlock.Paragraph(rowLine))
                }
            }
            currentTableRows.clear()
        }
    }

    val orderedListRegex = Regex("^(\\d+)\\.\\s+(.*)")

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.contains("|")) {
            currentTableRows.add(line)
            i++
            continue
        } else {
            flushTable()
        }

        when {
            trimmed.startsWith("#") -> {
                val hashCount = trimmed.takeWhile { it == '#' }.length
                if (hashCount in 1..6 && trimmed.length > hashCount && trimmed[hashCount] == ' ') {
                    val headerText = trimmed.substring(hashCount + 1).trim()
                    blocks.add(MarkdownBlock.Header(hashCount, headerText))
                } else {
                    blocks.add(MarkdownBlock.Paragraph(line))
                }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks.add(MarkdownBlock.ListItem(trimmed.substring(2)))
            }
            else -> {
                val match = orderedListRegex.matchEntire(trimmed)
                if (match != null) {
                    val num = match.groupValues[1]
                    val itemText = match.groupValues[2]
                    blocks.add(MarkdownBlock.OrderedListItem(num, itemText))
                } else {
                    blocks.add(MarkdownBlock.Paragraph(line))
                }
            }
        }
        i++
    }
    flushTable()
    return blocks
}

fun parseInlineMarkdown(
    text: String,
    absoluteStartIdx: Int = 0,
    isStreaming: Boolean = false,
    revealTimes: List<Long>? = null,
    currentTime: Long = 0L,
    settleDuration: Long = 200L,
    isDark: Boolean = false
): AnnotatedString {
    fun AnnotatedString.Builder.appendWithScramble(
        subText: String,
        subTextStartInBlock: Int,
        baseStyle: SpanStyle? = null
    ) {
        if (!isStreaming || revealTimes == null) {
            if (baseStyle != null) {
                withStyle(baseStyle) {
                    append(subText)
                }
            } else {
                append(subText)
            }
            return
        }

        val scramblePool = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#@$&%?"
        
        for (i in subText.indices) {
            val originalChar = subText[i]
            val absIdx = absoluteStartIdx + subTextStartInBlock + i
            
            if (originalChar.isWhitespace() || originalChar == '\n' || originalChar == '\r' ||
                originalChar == '*' || originalChar == '`' || originalChar == '_' || originalChar == '#' ||
                originalChar == '|' || originalChar == '-' || originalChar == '[' || originalChar == ']' ||
                originalChar == '(' || originalChar == ')'
            ) {
                if (baseStyle != null) {
                    withStyle(baseStyle) {
                        append(originalChar.toString())
                    }
                } else {
                    append(originalChar.toString())
                }
                continue
            }
            
            val revealTime = revealTimes.getOrNull(absIdx) ?: 0L
            val age = currentTime - revealTime
            
            if (revealTime > 0 && age < settleDuration) {
                val seed = (absIdx * 31 + currentTime / 40).toInt()
                val randomCharIndex = (seed.coerceAtLeast(0)) % scramblePool.length
                val scrambleChar = scramblePool[randomCharIndex]
                
                val scrambleColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF7C4DFF)
                val scrambleStyle = baseStyle?.copy(
                    color = scrambleColor,
                    fontWeight = FontWeight.Bold
                ) ?: SpanStyle(
                    color = scrambleColor,
                    fontWeight = FontWeight.Bold
                )
                
                withStyle(scrambleStyle) {
                    append(scrambleChar.toString())
                }
            } else {
                if (baseStyle != null) {
                    withStyle(baseStyle) {
                        append(originalChar.toString())
                    }
                } else {
                    append(originalChar.toString())
                }
            }
        }
    }

    val regex = Regex("(\\*\\*.*?\\*\\*|\\*\\*.*$)|(\\*(?!\\*).*?\\*(?!\\*)|\\*(?!\\*).*$)|(`.*?`|`.*$)")
    return buildAnnotatedString {
        var cursor = 0
        val matches = regex.findAll(text)
        
        matches.forEach { match ->
            if (cursor <= match.range.first) {
                val plainText = text.substring(cursor, match.range.first)
                appendWithScramble(
                    subText = plainText,
                    subTextStartInBlock = cursor
                )
            }
            
            val matchValue = match.value
            when {
                matchValue.startsWith("**") -> {
                    val innerText = if (matchValue.endsWith("**") && matchValue.length >= 4) {
                        matchValue.substring(2, matchValue.length - 2)
                    } else {
                        matchValue.substring(2)
                    }
                    appendWithScramble(
                        subText = innerText,
                        subTextStartInBlock = match.range.first + 2,
                        baseStyle = SpanStyle(fontWeight = FontWeight.Bold)
                    )
                }
                matchValue.startsWith("*") -> {
                    val innerText = if (matchValue.endsWith("*") && matchValue.length >= 2) {
                        matchValue.substring(1, matchValue.length - 1)
                    } else {
                        matchValue.substring(1)
                    }
                    appendWithScramble(
                        subText = innerText,
                        subTextStartInBlock = match.range.first + 1,
                        baseStyle = SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    )
                }
                matchValue.startsWith("`") -> {
                    val innerText = if (matchValue.endsWith("`") && matchValue.length >= 2) {
                        matchValue.substring(1, matchValue.length - 1)
                    } else {
                        matchValue.substring(1)
                    }
                    appendWithScramble(
                        subText = innerText,
                        subTextStartInBlock = match.range.first + 1,
                        baseStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = Color.LightGray.copy(alpha = 0.3f))
                    )
                }
                else -> {
                    appendWithScramble(
                        subText = matchValue,
                        subTextStartInBlock = match.range.first
                    )
                }
            }
            cursor = match.range.last + 1
        }
        
        if (cursor < text.length) {
            val trailingText = text.substring(cursor)
            appendWithScramble(
                subText = trailingText,
                subTextStartInBlock = cursor
            )
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
    config: com.scypheon.sdk.core.model.ScypheonConfig,
    onRetryMessage: (String, Uri?) -> Unit = { _, _ -> },
    enableThinking: Boolean = true
) {
    val context = LocalContext.current
    
    val isDark = when (config.themeMode) {
        com.scypheon.sdk.core.model.ThemeMode.DARK -> true
        com.scypheon.sdk.core.model.ThemeMode.LIGHT -> false
        com.scypheon.sdk.core.model.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    
    val userBubbleColors = remember(config.chatBubbleStyle, isDark) {
        when (config.chatBubbleStyle) {
            com.scypheon.sdk.core.model.ChatBubbleStyle.GRADIENT_BLUE -> listOf(Color(0xFF007AFF), Color(0xFF5856D6))
            com.scypheon.sdk.core.model.ChatBubbleStyle.GRADIENT_WARM -> listOf(Color(0xFFFF512F), Color(0xFFDD2476))
            com.scypheon.sdk.core.model.ChatBubbleStyle.GRADIENT_GREEN -> listOf(Color(0xFF11998E), Color(0xFF38EF7D))
            com.scypheon.sdk.core.model.ChatBubbleStyle.MINIMALIST_SOLID -> listOf(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE9E9EB))
        }
    }
    
    val userBubbleBrush = remember(userBubbleColors) {
        if (userBubbleColors.size == 1) SolidColor(userBubbleColors.first())
        else Brush.linearGradient(userBubbleColors)
    }
    
    val userTextColor = remember(config.chatBubbleStyle, isDark) {
        when (config.chatBubbleStyle) {
            com.scypheon.sdk.core.model.ChatBubbleStyle.GRADIENT_BLUE -> Color.White
            com.scypheon.sdk.core.model.ChatBubbleStyle.GRADIENT_WARM -> Color.White
            com.scypheon.sdk.core.model.ChatBubbleStyle.GRADIENT_GREEN -> Color.White
            com.scypheon.sdk.core.model.ChatBubbleStyle.MINIMALIST_SOLID -> if (isDark) Color.White else Color(0xFF1C1C1E)
        }
    }
    
    val aiBubbleColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val aiTextColor = remember(aiBubbleColor) { aiBubbleColor.contrastTextColor() }
    val isError = msg.text.startsWith("Error:")
    val alignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "BubbleScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "BubbleAlpha"
    )
    val slideY by animateDpAsState(
        targetValue = if (visible) 0.dp else 10.dp,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "BubbleSlide"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                translationY = slideY.toPx()
            },
        contentAlignment = alignment
    ) {
        if (msg.isUser) {
            Box(
                modifier = Modifier
                    .padding(start = 64.dp, end = 16.dp)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp))
                    .background(userBubbleBrush, shape = RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Column {
                    if (msg.imageUri != null) {
                        var showFullImage by remember { mutableStateOf(false) }
                        
                        if (showFullImage) {
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = { showFullImage = false },
                                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.9f))
                                        .clickable { showFullImage = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    coil.compose.AsyncImage(
                                        model = msg.imageUri,
                                        contentDescription = "Full Image",
                                        modifier = Modifier.fillMaxSize().padding(16.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                            }
                        }

                        coil.compose.AsyncImage(
                            model = msg.imageUri,
                            contentDescription = "Attached Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f) // "ngotak"
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                .clickable { showFullImage = true },
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            alignment = Alignment.TopCenter // "tengah paling atas"
                        )
                    }
                    MarkdownText(
                        text = msg.text.replace("[Image Attached] ", ""),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        color = userTextColor,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        isDark = isDark
                    )
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.padding(start = 16.dp, end = 48.dp)) {
                // Aesthetic top label for AI
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                ) {
                    Icon(
                        imageVector = com.scypheon.app.ui.components.ScypheonIcons.ScypheonAiIcon,
                        contentDescription = "AI",
                        tint = if (isDark) Color(0xFF8AB4F8) else Color(0xFF0A56D1),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Scypheon AI",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFB0B0B0) else Color(0xFF5F6368),
                            letterSpacing = 0.5.sp
                        )
                    )
                }
                
                if (msg.isLoading && msg.text == "Processing...") {
                    // [v1.5.0-SAR] Premium prefill animation — bouncing dots instead of spinner
                    ThinkingDotsIndicator(isDark = isDark)
                } else {
                    // Unified Bubble for Streaming and Thinking
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = aiBubbleColor,
                        border = BorderStroke(0.5.dp, Color(0xFF0A56D1).copy(alpha = 0.08f)),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
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

                                    // [v1.6.0-SAR] REASONING BLOCK RENDERING (Enterprise Shield)
                                    // If thinkingText is present, we show it in a dedicated collapsible surface
                                    // to separate the "thought process" from the final "answer".
                                    if (enableThinking && !msg.thinkingText.isNullOrBlank()) {
                                        var isExpanded by remember { mutableStateOf(msg.isLoading) }
                                        val isThinkingActive = msg.isLoading && msg.text == "Thinking..."
                                        
                                        // Elegant breathing gradient animation for reasoning state
                                        val infiniteTransition = rememberInfiniteTransition(label = "ReasoningPulse")
                                        val breathingAlpha by infiniteTransition.animateFloat(
                                            initialValue = 0.5f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1500, easing = FastOutSlowInEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "BreathingAlpha"
                                        )

                                        val surfaceColor = if (isDark) {
                                            if (isThinkingActive) Color(0xFF1E2A3A).copy(alpha = breathingAlpha) else Color(0xFF2C2C2C)
                                        } else {
                                            if (isThinkingActive) Color(0xFFE8F0FE).copy(alpha = breathingAlpha) else Color(0xFFF8F9FA)
                                        }
                                        val borderColor = if (isDark) {
                                            if (isThinkingActive) Color(0xFF2A3F5C).copy(alpha = breathingAlpha) else Color(0xFF404040)
                                        } else {
                                            if (isThinkingActive) Color(0xFFD6E4F8).copy(alpha = breathingAlpha) else Color(0xFFE0E0E0)
                                        }
                                        val primaryTextColor = if (isDark) Color(0xFF8AB4F8) else Color(0xFF0A56D1)

                                        Surface(
                                            color = surfaceColor,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, borderColor),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = if (isThinkingActive) 0.dp else 12.dp)
                                                .clickable { isExpanded = !isExpanded }
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = com.scypheon.app.ui.components.ScypheonIcons.ScypheonReasoningIcon,
                                                        contentDescription = "Reasoning",
                                                        modifier = Modifier.size(16.dp).graphicsLayer(alpha = if(isThinkingActive) breathingAlpha else 1f),
                                                        tint = primaryTextColor
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = if (isThinkingActive) "Reasoning..." else "Reasoning Process",
                                                        style = TextStyle(
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = primaryTextColor.copy(alpha = if(isThinkingActive) breathingAlpha else 1f),
                                                            letterSpacing = 0.5.sp
                                                        ),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    val rotation by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)
                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                                                        contentDescription = "Toggle",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.graphicsLayer(rotationZ = rotation).size(20.dp)
                                                    )
                                                }

                                                androidx.compose.animation.AnimatedVisibility(
                                                    visible = isExpanded,
                                                    enter = expandVertically(
                                                        animationSpec = spring(
                                                            dampingRatio = 0.8f,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    ) + fadeIn(),
                                                    exit = shrinkVertically(
                                                        animationSpec = spring(
                                                            dampingRatio = 0.85f,
                                                            stiffness = Spring.StiffnessMedium
                                                        )
                                                    ) + fadeOut()
                                                ) {
                                                    Column {
                                                        Spacer(Modifier.height(8.dp))
                                                        Text(
                                                            text = msg.thinkingText ?: "",
                                                            style = TextStyle(
                                                                fontSize = 14.sp,
                                                                color = if (isDark) Color(0xFFB0B0B0) else Color(0xFF5F6368),
                                                                lineHeight = 20.sp
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // [v1.5.0-SAR] Typewriter Buffer for streaming — smooth character reveal
                                    if (msg.isLoading && msg.text != "Thinking...") {
                                        TypewriterBuffer(
                                            targetText = msg.text,
                                            isStreaming = true,
                                            color = if (msg.status == com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED) Color(0xFFD32F2F) else aiTextColor,
                                            fontSize = 16.sp,
                                            lineHeight = 22.sp,
                                            enableThinking = enableThinking,
                                            isDark = isDark
                                        )
                                    } else if (msg.text != "Thinking...") {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            MarkdownText(
                                                text = msg.text,
                                                color = if (msg.status == com.scypheon.sdk.core.memory.ScypheonDbHelper.STATUS_FAILED) Color(0xFFD32F2F) else aiTextColor,
                                                fontSize = 16.sp,
                                                lineHeight = 22.sp,
                                                modifier = Modifier.weight(1f, fill = false),
                                                enableThinking = enableThinking,
                                                isDark = isDark
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
                                    
                                    if (msg.disclaimerType != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isDark) Color(0xFF3E2723) else Color(0xFFFFF3E0),
                                            border = BorderStroke(1.dp, if (isDark) Color(0xFF5D4037) else Color(0xFFFFCDD2)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp)) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = "Disclaimer",
                                                    tint = if (isDark) Color(0xFFFF8A65) else Color(0xFFD32F2F),
                                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                val disclaimerText = when (msg.disclaimerType) {
                                                    "MEDICAL" -> "This is a high-risk medical/clinical query. The AI response is not verified by grounded resources, may hallucinate, and should not replace professional medical advice."
                                                    "EDUCATION" -> "Critical domain without grounded resource verification. AI may hallucinate and is limited by its training knowledge cutoff."
                                                    else -> "Domain requires verification."
                                                }
                                                Text(
                                                    text = disclaimerText,
                                                    style = TextStyle(fontSize = 11.sp, color = if (isDark) Color(0xFFFFCCBC) else Color(0xFFB71C1C), lineHeight = 16.sp)
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

@Composable
fun ArchivedChatsDialog(
    archivedSessions: List<com.scypheon.sdk.core.memory.Session>,
    onDismiss: () -> Unit,
    onUnarchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onLoadSession: (String) -> Unit,
    isDark: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Archived Chats",
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF1F1F1F)
            )
        },
        text = {
            if (archivedSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No archived conversations.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(archivedSessions, key = { it.id }) { session ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLoadSession(session.id)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = if (isDark) Color.LightGray else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = session.title,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                                color = if (isDark) Color.White else Color(0xFF1F1F1F),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(
                                onClick = { onUnarchive(session.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Unarchive,
                                    contentDescription = "Unarchive",
                                    tint = if (isDark) Color(0xFF8AB4F8) else Color(0xFF0A56D1),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDelete(session.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}
@Composable
fun AttachmentMenuSheet(isDark: Boolean, onOptionSelected: (String) -> Unit) {
    val bgColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val surfaceColor = if (isDark) Color(0xFF2C2C2E) else Color.White
    val dividerColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(top = 12.dp, bottom = 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Custom drag handle for iOS look
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(if (isDark) Color(0xFF5F5F63) else Color(0xFFD1D1D6))
        )
        
        Spacer(Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = surfaceColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                AttachmentOptionRow("Camera", Icons.Default.PhotoCamera, Color(0xFF007AFF), isDark) { onOptionSelected("Camera") }
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 64.dp))
                AttachmentOptionRow("Photo Gallery", Icons.Default.Collections, Color(0xFF34C759), isDark) { onOptionSelected("Gallery") }
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 64.dp))
                AttachmentOptionRow("Document", Icons.Default.Description, Color(0xFFFF9500), isDark) { onOptionSelected("File") }
                HorizontalDivider(color = dividerColor, modifier = Modifier.padding(start = 64.dp))
                AttachmentOptionRow("Cloud Drive", Icons.Default.CloudQueue, Color(0xFF5856D6), isDark) { onOptionSelected("Cloud") }
            }
        }
    }
}

@Composable
fun AttachmentOptionRow(label: String, icon: ImageVector, iconBgColor: Color, isDark: Boolean, onClick: () -> Unit) {
    val textColor = if (isDark) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = label, 
            fontSize = 17.sp,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun BlinkingCursor(color: Color = Color(0xFF1F1F1F)) {
    val infiniteTransition = rememberInfiniteTransition(label = "DotCursor")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    Text(
        text = "·",
        color = color,
        fontSize = 22.sp, // Slightly larger for an elegant middle dot
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .padding(start = 2.dp)
            .graphicsLayer { this.alpha = alpha }
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
    isDark: Boolean = false,
    onDisplayedTextUpdate: ((String) -> Unit)? = null
) {
    // The number of characters currently visible to the user
    var displayedLength by remember { mutableIntStateOf(0) }
    // Track if we've ever started displaying (to handle initial prefill)
    var hasStartedDisplaying by remember { mutableStateOf(false) }

    var lastTargetText by remember { mutableStateOf("") }
    val revealTimes = remember { mutableStateListOf<Long>() }

    if (targetText != lastTargetText) {
        if (!targetText.startsWith(lastTargetText) || targetText.length < lastTargetText.length) {
            revealTimes.clear()
            displayedLength = 0
        }
        lastTargetText = targetText
    }

    // When streaming ends, immediately show full text (for scroll-back, no animation needed)
    if (!isStreaming) {
        LaunchedEffect(Unit) {
            displayedLength = targetText.length
            hasStartedDisplaying = true
        }
    }

    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Adaptive character drip during streaming
    LaunchedEffect(targetText, isStreaming) {
        if (!isStreaming) {
            displayedLength = targetText.length
            return@LaunchedEffect
        }
        
        hasStartedDisplaying = true
        var lastHapticTime = 0L
        var lastSoundTime = 0L
        
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
            
            val now = System.currentTimeMillis()
            // Throttle sound/haptic triggers to emulate realistic mechanical keyboard rhythm
            if (now - lastSoundTime >= 50L) {
                com.scypheon.app.core.ui.util.SynthClickPlayer.playClick()
                lastSoundTime = now
            }
            if (now - lastHapticTime >= 80L) {
                hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                lastHapticTime = now
            }
            
            delay(delayMs)
        }
    }

    // Append reveal times when displayedLength increases
    LaunchedEffect(displayedLength) {
        val now = System.currentTimeMillis()
        while (revealTimes.size < displayedLength) {
            revealTimes.add(now)
        }
    }

    var tickerTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Ticker to drive the scrambling animation
    LaunchedEffect(isStreaming, displayedLength) {
        while (isStreaming) {
            val now = System.currentTimeMillis()
            tickerTime = now
            
            // Check if any character is still scrambling
            val anyScrambling = (0 until displayedLength).any { i ->
                val revealTime = revealTimes.getOrNull(i) ?: 0L
                now - revealTime < 200L
            }
            
            if (!anyScrambling && displayedLength >= targetText.length) {
                delay(100L) // Idle tick
            } else {
                delay(30L) // 33 fps scramble rate
            }
        }
    }

    // Compute the visible portion of text
    val visibleText = if (hasStartedDisplaying && displayedLength > 0) {
        targetText.substring(0, displayedLength.coerceAtMost(targetText.length))
    } else {
        ""
    }

    if (visibleText.isNotEmpty()) {
        MarkdownText(
            text = visibleText,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            modifier = modifier,
            enableThinking = enableThinking,
            isDark = isDark,
            showCursor = isStreaming,
            revealTimes = revealTimes,
            tickerTime = tickerTime,
            isStreaming = isStreaming
        )
    }
}

/**
 * [v1.6.0-SAR] Neural Pulse Indicator — Premium prefill animation.
 * 
 * Replaces the boring bouncing dots with an organic "breathing/pulsing"
 * scale and alpha transition. Features a subtle glow for high-tech aesthetic.
 */
@Composable
fun ThinkingDotsIndicator(isDark: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "NeuralPulseDots")
    
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val delay = index * 250 // Stagger each dot's pulse

            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = delay,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "DotScale$index"
            )
            
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = delay,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "DotAlpha$index"
            )

            val dotColor = if (isDark) Color(0xFF8AB4F8) else Color(0xFF0A56D1)

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { 
                        this.scaleX = scale
                        this.scaleY = scale
                        this.alpha = alpha 
                    }
                    .shadow(
                        elevation = (4 * scale).dp,
                        shape = CircleShape,
                        ambientColor = dotColor,
                        spotColor = dotColor,
                        clip = false
                    )
                    .background(dotColor, CircleShape)
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

fun Color.contrastTextColor(): Color {
    val luminance = this.luminance()
    return if (luminance > 0.179) Color.Black else Color.White
}

@Composable
private fun CustomPopupItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E),
                letterSpacing = 0.1.sp
            )
        )
    }
}
