package com.scypheon.app.ui.screens

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scypheon.sdk.live.core.model.TranscriptEntry
import com.scypheon.sdk.live.core.model.LiveState
import kotlin.math.sin
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlinx.coroutines.launch
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items


// ═══════════════════════════════════════════════════════════════════
// [v4.1.0-SAR] SCYPHEON LIVE — DUAL THEME × AGSL FLUID SHADERS
// ═══════════════════════════════════════════════════════════════════

// Halo Holographic Gradients (Dark Mode)
private val HaloCyan = Color(0xFF00F0FF)              
private val HaloCortanaBlue = Color(0xFF0055FF)       
private val HaloPurple = Color(0xFF8B00FF)            
private val HaloMagenta = Color(0xFFFF00D6)           
private val HaloWhite = Color(0xFFFFFFFF)             
private val HaloAmber = Color(0xFFFF9D00)             
private val HaloGreen = Color(0xFF00FF88)             
private val HaloRed = Color(0xFFFF2442)               

private data class OrbPalette(
    val primary: Color,
    val secondary: Color,
    val highlight: Color,
    val glow: Color
)

data class LiveThemeColors(
    val bg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDim: Color,
    val pillBg: Color,
    val iconBg: Color,
    val iconTint: Color
)

@Composable
private fun getLiveThemeColors(isDark: Boolean): LiveThemeColors {
    return if (isDark) {
        LiveThemeColors(
            bg = Color.Transparent,
            textPrimary = Color.White,
            textSecondary = Color(0xFFE0E0E0),
            textDim = Color(0xFFA0A0A0),
            pillBg = Color(0xFF1C1C1E), // Solid dark grey for contrast
            iconBg = Color(0xFF2D0A0A),
            iconTint = Color(0xFFFF5252)
        )
    } else {
        LiveThemeColors(
            bg = Color.Transparent,
            textPrimary = Color(0xFF1F2937),
            textSecondary = Color(0xFF4B5563),
            textDim = Color(0xFF9CA3AF),
            pillBg = Color.White, // Solid white for contrast and shadow visibility
            iconBg = Color(0xFFFEE2E2),
            iconTint = Color(0xFFDC2626)
        )
    }
}

@Composable
private fun getOrbPalette(
    liveState: LiveState,
    activeSkillType: com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType,
    isDark: Boolean
): OrbPalette {
    if (liveState is LiveState.SafetyBlocked || liveState is LiveState.Interrupted || liveState is LiveState.Degraded) {
        return OrbPalette(HaloRed, HaloMagenta, HaloWhite, HaloRed)
    }

    return when (activeSkillType) {
        com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.STEM,
        com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.EDUCATION -> {
            OrbPalette(
                primary = Color(0xFFFF9D00),   // Gold
                secondary = Color(0xFFFFC107), // Amber Gold
                highlight = Color(0xFFFFF9C4), // Light Gold Highlight
                glow = Color(0xFFFFD54F)       // Glow Gold
            )
        }
        com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.MEDICAL -> {
            OrbPalette(
                primary = Color(0xFFFF1744),   // Crimson Red
                secondary = Color(0xFFB71C1C), // Deep Crimson
                highlight = Color(0xFFFFCDD2), // Soft pinkish red highlight
                glow = Color(0xFFFF5252)       // Neon red glow
            )
        }
        else -> {
            when (liveState) {
                is LiveState.Listening -> OrbPalette(HaloCortanaBlue, HaloPurple, HaloCyan, HaloCyan)
                is LiveState.UserSpeaking -> OrbPalette(HaloAmber, HaloMagenta, HaloWhite, HaloAmber)
                is LiveState.Thinking -> OrbPalette(HaloPurple, HaloMagenta, HaloCyan, HaloPurple)
                is LiveState.Speaking -> OrbPalette(HaloCyan, HaloCortanaBlue, HaloGreen, HaloCyan)
                else -> OrbPalette(Color(0xFF1558D6), Color(0xFF00F0FF), Color.White, Color(0xFF1558D6))
            }
        }
    }
}


