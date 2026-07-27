package com.scypheon.app.ui.screens

import kotlinx.coroutines.launch

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.scypheon.app.data.models.GraphNode
import com.scypheon.app.ui.viewmodel.GraphViewModel
import com.scypheon.app.ui.views.GraphSurfaceView
import com.scypheon.app.ui.views.TextLODManager
import java.nio.ByteBuffer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.Canvas

// ═══════════════════════════════════════════════════════════════════
// [v1.7.0-SAR] NEURAL VAULT — APPLE-GRADE FLUID PHYSICS & AUTO-FRAMING
// ═══════════════════════════════════════════════════════════════════

data class VaultThemeColors(
    val bg: Color,
    val bgDeep: Color,
    val accent: Color,
    val accentSecondary: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val borderSubtle: Color,
    val divider: Color
)

@Composable
private fun getVaultThemeColors(isDark: Boolean): VaultThemeColors {
    return if (isDark) {
        VaultThemeColors(
            bg = Color(0xFF0F0F10), // Match MainChatScreen dark bg start
            bgDeep = Color(0xFF151517), // Match MainChatScreen dark bg end
            accent = Color(0xFF0A84FF), // iOS Light Blue
            accentSecondary = Color(0xFF5E5CE6), // iOS Indigo
            surface = Color(0xFF1C1C1E), // iOS Dark Elevated Surface
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF8E8E93), // iOS Gray
            textTertiary = Color(0xFF636366),
            borderSubtle = Color(0xFF38383A),
            divider = Color(0xFF2C2C2E)
        )
    } else {
        VaultThemeColors(
            bg = Color(0xFFFCFDFF), // Match MainChatScreen light bg start
            bgDeep = Color(0xFFF0F4FA), // Match MainChatScreen light bg end
            accent = Color(0xFF007AFF), // iOS Blue
            accentSecondary = Color(0xFF5856D6), // iOS Indigo
            surface = Color(0xFFFFFFFF),
            textPrimary = Color(0xFF000000),
            textSecondary = Color(0xFF8E8E93),
            textTertiary = Color(0xFFAEAEB2),
            borderSubtle = Color(0xFFE5E5EA),
            divider = Color(0xFFD1D1D6)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphExplorerScreen(
    viewModel: GraphViewModel,
    graphData: List<com.scypheon.app.data.models.RawGraphEdge>,
    onBack: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val colors = getVaultThemeColors(isDark = isDark)

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    
    // Viewport State
    var isUserInteracting by remember { mutableStateOf(false) }
    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }
    
    // Auto-Framing Animation States
    val animatedScale = remember { Animatable(1f) }
    val animatedOffsetX = remember { Animatable(0f) }
    val animatedOffsetY = remember { Animatable(0f) }
    
    val nodesState by viewModel.nodesState.collectAsState()
    
    // We keep this to match any tap events if we implement node picking in the future
    var isGraphReady by remember { mutableStateOf(false) }
    var mappedBuffer by remember { mutableStateOf<ByteBuffer?>(null) }
    
    val textMeasurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    
    val viewMode by viewModel.currentView.collectAsState()
    var vizMode by remember { mutableStateOf(0) } // 0 = Network, 1 = Overlay

    LaunchedEffect(graphData) {
        viewModel.initGraph(graphData)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "VaultAmbient")
    val ambientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AmbientShift"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(colors.bg, colors.bgDeep)
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                    shadowElevation = 0.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = colors.accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Neural Vault",
                                    style = TextStyle(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        letterSpacing = (-0.3).sp
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    val dotAlpha by infiniteTransition.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1500, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "LiveDot"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .graphicsLayer { alpha = dotAlpha }
                                            .background(
                                                if (graphData.isNotEmpty()) colors.accentSecondary
                                                else colors.textTertiary,
                                                CircleShape
                                            )
                                    )
                                    Text(
                                        text = when {
                                            graphData.isEmpty() -> "No knowledge yet"
                                            graphData.size == 1 -> "1 connection"
                                            else -> "${graphData.size} connections"
                                        },
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            color = colors.textSecondary,
                                            letterSpacing = 0.sp
                                        )
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))
                            Spacer(Modifier.width(48.dp))
                        }
                        
                        // Tab Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TabSegmentedControl(
                                selectedMode = viewMode,
                                onModeSelected = { viewModel.setViewMode(it) },
                                colors = colors
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(colors.borderSubtle)
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AnimatedContent(
                    targetState = viewMode,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, delayMillis = 90)) + 
                         scaleIn(initialScale = 0.96f, animationSpec = tween(220, delayMillis = 90)))
                            .togetherWith(fadeOut(animationSpec = tween(150)))
                    },
                    label = "VaultTabTransition",
                    modifier = Modifier.fillMaxSize()
                ) { targetMode ->
                    if (targetMode == GraphViewModel.VaultViewMode.KNOWLEDGE) {
                        KnowledgeListView(
                            graphData = graphData,
                            colors = colors,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // --- GRAPH VIEW ---
                        if (nodesState.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AndroidView(
                                    factory = { ctx ->
                                        GraphSurfaceView(ctx).apply {
                                            val bgColor = colors.bg
                                            val alpha = (bgColor.alpha * 255).toInt().coerceIn(0, 255)
                                            val red = (bgColor.red * 255).toInt().coerceIn(0, 255)
                                            val green = (bgColor.green * 255).toInt().coerceIn(0, 255)
                                            val blue = (bgColor.blue * 255).toInt().coerceIn(0, 255)
                                            val colorStr = String.format("#%02X%02X%02X%02X", alpha, red, green, blue)
                                            setBackgroundColorHex(colorStr)
                                            allocateSharedBuffer(nodesState.size)
                                            mappedBuffer = this.mappedBuffer
                                            isGraphReady = true
                                            viewModel.engine.nativeSetVizMode(vizMode)
                                        }
                                    },
                                    update = { view ->
                                        view.setTransform(userScale, userOffset.x, userOffset.y)
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectTransformGestures { centroid, pan, zoom, _ ->
                                                if (!isUserInteracting) {
                                                    isUserInteracting = true
                                                    userScale = animatedScale.value
                                                    userOffset = Offset(animatedOffsetX.value, animatedOffsetY.value)
                                                }
                                                val oldScale = userScale
                                                userScale = (userScale * zoom).coerceIn(0.2f, 5f)
                                                userOffset = (userOffset - centroid) * (userScale / oldScale) + centroid + pan
                                                viewModel.engine.nativeRecompute()
                                            }
                                        }
                                )
                                
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    TextLODManager.drawNodeLabels(
                                        drawScope = this,
                                        textMeasurer = textMeasurer,
                                        mappedBuffer = mappedBuffer,
                                        nodes = nodesState,
                                        userScale = userScale,
                                        userOffset = userOffset,
                                        density = density,
                                        baseColor = colors.textPrimary
                                    )
                                }

                                // Viz Mode Selector Overlay (Network vs Overlay)
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 16.dp)
                                        .background(colors.surface.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                                        .border(0.5.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val vizModes = listOf("Network", "Overlay")
                                    for (index in vizModes.indices) {
                                        val modeText = vizModes[index]
                                        val isSel = vizMode == index
                                        val bg by animateColorAsState(
                                            targetValue = if (isSel) colors.accent else Color.Transparent,
                                            label = "vizModeBg"
                                        )
                                        val txtColor by animateColorAsState(
                                            targetValue = if (isSel) Color.White else colors.textSecondary,
                                            label = "vizModeText"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(bg)
                                                .clickable {
                                                    vizMode = index
                                                    viewModel.engine.nativeSetVizMode(index)
                                                }
                                                .padding(horizontal = 14.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                modeText,
                                                style = TextStyle(
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = txtColor
                                                )
                                            )
                                        }
                                    }
                                }

                                // Color Scale Bar Overlay (shown in Overlay mode)
                                AnimatedVisibility(
                                    visible = vizMode == 1,
                                    enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 2 },
                                    exit = fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { it / 2 },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp)
                                        .padding(bottom = if (selectedNode != null) 140.dp else 24.dp)
                                ) {
                                    ColorScaleBar(colors = colors)
                                }
                            }
                        } else {
                            CircularProgressIndicator(
                                color = colors.accent,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
        
                        // Bottom Info Card
                        AnimatedVisibility(
                            visible = selectedNode != null,
                            enter = fadeIn(tween(300)) + slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 2 },
                            exit = fadeOut(tween(200)) + slideOutVertically(tween(300)) { it / 2 },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .padding(bottom = 24.dp)
                        ) {
                            selectedNode?.let { node ->
                                val relatedEdges = graphData.filter {
                                    it.subject.equals(node.id, ignoreCase = true) || it.obj.equals(node.id, ignoreCase = true)
                                }
                                NeuralInfoCard(
                                    node = node,
                                    relatedEdges = relatedEdges,
                                    colors = colors,
                                    onDismiss = { selectedNode = null }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeuralVaultEmptyState(modifier: Modifier = Modifier, colors: VaultThemeColors) {
    val infiniteTransition = rememberInfiniteTransition(label = "EmptyAnim")

    val ringScale1 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "RingPulse1"
    )
    val ringAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "RingAlpha1"
    )
    
    val ringScale2 by infiniteTransition.animateFloat(
        initialValue = 1.0f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(4000, 1000, FastOutSlowInEasing), RepeatMode.Reverse),
        label = "RingPulse2"
    )
    val ringAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(4000, 1000, FastOutSlowInEasing), RepeatMode.Reverse),
        label = "RingAlpha2"
    )

    Column(
        modifier = modifier.padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer { scaleX = ringScale2; scaleY = ringScale2; alpha = ringAlpha2 }
                    .border(0.5.dp, colors.textSecondary, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .graphicsLayer { scaleX = ringScale1; scaleY = ringScale1; alpha = ringAlpha1 }
                    .border(0.8.dp, colors.accentSecondary, CircleShape)
            )
            
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(colors.surface, CircleShape)
                    .border(1.dp, colors.borderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Hub,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "Knowledge Explorer",
            style = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                letterSpacing = (-0.5).sp
            )
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Your semantic repository is empty.\nInteract with the system to organically build the graph.",
            style = TextStyle(
                fontSize = 15.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        )

        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralFeaturePill(Icons.Default.Hub, "Entities", colors.accent, colors.surface, colors.textPrimary, colors.textSecondary, colors.borderSubtle)
            NeuralFeaturePill(Icons.Default.AutoGraph, "Relations", colors.accent, colors.surface, colors.textPrimary, colors.textSecondary, colors.borderSubtle)
            NeuralFeaturePill(Icons.Default.Shield, "Local Vault", colors.accent, colors.surface, colors.textPrimary, colors.textSecondary, colors.borderSubtle)
        }
    }
}

