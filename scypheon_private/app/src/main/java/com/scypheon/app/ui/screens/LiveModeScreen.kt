package com.scypheon.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scypheon.sdk.core.live.LiveSessionOrchestrator
import com.scypheon.sdk.core.live.LiveSessionOrchestrator.LiveState

// ═══════════════════════════════════════════════════════════════════
// [v1.5.4-SAR] SCYPHEON LIVE — Immersive Deep Obsidian Dark Theme
// Premium dark mode, Manual Push-to-Talk Orb flow with top close button
// ═══════════════════════════════════════════════════════════════════

// Design Tokens (Aesthetic Obsidian Dark Theme)
private val LiveBg = Color(0xFF090A0F)
private val LiveSurface = Color(0xFF131520)
private val LiveBorderColor = Color(0x2BFFFFFF)
private val LiveAccentBlue = Color(0xFF00E5FF)  // Premium neon cyan/blue
private val LiveAccentPurple = Color(0xFFC084FC) // Soft bright violet
private val LiveAccentCyan = Color(0xFF38BDF8)
private val LiveAccentGreen = Color(0xFF34D399) // Mint green
private val LiveAccentRed = Color(0xFFFB7185)   // Salmon red
private val LiveTextPrimary = Color(0xFFF1F5F9)  // Off white
private val LiveTextSecondary = Color(0xFF94A3B8)// Slate secondary
private val LiveTextDim = Color(0xFF64748B)      // Muted slate