private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f
    )
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LiveModeScreen(
    liveState: LiveState,
    audioLevel: Float,
    transcript: List<TranscriptEntry>,
    activeSkillType: com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType,
    canvasDsl: String?,
    onEndSession: () -> Unit,
    onOrbClick: () -> Unit,
    onCameraClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isCameraActive: Boolean,
    onCameraPreviewReady: (PreviewView?) -> Unit = {},
    onInterrupt: () -> Unit = {},
    onSkillTypeChange: (com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType) -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val colors = getLiveThemeColors(isDark)

    val infiniteTransition = rememberInfiniteTransition(label = "LiveAmbient")

    // Snap Interruption Animation
    val interruptionScale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds++
        }
    }

    var isTranscriptVisible by remember { mutableStateOf(false) }

    val isSplitScreen = canvasDsl != null && (
        activeSkillType == com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.STEM ||
        activeSkillType == com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.EDUCATION
    )

    val orbLayoutSize by animateDpAsState(
        targetValue = if (isSplitScreen) 180.dp else 450.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "OrbLayoutSize"
    )

    val orbScaleFactor by animateFloatAsState(
        targetValue = if (isSplitScreen) 0.4f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
        label = "OrbScaleFactor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        if (isCameraActive) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides Color.White
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    modifier = Modifier
                        .padding(top = 100.dp, end = 24.dp)
                        .size(140.dp, 200.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, colors.textPrimary.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
                    update = { previewView ->
                        onCameraPreviewReady(previewView)
                    }
                )
            }
            // Removed the black overlay to prevent the UI from looking like it forced a dark theme
            
            DisposableEffect(Unit) {
                onDispose {
                    onCameraPreviewReady(null)
                }
            }
        }

        LiveAmbientBackground(infiniteTransition, liveState, audioLevel, activeSkillType, isDark)

        // ─── Main Content Column ───
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LiveTopBar(
                elapsedSeconds = elapsedSeconds,
                activeSkillType = activeSkillType,
                onSkillTypeChange = onSkillTypeChange,
                colors = colors,
                isDark = isDark
            )

            AnimatedVisibility(
                visible = isSplitScreen,
                enter = fadeIn(tween(500)) + expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)),
                exit = fadeOut(tween(400)) + shrinkVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
            ) {
                if (canvasDsl != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(24.dp),
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.05f),
                                spotColor = Color.Black.copy(alpha = 0.15f)
                            ),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E1E1E).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                        )
                    ) {
                        VisualCanvas(dslString = canvasDsl)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(orbLayoutSize)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { 
                        // SNAP INTERRUPTION
                        coroutineScope.launch {
                            interruptionScale.snapTo(0.6f)
                            interruptionScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
                        }
                        onOrbClick() 
                    }
                    .scale(orbScaleFactor * interruptionScale.value),
                contentAlignment = Alignment.Center
            ) {
                LiveVoiceRipples(liveState, audioLevel, infiniteTransition, activeSkillType, isDark)
                LivePulsingOrb(liveState, audioLevel, infiniteTransition, activeSkillType, isDark)
            }

            Spacer(Modifier.weight(1f))
            LiveStateLabel(liveState, colors)
            Spacer(Modifier.height(8.dp))
            LiveBottomControlBar(
                colors = colors,
                isCameraActive = isCameraActive,
                onCameraClick = onCameraClick,
                onTranscriptClick = { isTranscriptVisible = true },
                onSettingsClick = onSettingsClick,
                onClose = { 
                    onInterrupt()
                    onEndSession()
                }
            )
            Spacer(Modifier.height(16.dp))
        }

        if (isTranscriptVisible) {
            TranscriptBottomSheet(
                transcript = transcript,
                onDismiss = { isTranscriptVisible = false }
            )
        }
    }
}