@Composable
private fun NeuralFeaturePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    surface: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderSubtle: Color
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = surface,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = textSecondary, modifier = Modifier.size(12.dp))
            Text(
                label,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary,
                    letterSpacing = 0.2.sp
                )
            )
        }
    }
}

@Composable
fun NeuralInfoCard(
    node: GraphNode,
    relatedEdges: List<com.scypheon.app.data.models.RawGraphEdge>,
    colors: VaultThemeColors,
    onDismiss: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = colors.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
        shadowElevation = 8.dp,
        onClick = onDismiss
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderSubtle)
                    .align(Alignment.CenterHorizontally)
            )
 
            Spacer(Modifier.height(16.dp))
 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            colors.bgDeep,
                            RoundedCornerShape(12.dp)
                        )
                        .border(0.5.dp, colors.borderSubtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Insights,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
 
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        node.label.replaceFirstChar { it.uppercase() },
                        style = TextStyle(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            letterSpacing = (-0.3).sp
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(colors.accentSecondary, CircleShape)
                        )
                        Text(
                            "Knowledge Entity · Verified",
                            style = TextStyle(
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.2.sp
                            )
                        )
                    }
                }
            }
 
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.divider)
            )
            Spacer(Modifier.height(12.dp))
 
            if (relatedEdges.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    relatedEdges.forEach { edge ->
                        val formattedPredicate = when (edge.predicate.lowercase()) {
                            "is allergic to" -> "is allergic to"
                            "takes_medicine" -> "takes medicine"
                            "likes" -> "likes"
                            "dislikes" -> "dislikes"
                            "loves" -> "loves"
                            "has" -> "has"
                            "is" -> "is"
                            "works as" -> "works as"
                            "lives in" -> "lives in"
                            "fears" -> "fears"
                            else -> edge.predicate
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(colors.accent, CircleShape)
                            )
                            Text(
                                text = buildString {
                                    append(edge.subject.replaceFirstChar { it.uppercase() })
                                    append(" ")
                                    append(formattedPredicate)
                                    append(" ")
                                    append(edge.obj.replaceFirstChar { it.uppercase() })
                                },
                                style = TextStyle(
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Stored securely in your local neural repository. This entity is part of your personal knowledge graph.",
                    style = TextStyle(
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                )
            }
 
            Spacer(Modifier.height(14.dp))
 
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (relatedEdges.isNotEmpty()) {
                    NeuralMetaChip("Retrieved Fact", colors.accent)
                }
                NeuralMetaChip("On-Device", colors.accentSecondary)
                NeuralMetaChip("Encrypted", colors.accentSecondary)
            }
        }
    }
}