private data class QuadColors(
    val c1: Color,
    val c2: Color,
    val c3: Color,
    val glow: Color
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LiveModeScreen(
    liveState: LiveState,
    audioLevel: Float,
    transcript: List<LiveSessionOrchestrator.TranscriptEntry>,
    onEndSession: () -> Unit,
    onOrbClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiveAmbient")

    // State for Developer Preview Info Dialog
    var showInfoDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiveBg)
    ) {
        // ─── Ambient Pastel Background Glows ───
        LiveAmbientBackground(infiniteTransition, liveState, audioLevel)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Top Bar with Interactive Preview, Info Badge & Frosted Close Button ───
            LiveTopBar(
                onInfoClick = { showInfoDialog = true },
                onEndSession = onEndSession
            )

            Spacer(Modifier.weight(0.7f))

            // ─── Central Fluid Sphere (Manual Orb Trigger) ───
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onOrbClick() }
                    .border(1.5.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                LivePulsingOrb(
                    liveState = liveState,
                    audioLevel = audioLevel,
                    infiniteTransition = infiniteTransition
                )
            }

            Spacer(Modifier.height(36.dp))

            // ─── Live State Indicator Subtitle ───
            LiveStateLabel(liveState)

            Spacer(Modifier.height(16.dp))

            // ─── Live Transcript ───
            LiveTranscript(
                transcript = transcript,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(16.dp))
        }

        // ─── Beautiful Glassmorphic Developer Preview Dialog ───
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = LiveAccentPurple,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Live Voice Preview",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = LiveTextPrimary
                            )
                        )
                    }
                },
                text = {
                    Text(
                        text = "Scypheon Live uses cutting-edge, offline-native speech recognition and local LLM inference engines to enable real-time voice interactions.\n\nSince this pipeline runs entirely on-device to guarantee offline security in disaster zones, it is computationally intensive and may experience instability, latency, or voice engine initialization lag on some hardware configurations.",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = LiveTextSecondary,
                            lineHeight = 20.sp
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showInfoDialog = false },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LiveAccentBlue)
                    ) {
                        Text(
                            "Got it",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = LiveSurface.copy(alpha = 0.95f),
                modifier = Modifier
                    .padding(16.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Top Bar — Minimal status & interactive sandbox toggle
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LiveTopBar(
    onInfoClick: () -> Unit,
    onEndSession: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Side: Pulsing Live dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "LiveDot")
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "DotPulse"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .background(LiveAccentGreen, CircleShape)
            )
            Text(
                "Scypheon Live",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiveTextPrimary,
                    letterSpacing = 0.5.sp
                )
            )
        }

        // Right Side: Beautiful Preview Badge with Info Icon Button & Close Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "Preview" badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x2BFFFFFF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3DFFFFFF)),
                modifier = Modifier.height(26.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Preview",
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = LiveTextSecondary,
                            letterSpacing = 0.3.sp
                        )
                    )
                }
            }

            // Info icon button next to it
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Preview Info",
                    tint = LiveAccentBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Modern Frosted Close Icon Button
            Surface(
                onClick = onEndSession,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Live Mode",
                        tint = LiveTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Dynamic Organic Morphing Watercolor Sphere — Pure watercolor visual art
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LivePulsingOrb(
    liveState: LiveState,
    audioLevel: Float,
    infiniteTransition: InfiniteTransition
) {
    // ─── Continuous Smooth Angles for Liquid Morphing ───
    val angle1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(14000, easing = LinearEasing)),
        label = "Angle1"
    )
    val angle2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(19000, easing = LinearEasing)),
        label = "Angle2"
    )
    val angle3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(25000, easing = LinearEasing)),
        label = "Angle3"
    )

    // Breathing scales for natural organic shape changes
    val breathe1 by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Breathe1"
    )
    val breathe2 by infiniteTransition.animateFloat(
        initialValue = 1.06f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Breathe2"
    )

    // Spring-based reactive audio scale for immersive liquid feedback
    val dynamicAudioScale by animateFloatAsState(
        targetValue = audioLevel * 0.45f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = 160f),
        label = "DynamicAudio"
    )

    // Custom aesthetic HSL quad-color schemes for each state
    val (color1, color2, color3, glowColor) = when (liveState) {
        is LiveState.Listening -> QuadColors(
            Color(0xFF007AFF), // Deep Cobalt Blue
            Color(0xFF00C7FF), // Cyan Cloud
            Color(0xFFE3F2FD), // Bright Ice Highlight
            Color(0xFF58A6FF)  // Radial Background Glow
        )
        is LiveState.UserSpeaking -> QuadColors(
            Color(0xFF7C3AED), // Indigo Purple
            Color(0xFF00C7FF), // Bright Cyan
            Color(0xFFFFFFFF), // Brilliant White
            Color(0xFF7C3AED)  // Violet Neon Glow
        )
        is LiveState.Processing -> QuadColors(
            Color(0xFF7C3AED), // Royal Purple
            Color(0xFFEC4899), // Deep Magenta
            Color(0xFFFEF08A), // Soft Golden Highlight
            Color(0xFFD8B4FE)  // Lavender Soft Glow
        )
        is LiveState.AiSpeaking -> QuadColors(
            Color(0xFF10B981), // Emerald Green
            Color(0xFF06B6D4), // Sky Blue-Green
            Color(0xFFECFDF5), // Mint White Highlight
            Color(0xFF34D399)  // Lime-Emerald Glow
        )
        is LiveState.Error -> QuadColors(
            Color(0xFFEF4444), // Coral Red
            Color(0xFFF59E0B), // Ember Orange
            Color(0xFFFEF2F2), // Pale Rose Highlight
            Color(0xFFFCA5A5)  // Crimson Background Glow
        )
        else -> QuadColors(
            Color(0xFF4B5563),
            Color(0xFF374151),
            Color(0xFFF3F4F6),
            Color(0xFF9CA3AF)
        )
    }

    Box(
        modifier = Modifier
            .size(240.dp)
            .graphicsLayer {
                val totalScale = 1.0f + dynamicAudioScale * 0.22f
                scaleX = totalScale
                scaleY = totalScale
            },
        contentAlignment = Alignment.Center
    ) {
        // ─── Ambient Glow Halo (Behind the Sphere) ───
        Box(
            modifier = Modifier
                .size(220.dp)
                .blur(42.dp)
                .alpha(0.6f + dynamicAudioScale * 0.35f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.45f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        // ─── Organic Morphing Watercolor Sphere ───
        Box(
            modifier = Modifier
                .size(165.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.15f), CircleShape)
        ) {
            // Layer 1: Base Organic Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = angle1
                        scaleX = breathe1 * 1.12f
                        scaleY = breathe2 * 1.12f
                    }
                    .background(
                        Brush.linearGradient(
                            colors = listOf(color1, color2)
                        )
                    )
            )

            // Layer 2: Swirling Secondary Core (Double Blend Mode)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = angle2
                        scaleX = breathe2 * 1.25f
                        scaleY = breathe1 * 0.85f
                        translationX = 14f * breathe1
                        translationY = -14f * breathe2
                        alpha = 0.85f
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(color2.copy(alpha = 0.92f), Color.Transparent),
                            radius = 260f
                        )
                    )
            )

            // Layer 3: High-contrast Fluid Highlight Cloud (Highly Reactive to Voice)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = angle3
                        scaleX = 0.85f + dynamicAudioScale * 0.6f
                        scaleY = 0.85f + dynamicAudioScale * 0.6f
                        translationX = -12f * breathe2
                        translationY = 12f * breathe1
                        alpha = 0.8f
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(color3.copy(alpha = 0.9f), Color.Transparent),
                            radius = 190f
                        )
                    )
            )

            // Layer 4: Premium Glass-like Inner Shading Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.16f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.22f)
                            )
                        )
                    )
            )
        }

        // ─── Outer Transparent Glass Ring (Adds gloss and precision) ───
        Box(
            modifier = Modifier
                .size(167.dp)
                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// State Label
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun LiveStateLabel(liveState: LiveState) {
    val text = when (liveState) {
        is LiveState.Listening -> "Tap Orb to Speak"
        is LiveState.UserSpeaking -> if (liveState.partialText.isBlank()) "Listening..." else liveState.partialText
        is LiveState.Processing -> "Thinking..."
        is LiveState.AiSpeaking -> liveState.responseText
        is LiveState.Error -> "Neural core lag... retrying"
        else -> "Standby"
    }

    val color = when (liveState) {
        is LiveState.Listening -> LiveTextSecondary
        is LiveState.UserSpeaking -> LiveTextPrimary
        is LiveState.Processing -> LiveAccentPurple
        is LiveState.AiSpeaking -> LiveTextPrimary
        else -> LiveTextDim
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                fadeIn(tween(350)) + slideInVertically { it / 3 } togetherWith
                    fadeOut(tween(250)) + slideOutVertically { -it / 3 }
            },
            label = "StateLabel"
        ) { label ->
            Text(
                label,
                style = TextStyle(
                    fontSize = if (liveState is LiveState.UserSpeaking) 18.sp else 16.sp,
                    fontWeight = if (liveState is LiveState.UserSpeaking) FontWeight.Medium else FontWeight.Normal,
                    color = color,
                    textAlign = TextAlign.Center,
                    letterSpacing = if (liveState is LiveState.UserSpeaking) (-0.2).sp else 0.4.sp
                ),
                maxLines = 3,
                modifier = Modifier.padding(horizontal = 36.dp)
            )
        }

        if (liveState is LiveState.UserSpeaking) {
            Text(
                "Tap Orb to Send",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light,
                    color = LiveTextDim,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.alpha(0.8f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Live Transcript — Scrolling conversation log
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LiveTranscript(
    transcript: List<LiveSessionOrchestrator.TranscriptEntry>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to latest entry
    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) {
            listState.animateScrollToItem(transcript.lastIndex)
        }
    }

    if (transcript.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "Transcript log will stream here",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = LiveTextDim,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.2.sp
                )
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(transcript) { entry ->
                LiveTranscriptBubble(entry)
            }
        }
    }
}