@Composable
private fun PolishedIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
    activeColor: Color = Color.Unspecified,
    colors: LiveThemeColors,
    isDark: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "PolishedBtnScale"
    )
    
    val targetBg = if (isActive) {
        activeColor.copy(alpha = 0.18f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
    }
    val animatedBg by animateColorAsState(targetValue = targetBg, animationSpec = tween(300), label = "PolishedBtnBg")
    
    val targetBorder = if (isActive) {
        activeColor.copy(alpha = 0.4f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    }
    val animatedBorder by animateColorAsState(targetValue = targetBorder, animationSpec = tween(300), label = "PolishedBtnBorder")

    val targetTint = if (isActive) {
        activeColor
    } else {
        colors.textPrimary.copy(alpha = 0.7f)
    }
    val animatedTint by animateColorAsState(targetValue = targetTint, animationSpec = tween(300), label = "PolishedBtnTint")

    Box(
        modifier = Modifier
            .scale(scale)
            .size(48.dp)
            .shadow(
                elevation = if (isActive) 6.dp else 0.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = activeColor.copy(alpha = 0.1f),
                spotColor = activeColor
            )
            .background(animatedBg, CircleShape)
            .border(1.dp, animatedBorder, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = animatedTint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun DraggableCloseButton(
    onClose: () -> Unit,
    colors: LiveThemeColors
) {
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    
    val scale by animateFloatAsState(
        targetValue = 1f + (kotlin.math.abs(dragOffset.value) / 400f).coerceIn(0f, 0.25f),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "CloseBtnScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationY = dragOffset.value
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragTriggered = false
                        
                        drag(down.id) { change ->
                            val dragAmount = change.positionChange().y
                            // Only allow dragging upwards (negative y)
                            val newOffset = (dragOffset.value + dragAmount).coerceIn(-250f, 0f)
                            coroutineScope.launch {
                                dragOffset.snapTo(newOffset)
                            }
                            change.consume()
                            dragTriggered = true
                        }
                        
                        if (dragTriggered) {
                            if (dragOffset.value < -120f) {
                                onClose()
                            } else {
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f)
                                    )
                                }
                            }
                        } else {
                            onClose()
                        }
                    }
                }
            }
            .size(54.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = colors.iconTint.copy(alpha = 0.15f),
                spotColor = colors.iconTint
            )
            .background(colors.iconBg, CircleShape)
            .border(1.dp, colors.iconTint.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Exit Live Mode",
            tint = colors.iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun LiveBottomControlBar(
    colors: LiveThemeColors,
    isCameraActive: Boolean,
    onCameraClick: () -> Unit,
    onTranscriptClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onClose: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val solidBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = solidBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.08f)),
            modifier = Modifier
                .height(72.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(36.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.24f)
                )
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PolishedIconButton(
                        icon = Icons.Default.CameraAlt,
                        contentDescription = "Camera Vision",
                        onClick = onCameraClick,
                        isActive = isCameraActive,
                        activeColor = colors.iconTint,
                        colors = colors,
                        isDark = isDark
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    PolishedIconButton(
                        icon = Icons.Default.History,
                        contentDescription = "Session Transcript",
                        onClick = onTranscriptClick,
                        isActive = false,
                        colors = colors,
                        isDark = isDark
                    )
                }
                Box(
                    modifier = Modifier.wrapContentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    DraggableCloseButton(
                        onClose = onClose,
                        colors = colors
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PolishedIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = "Settings",
                        onClick = onSettingsClick,
                        isActive = false,
                        colors = colors,
                        isDark = isDark
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTopBar(
    elapsedSeconds: Int,
    activeSkillType: com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType,
    onSkillTypeChange: (com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType) -> Unit,
    colors: LiveThemeColors,
    isDark: Boolean
) {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeString = "%d:%02d".format(minutes, seconds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Polished Time Capsule with drop shadow
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.pillBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.textPrimary.copy(alpha = 0.08f)),
            modifier = Modifier.shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = timeString,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Default,
                        letterSpacing = 0.5.sp,
                        color = colors.textPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Skills Row on the right
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val skills = listOf(
                Triple(com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.GENERAL, "General", "✨"),
                Triple(com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.MEDICAL, "Medical", "⚕️"),
                Triple(com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.STEM, "STEM", "🔬")
            )

            skills.forEach { (type, label, emoji) ->
                val isActive = activeSkillType == type
                
                val activeBgColor = when (type) {
                    com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.MEDICAL -> Color(0xFFFF1744)
                    com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.STEM -> Color(0xFFFF9D00)
                    else -> Color(0xFF0055FF)
                }

                val bgValue = if (isActive) activeBgColor.copy(alpha = 0.15f) else {
                    if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
                }
                val borderValue = if (isActive) activeBgColor.copy(alpha = 0.5f) else {
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                }
                val textValue = if (isActive) activeBgColor else colors.textSecondary

                val animatedBg by animateColorAsState(targetValue = bgValue, animationSpec = tween(300), label = "PillBg")
                val animatedBorder by animateColorAsState(targetValue = borderValue, animationSpec = tween(300), label = "PillBorder")
                val animatedText by animateColorAsState(targetValue = textValue, animationSpec = tween(300), label = "PillText")

                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.05f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
                    label = "PillScale"
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = animatedBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, animatedBorder),
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .scale(scale)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSkillTypeChange(type)
                        }
                        .shadow(
                            elevation = if (isActive) 4.dp else 0.dp,
                            shape = RoundedCornerShape(20.dp),
                            clip = false,
                            ambientColor = activeBgColor.copy(alpha = 0.1f),
                            spotColor = activeBgColor
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$emoji $label",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = animatedText
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveVoiceRipples(
    liveState: LiveState,
    audioLevel: Float,
    infiniteTransition: InfiniteTransition,
    activeSkillType: com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType,
    isDark: Boolean
) {
    if (liveState == LiveState.Idle) return

    val audioScale by animateFloatAsState(targetValue = audioLevel.coerceIn(0f, 1f), animationSpec = spring(dampingRatio = 0.4f, stiffness = 120f), label = "RippleScale")
    val phase by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "RipplePhase")

    val palette = getOrbPalette(liveState, activeSkillType, isDark)
    val animatedColor by animateColorAsState(targetValue = palette.glow, animationSpec = tween(600), label = "RippleColor")

    Box(contentAlignment = Alignment.Center) {
        for (i in 0..2) {
            val progress = (phase + i / 3f) % 1f
            val scale = 1.0f + progress * (1.3f + audioScale * 0.9f)
            val alpha = (1f - progress) * (0.12f + audioScale * 0.22f)
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                    .border((1.5.dp / scale).coerceAtLeast(0.5.dp), animatedColor, CircleShape)
            )
        }
    }
}

private fun getWaveValue(
    angle: Float,
    activeSkillType: com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType
): Float {
    val isStemOrEd = activeSkillType == com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.STEM ||
            activeSkillType == com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType.EDUCATION
    return if (isStemOrEd) {
        val x = angle / (2f * Math.PI.toFloat())
        2f * kotlin.math.abs(2f * (x - kotlin.math.floor(x + 0.5f).toFloat())) - 1f
    } else {
        kotlin.math.sin(angle)
    }
}

@Composable
private fun LivePulsingOrb(
    liveState: LiveState,
    audioLevel: Float,
    infiniteTransition: InfiniteTransition,
    activeSkillType: com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType,
    isDark: Boolean
) {
    val audioScale by animateFloatAsState(targetValue = audioLevel.coerceIn(0f, 1f), animationSpec = spring(dampingRatio = 0.35f, stiffness = 150f), label = "OrbScale")
    
    val targetSpeedMultiplier = when (liveState) {
        is LiveState.Idle -> 0.2f
        is LiveState.Listening -> 0.3f
        is LiveState.UserSpeaking -> 0.4f
        is LiveState.Thinking -> 0.6f
        is LiveState.Speaking -> 0.5f
        is LiveState.SafetyBlocked, is LiveState.Interrupted, is LiveState.Degraded -> 0.1f
    }
    
    val speedMultiplier by animateFloatAsState(targetValue = targetSpeedMultiplier, animationSpec = tween(1000), label = "Speed")
    val uTime by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f, 
        animationSpec = infiniteRepeatable(tween(1000000, easing = LinearEasing)), label = "Time"
    )

    val palette = getOrbPalette(liveState, activeSkillType, isDark)
    val p1 by animateColorAsState(palette.primary, tween(600), label = "")
    val p2 by animateColorAsState(palette.secondary, tween(600), label = "")
    val hl by animateColorAsState(palette.highlight, tween(600), label = "")
    val glow by animateColorAsState(palette.glow, tween(600), label = "")

    // Pre-allocated paths to prevent runtime object allocations during draw phase (ensures butter-smooth, stutter-free animations)
    val outerWavePath = remember { androidx.compose.ui.graphics.Path() }
    val innerWavePath = remember { androidx.compose.ui.graphics.Path() }
    val fillPath = remember { androidx.compose.ui.graphics.Path() }
    val echoWavePath = remember { androidx.compose.ui.graphics.Path() }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(450.dp)) {
        // Outer glowing aura (Replaced Modifier.blur with Canvas radial gradient to fix hardware rendering squares)
        Canvas(modifier = Modifier.size(450.dp)) {
            val sphereRadius = 92.dp.toPx() * (1f + audioScale * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.22f + audioScale * 0.25f), Color.Transparent),
                    radius = sphereRadius * 2.2f
                )
            )
        }

        // AMOEBA & ORB RENDERING
        Canvas(modifier = Modifier.size(450.dp)) {
            val time = uTime * speedMultiplier * 2f
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            
            // Base sphere radius enlarged from 96.dp to 102.dp to make it look substantial
            val sphereRadius = 102.dp.toPx() * (1f + audioScale * 0.08f)
            
            // Build the outer amoeba path - widened to flare out smoothly with sound
            outerWavePath.reset()
            val waveBaseRadius = sphereRadius * (1.20f + audioScale * 0.10f)
            val deform = 0.10f + audioScale * 0.08f // Subtle fluid deformation
            val numPoints = 180
            
            // Slow global rotation to the outer wave
            val rotationSpeed = time * 0.03f
            
            for (i in 0..numPoints) {
                val angle = (i.toFloat() / numPoints) * 2f * Math.PI.toFloat()
                val wave1 = getWaveValue(angle * 3f + time, activeSkillType) * deform
                val wave2 = getWaveValue(angle * 4f - time * 0.8f, activeSkillType) * (deform * 0.7f)
                val wave3 = getWaveValue(angle * 5f + time * 1.2f, activeSkillType) * (deform * 0.4f)
                
                // Low-frequency fluid speech ripple
                val speechRipple = if (audioScale > 0.02f) {
                    getWaveValue(angle * 6f + time * 3f, activeSkillType) * (audioScale * 0.03f)
                } else 0f
                
                val radius = waveBaseRadius * (1f + wave1 + wave2 + wave3 + speechRipple)
                val x = center.x + radius * kotlin.math.cos(angle + rotationSpeed)
                val y = center.y + radius * kotlin.math.sin(angle + rotationSpeed)
                
                if (i == 0) outerWavePath.moveTo(x, y) else outerWavePath.lineTo(x, y)
            }
            outerWavePath.close()

            // Build the inner amoeba path with slow opposite rotation and phase-shifted time
            // Compresses inward dynamically with sound to thicken the fluid band
            // Enlarged slightly to 0.45f for a larger hollow
            innerWavePath.reset()
            val innerBaseRadius = sphereRadius * (0.45f - audioScale * 0.02f)
            val innerTime = -time * 0.7f
            val innerRotationSpeed = -time * 0.015f
            
            for (i in 0..numPoints) {
                val angle = (i.toFloat() / numPoints) * 2f * Math.PI.toFloat()
                val wave1 = getWaveValue(angle * 3f + innerTime, activeSkillType) * deform
                val wave2 = getWaveValue(angle * 4f - innerTime * 0.8f, activeSkillType) * (deform * 0.7f)
                val wave3 = getWaveValue(angle * 5f + innerTime * 1.2f, activeSkillType) * (deform * 0.4f)
                
                // Fluid inner ripple
                val innerSpeechRipple = if (audioScale > 0.02f) {
                    getWaveValue(angle * 5f - time * 3f, activeSkillType) * (audioScale * 0.02f)
                } else 0f
                
                val radius = innerBaseRadius * (1f + wave1 + wave2 + wave3 + innerSpeechRipple)
                val x = center.x + radius * kotlin.math.cos(angle + innerRotationSpeed)
                val y = center.y + radius * kotlin.math.sin(angle + innerRotationSpeed)
                
                if (i == 0) innerWavePath.moveTo(x, y) else innerWavePath.lineTo(x, y)
            }
            innerWavePath.close()

            // Build the secondary echo wave path (further out, thinner outline, offset phases)
            // This adds multi-layered depth (Apple style glass-glow effect)
            echoWavePath.reset()
            val echoBaseRadius = sphereRadius * (1.35f + audioScale * 0.15f)
            val echoDeform = 0.06f + audioScale * 0.05f
            val echoRotationSpeed = -time * 0.02f
            
            for (i in 0..numPoints) {
                val angle = (i.toFloat() / numPoints) * 2f * Math.PI.toFloat()
                val wave1 = getWaveValue(angle * 2.5f + time * 0.7f, activeSkillType) * echoDeform
                val wave2 = getWaveValue(angle * 3.5f - time * 0.5f, activeSkillType) * (echoDeform * 0.6f)
                
                val radius = echoBaseRadius * (1f + wave1 + wave2)
                val x = center.x + radius * kotlin.math.cos(angle + echoRotationSpeed)
                val y = center.y + radius * kotlin.math.sin(angle + echoRotationSpeed)
                
                if (i == 0) echoWavePath.moveTo(x, y) else echoWavePath.lineTo(x, y)
            }
            echoWavePath.close()

            // Build hollowed out fill path using EvenOdd fill rule for the main fluid band
            fillPath.reset()
            fillPath.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            fillPath.addPath(outerWavePath)
            fillPath.addPath(innerWavePath)

            // Dynamic color shift blending: lerps gradient colors towards glowing neon colors during active user input
            val speechIntensity = if (liveState is LiveState.Listening || liveState is LiveState.UserSpeaking) {
                (audioScale * 1.5f).coerceIn(0f, 1f)
            } else {
                0f
            }
            
            // Transition to an electric cyber-gradient when speaking: Neon Gold/Amber -> Violet/Magenta -> Turquoise/Cyan
            val activeP1 = Color(0xFFFF9D00) // Neon Golden Amber
            val activeP2 = Color(0xFFFF00D6) // Hot Neon Magenta
            val activeHl = Color(0xFF00F0FF) // Electric Turquoise/Cyan
            
            val finalP1 = lerpColor(p1, activeP1, speechIntensity)
            val finalP2 = lerpColor(p2, activeP2, speechIntensity)
            val finalHl = lerpColor(hl, activeHl, speechIntensity)

            // Swirling effect: linear gradient start/end offsets shift dynamically with speech intensity
            val gradientStart = androidx.compose.ui.geometry.Offset(
                x = size.width * (0.1f + audioScale * 0.15f),
                y = size.height * (0.1f - audioScale * 0.05f)
            )
            val gradientEnd = androidx.compose.ui.geometry.Offset(
                x = size.width * (0.9f - audioScale * 0.15f),
                y = size.height * (0.9f + audioScale * 0.05f)
            )

            // 1. Central Hollow (Orb) - Solid white with blur and 0.85 alpha as requested
            drawContext.canvas.let { canvas ->
                val paint = androidx.compose.ui.graphics.Paint()
                paint.color = Color.White.copy(alpha = 0.85f)
                paint.asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(40f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                canvas.drawPath(innerWavePath, paint)
            }

            // 3. The Soundwave/Amoeba Fill - Placed in the foreground, hollowed out in the middle
            drawPath(
                path = fillPath,
                brush = Brush.linearGradient(
                    colors = listOf(finalHl.copy(alpha = 0.85f), finalP1.copy(alpha = 0.5f), finalP2.copy(alpha = 0.8f)),
                    start = gradientStart,
                    end = gradientEnd
                )
            )

            // 4. The Soundwave/Amoeba Edge (Stroke)
            drawPath(
                path = outerWavePath,
                brush = Brush.linearGradient(
                    colors = listOf(finalHl, finalP2.copy(alpha = 0.7f)),
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.2f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.8f)
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5.dp.toPx()) // Slightly thicker stroke for crisp definition
            )

            // 5. Thin Echo Wave Outline (Provides premium visual richness and holographic depth)
            drawPath(
                path = echoWavePath,
                brush = Brush.linearGradient(
                    colors = listOf(finalHl.copy(alpha = 0.35f), finalP2.copy(alpha = 0.12f)),
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.2f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.8f)
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.25.dp.toPx())
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun LiveStateLabel(liveState: LiveState, colors: LiveThemeColors) {
    val (text, subtext) = (when (liveState) {
        is LiveState.Listening -> "Ready" to "Tap to speak"
        is LiveState.UserSpeaking -> {
            val partial = liveState.transcript
            if (partial.isBlank()) "Listening..." to null
            else partial to "Tap to send"
        }
        is LiveState.Thinking -> "Analyzing Core..." to null
        is LiveState.Speaking -> "Speaking..." to "Tap to interrupt"
        is LiveState.SafetyBlocked -> "Connection Lost" to null
        else -> "Standby" to null
    }) as Pair<String, String?>

    val primaryColor by animateColorAsState(
        targetValue = when (liveState) {
            is LiveState.Listening -> colors.textSecondary
            is LiveState.UserSpeaking -> colors.textPrimary
            is LiveState.Thinking -> colors.textPrimary
            is LiveState.Speaking -> colors.textPrimary
            is LiveState.SafetyBlocked, is LiveState.Interrupted, is LiveState.Degraded -> colors.iconTint
            else -> colors.textDim
        }, tween(500), label = ""
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
        AnimatedContent(
            targetState = text,
            transitionSpec = { (fadeIn(tween(400)) + slideInVertically { it / 4 }) togetherWith (fadeOut(tween(300)) + slideOutVertically { -it / 4 }) },
            label = ""
        ) { label ->
            Text(label, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = primaryColor, textAlign = TextAlign.Center), maxLines = 3)
        }
        AnimatedVisibility(visible = subtext != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            subtext?.let { Text(it, style = TextStyle(fontSize = 12.sp, color = colors.textDim)) }
        }
    }
}

