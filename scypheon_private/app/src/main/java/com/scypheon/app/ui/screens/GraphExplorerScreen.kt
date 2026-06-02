package com.scypheon.app.ui.screens

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.scypheon.app.ui.views.NeuralGraphView

// ═══════════════════════════════════════════════════════════════════
// [v1.5.0-SAR] NEURAL VAULT — Premium Knowledge Graph Explorer
// Apple-inspired light glassmorphic design with micro-animations
// ═══════════════════════════════════════════════════════════════════

// Design Tokens — Light Premium
private val VaultBg = Color(0xFFF8F9FC)
private val VaultBgDeep = Color(0xFFEFF2F9)
private val VaultAccent = Color(0xFF3478F6) // iOS blue
private val VaultAccentPurple = Color(0xFF7C3AED)
private val VaultAccentCyan = Color(0xFF06B6D4)
private val VaultAccentGreen = Color(0xFF34C759)
private val VaultSurface = Color(0xFFFFFFFF)
private val VaultTextPrimary = Color(0xFF1D1D1F)
private val VaultTextSecondary = Color(0xFF86868B)
private val VaultTextTertiary = Color(0xFFC7C7CC)
private val VaultBorderSubtle = Color(0xFFE5E5EA)
private val VaultDivider = Color(0xFFF2F2F7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphExplorerScreen(
    viewModel: GraphViewModel,
    graphData: List<com.scypheon.app.data.models.RawGraphEdge>,
    onBack: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    
    // Viewport State
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val layoutState by viewModel.physics.layout.collectAsState()

    LaunchedEffect(graphData) {
        viewModel.initGraph(graphData)
    }

    // [v1.5.3-SAR] Auto-center viewport on graph nodes after physics stabilizes.
    // Without this, nodes render off-screen at the physics origin (500, 500).
    var hasAutoCentered by remember { mutableStateOf(false) }
    LaunchedEffect(graphData) {
        if (graphData.isNotEmpty()) {
            kotlinx.coroutines.delay(300) // Wait for physics to compute initial positions
            hasAutoCentered = false
        }
    }

    // Ambient animation
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
                    colors = listOf(VaultBg, VaultBgDeep, VaultBg)
                )
            )
    ) {
        // Subtle ambient glow orbs (very faint on white)
        Box(
            modifier = Modifier
                .size(350.dp)
                .offset(x = (-100).dp, y = (150 + ambientShift * 30).dp)
                .blur(150.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            VaultAccent.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = (-100 + ambientShift * 20).dp)
                .blur(130.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            VaultAccentPurple.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Clean frosted top bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = VaultSurface.copy(alpha = 0.92f),
                    shadowElevation = 0.5.dp
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
                                    tint = VaultAccent,
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
                                        color = VaultTextPrimary,
                                        letterSpacing = (-0.3).sp
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    // Live indicator dot
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
                                                if (graphData.isNotEmpty()) VaultAccentGreen
                                                else VaultTextTertiary,
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
                                            color = VaultTextSecondary,
                                            letterSpacing = 0.sp
                                        )
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))
                            Spacer(Modifier.width(48.dp))
                        }
                        // Subtle bottom border
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(VaultBorderSubtle)
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.2f, 5f)
                            offset += pan
                        }
                    }
            ) {
                if (graphData.isEmpty()) {
                    NeuralVaultEmptyState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            NeuralGraphView(ctx).apply {
                                setLifecycle(lifecycleOwner.lifecycle)
                                setOnNodeClickListener { node ->
                                    selectedNode = node
                                }
                                bindPhysics(viewModel.physics.layout, scope)
                            }
                        },
                        update = { view ->
                            // [v1.5.3-SAR] Auto-center viewport on first valid layout.
                            // The physics engine positions nodes around (500, 500) but the
                            // viewport matrix starts at origin (0, 0), rendering nodes off-screen.
                            if (!hasAutoCentered && view.width > 0 && view.height > 0) {
                                val layout = layoutState
                                if (layout.nodes.isNotEmpty()) {
                                    val cx = layout.nodes.map { it.posX }.average().toFloat()
                                    val cy = layout.nodes.map { it.posY }.average().toFloat()
                                    // Offset = screen center - node centroid
                                    offset = androidx.compose.ui.geometry.Offset(
                                        view.width / 2f - cx,
                                        view.height / 2f - cy
                                    )
                                    hasAutoCentered = true
                                }
                            }
                            view.updateTransform(scale, offset.x, offset.y)
                        },
                        onRelease = { view ->
                            view.unbindPhysics()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // ─── Premium Info Card ───
                AnimatedVisibility(
                    visible = selectedNode != null,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 2 },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(300)) { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    selectedNode?.let { node ->
                        NeuralInfoCard(node = node, onDismiss = { selectedNode = null })
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Empty State — Clean, informative, Apple-like
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun NeuralVaultEmptyState(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "EmptyAnim")

    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RingPulse"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RingAlpha"
    )

    Column(
        modifier = modifier.padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Brain icon with pulsing ring
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .graphicsLayer {
                        scaleX = ringScale
                        scaleY = ringScale
                        alpha = ringAlpha
                    }
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(VaultAccent, VaultAccentPurple, VaultAccentCyan)
                        ),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                VaultAccent.copy(alpha = 0.08f),
                                VaultAccentPurple.copy(alpha = 0.05f)
                            )
                        ),
                        CircleShape
                    )
                    .border(0.5.dp, VaultBorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = VaultAccent,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "Neural Vault",
            style = TextStyle(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = VaultTextPrimary,
                letterSpacing = (-0.5).sp
            )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Your personal knowledge graph lives here.\nConverse with the AI to build connections.",
            style = TextStyle(
                fontSize = 15.sp,
                color = VaultTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        )

        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuralFeaturePill(Icons.Default.Hub, "Entities", VaultAccent)
            NeuralFeaturePill(Icons.Default.AutoGraph, "Relations", VaultAccentPurple)
            NeuralFeaturePill(Icons.Default.Shield, "Local Only", VaultAccentGreen)
        }
    }
}

@Composable
private fun NeuralFeaturePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Text(
                label,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    letterSpacing = 0.2.sp
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Info Card — Apple-style bottom sheet card
// ═══════════════════════════════════════════════════════════════════

@Composable
fun NeuralInfoCard(node: GraphNode, onDismiss: () -> Unit = {}) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = VaultSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 12.dp,
        onClick = onDismiss
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VaultTextTertiary)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    VaultAccent.copy(alpha = 0.1f),
                                    VaultAccentPurple.copy(alpha = 0.06f)
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .border(0.5.dp, VaultBorderSubtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Insights,
                        contentDescription = null,
                        tint = VaultAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        node.label.replaceFirstChar { it.uppercase() },
                        style = TextStyle(
                            fontWeight = FontWeight.SemiBold,
                            color = VaultTextPrimary,
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
                                .background(VaultAccentGreen, CircleShape)
                        )
                        Text(
                            "Knowledge Entity · Verified",
                            style = TextStyle(
                                color = VaultAccentGreen,
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
                    .background(VaultDivider)
            )
            Spacer(Modifier.height(12.dp))

            Text(
                "Stored securely in your local neural repository. " +
                "This entity is part of your personal knowledge graph and never leaves your device.",
                style = TextStyle(
                    color = VaultTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuralMetaChip("On-Device", VaultAccent)
                NeuralMetaChip("Encrypted", VaultAccentPurple)
                NeuralMetaChip("Zero-Cloud", VaultAccentCyan)
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