@Composable
private fun LiveTranscriptBubble(entry: LiveSessionOrchestrator.TranscriptEntry) {
    val alignment = if (entry.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (entry.isUser) LiveAccentBlue.copy(alpha = 0.08f) else LiveSurface
    val borderStroke = if (entry.isUser) {
        androidx.compose.foundation.BorderStroke(1.dp, LiveAccentBlue.copy(alpha = 0.25f))
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    }
    val textColor = if (entry.isUser) LiveAccentBlue else LiveTextPrimary
    val shape = if (entry.isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            shape = shape,
            color = bubbleColor,
            border = borderStroke,
            modifier = Modifier.widthIn(max = 280.dp),
            shadowElevation = 0.dp
        ) {
            Text(
                entry.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = TextStyle(
                    fontSize = 14.sp,
                    color = textColor,
                    lineHeight = 20.sp
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Ambient Background — Soft Pastel Glowing Orbs
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LiveAmbientBackground(
    infiniteTransition: InfiniteTransition,
    liveState: LiveState,
    audioLevel: Float
) {
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AmbientShift"
    )

    val orbColor = when (liveState) {
        is LiveState.Listening, is LiveState.UserSpeaking -> LiveAccentBlue
        is LiveState.Processing -> LiveAccentPurple
        is LiveState.AiSpeaking -> LiveAccentGreen
        else -> LiveTextDim
    }

    // Left orb (Pastel Cyan / Violet)
    Box(
        modifier = Modifier
            .size(380.dp)
            .offset(x = (-130).dp, y = (180 + shift * 60).dp)
            .blur(170.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        orbColor.copy(alpha = 0.05f + audioLevel * 0.03f),
                        Color.Transparent
                    )
                ),
                CircleShape
            )
    )

    // Right orb (Soft Lavender)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.End)
            .size(330.dp)
            .offset(x = 110.dp, y = (80 + shift * 50).dp)
            .blur(150.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        LiveAccentPurple.copy(alpha = 0.04f),
                        Color.Transparent
                    )
                ),
                CircleShape
            )
    )
}