@Composable
private fun LiveAmbientBackground(
    infiniteTransition: InfiniteTransition,
    liveState: LiveState,
    audioLevel: Float,
    activeSkillType: com.scypheon.sdk.core.agent.skills.AgentSkillRegistry.SkillType,
    isDark: Boolean
) {
    val drift by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "")
    val color = when (liveState) {
        is LiveState.Idle, is LiveState.Listening -> Color.Transparent
        else -> getOrbPalette(liveState, activeSkillType, isDark).glow
    }
    val ambientColor by animateColorAsState(color, tween(1000), label = "")

    Canvas(
        modifier = Modifier
            .size(380.dp)
            .offset(x = (-80 + drift * 30).dp, y = (180 + drift * 60).dp)
    ) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ambientColor.copy(alpha = 0.15f + audioLevel * 0.05f), Color.Transparent),
                radius = size.width / 2f
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscriptBottomSheet(
    transcript: List<TranscriptEntry>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { SheetDragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Session Transcript",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Limit to last 50 entries to prevent OOM
            val limitedTranscript = transcript.takeLast(50)
            
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                reverseLayout = true, // Show newest at bottom
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(limitedTranscript.reversed(), key = { it.timestamp }) { entry ->
                    TranscriptBubble(entry)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetDragHandle() {
    BottomSheetDefaults.DragHandle()
}

@Composable
private fun TranscriptBubble(entry: TranscriptEntry) {
    val isUser = entry.isUser
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isUser) 
                MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