@Composable
private fun NeuralMetaChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.12f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                letterSpacing = 0.2.sp
            )
        )
    }
}

@Composable
fun TabSegmentedControl(
    selectedMode: GraphViewModel.VaultViewMode,
    onModeSelected: (GraphViewModel.VaultViewMode) -> Unit,
    colors: VaultThemeColors
) {
    val modes = GraphViewModel.VaultViewMode.values()
    val isDark = isSystemInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA), RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (mode in modes) {
            val isSelected = mode == selectedMode
            val bgTint by animateColorAsState(
                targetValue = if (isSelected) (if (isDark) Color(0xFF636366) else Color.White) else Color.Transparent,
                animationSpec = tween(250, easing = EaseInOutCubic),
                label = "bgTint"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) colors.textPrimary else colors.textSecondary,
                animationSpec = tween(250, easing = EaseInOutCubic),
                label = "textColor"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgTint)
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (mode) {
                        GraphViewModel.VaultViewMode.GRAPH -> "GraphRAG"
                        GraphViewModel.VaultViewMode.KNOWLEDGE -> "Knowledge"
                    },
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                )
            }
        }
    }
}

@Composable
fun KnowledgeListView(
    graphData: List<com.scypheon.app.data.models.RawGraphEdge>,
    colors: VaultThemeColors,
    modifier: Modifier = Modifier
) {
    var memoriesEnabled by remember { mutableStateOf(true) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Controls Row: Memories Switch and Add Memory Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Memories Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Memories",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                )
                Switch(
                    checked = memoriesEnabled,
                    onCheckedChange = { memoriesEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.surface,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.borderSubtle
                    )
                )
            }
            
            // Add Memory Button
            Button(
                onClick = { /* Action to add memory */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentSecondary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Memory",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Add Memory",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        if (!memoriesEnabled) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Local memories are temporarily disabled.",
                    style = TextStyle(
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        } else if (graphData.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                NeuralVaultEmptyState(colors = colors)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(graphData) { edge ->
                    MemoryCard(edge = edge, colors = colors)
                }
            }
        }
    }
}

@Composable
fun MemoryCard(
    edge: com.scypheon.app.data.models.RawGraphEdge,
    colors: VaultThemeColors
) {
    val formattedPredicate = when (edge.predicate.lowercase()) {
        "is allergic to" -> "is allergic to"
        "takes_medicine" -> "takes medicine"
        "likes" -> "likes"
        "dislikes" -> "dislikes"
        "loves" -> "loves"
        "has" -> "has"
        "is" -> "is"
        "works as" -> "works as"
        "lives in" -> "lives in"
        "fears" -> "fears"
        else -> edge.predicate
    }

    val sentence = buildString {
        append(edge.subject.replaceFirstChar { it.uppercase() })
        append(" ")
        append(formattedPredicate)
        append(" ")
        append(edge.obj.replaceFirstChar { it.uppercase() })
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.accent.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Column {
                    Text(
                        text = sentence,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            lineHeight = 20.sp
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeuralMetaChip(label = "Encrypted", color = colors.accentSecondary)
                        NeuralMetaChip(label = "On-Device", color = colors.accent)
                    }
                }
            }
            
            IconButton(
                onClick = { /* Edit or details menu */ },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ColorScaleBar(colors: VaultThemeColors, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(colors.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .border(0.5.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1450DC), // Blue
                            Color(0xFF1EC85A), // Green
                            Color(0xFFF0F032)  // Yellow
                        )
                    )
                )
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.width(180.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("2024.0", style = TextStyle(fontSize = 10.sp, color = colors.textSecondary))
            Text("2025.0", style = TextStyle(fontSize = 10.sp, color = colors.textSecondary))
            Text("2026.0", style = TextStyle(fontSize = 10.sp, color = colors.textSecondary))
        }
    }
}
