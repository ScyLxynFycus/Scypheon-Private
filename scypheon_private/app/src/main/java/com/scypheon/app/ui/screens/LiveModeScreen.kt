package com.scypheon.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scypheon.sdk.core.live.LiveSessionOrchestrator
import com.scypheon.sdk.core.live.LiveSessionOrchestrator.LiveState
import kotlin.math.cos
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════
// [v3.1.0-SAR] SCYPHEON LIVE — HALO Holographic × Seamless Circular Audio Waves
// Premium light UI matching Main Screen background.
// Fluid Halo-Cortana holographic orb with 90-bar seamless circular soundwave bars.
// ═══════════════════════════════════════════════════════════════════

// ─── Design Tokens ─── Light Premium Palette ───
private val LiveBgStart = Color(0xFFFCFDFF)          // Matches Main Chat Bg start (white)
private val LiveBgEnd = Color(0xFFF0F4FA)            // Matches Main Chat Bg end (soft blue-gray)

// Typography colors for light theme
private val TextPrimary = Color(0xFF1D1D1F)           // Apple-like black
private val TextSecondary = Color(0xFF5F6368)         // Slate gray
private val TextDim = Color(0xFF86868B)               // Muted gray
private val TextMuted = Color(0x995F6368)             // 60% secondary text

// Halo Holographic Gradients (Cortana/Energy Shield inspired)
private val HaloCyan = Color(0xFF00F0FF)              // Active hologram cyan
private val HaloCortanaBlue = Color(0xFF0055FF)       // Cortana deep blue
private val HaloPurple = Color(0xFF8B00FF)            // Hologram purple
private val HaloMagenta = Color(0xFFFF00D6)           // Cortana magenta highlight
private val HaloWhite = Color(0xFFFFFFFF)             // High-intensity white core
private val HaloAmber = Color(0xFFFF9D00)             // Shield recharge amber
private val HaloGreen = Color(0xFF00FF88)             // Systems active green
private val HaloRed = Color(0xFFFF2442)               // Rampancy alert red

// Orb quad-color structure for state-based rendering
private data class OrbPalette(
    val primary: Color,
    val secondary: Color,
    val highlight: Color,
    val glow: Color
)

// ═══════════════════════════════════════════════════════════════════
// Main Screen Composable
// ═══════════════════════════════════════════════════════════════════

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

    // Elapsed time counter
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(LiveBgStart, LiveBgEnd)
                )
            )
    ) {
        // ─── Layer 0: Halo Ambient Hologram Glows ───
        LiveAmbientBackground(infiniteTransition, liveState, audioLevel)

        // ─── Main Content Column ───
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Top Bar ───
            LiveTopBar(
                elapsedSeconds = elapsedSeconds,
                onEndSession = onEndSession
            )

            Spacer(Modifier.weight(0.55f))

            // ─── Central Voice Orb & Seamless Circular Audio Wave ───
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onOrbClick() },
                contentAlignment = Alignment.Center
            ) {
                // Expanding Soundwave Ripples (Base)
                LiveVoiceRipples(
                    liveState = liveState,
                    audioLevel = audioLevel,
                    infiniteTransition = infiniteTransition
                )

                // Halo Holographic Orb & the Seamless Circular Soundwave Bars
                LivePulsingOrb(
                    liveState = liveState,
                    audioLevel = audioLevel,
                    infiniteTransition = infiniteTransition
                )
            }

            Spacer(Modifier.weight(0.15f))

            // ─── State Label ───
            LiveStateLabel(liveState)

            Spacer(Modifier.height(16.dp))

            // ─── Transcript Area with top gradient fade ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LiveTranscript(
                    transcript = transcript,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp)
                )

                // Gradient fade at top of transcript
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(LiveBgStart, Color.Transparent)
                            )
                        )
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Top Bar — Minimalist, Light Frosted Button
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LiveTopBar(
    elapsedSeconds: Int,
    onEndSession: () -> Unit
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
        // Left: Pulsing hologram indicator dot + elapsed time
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "TopDot")
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "DotPulse"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .background(HaloCyan, CircleShape)
            )

            Text(
                text = timeString,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
            )
        }

        // Right: Frosted glass close button
        Surface(
            onClick = onEndSession,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.05f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.Black.copy(alpha = 0.05f)
            ),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "End Session",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Voice Ripples — Halo Energy Shield Pulse Rings
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LiveVoiceRipples(
    liveState: LiveState,
    audioLevel: Float,
    infiniteTransition: InfiniteTransition
) {
    if (liveState == LiveState.Idle) return

    val audioScale by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 120f),
        label = "RippleAudioScale"
    )

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RipplePhase"
    )

    val color = when (liveState) {
        is LiveState.Listening -> HaloCyan
        is LiveState.UserSpeaking -> HaloAmber
        is LiveState.Processing -> HaloPurple
        is LiveState.AiSpeaking -> HaloGreen
        is LiveState.Error -> HaloRed
        else -> TextDim
    }

    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(600),
        label = "RippleColor"
    )

    Box(contentAlignment = Alignment.Center) {
        for (i in 0..2) {
            val progress = (phase + i / 3f) % 1f
            val scale = 1.0f + progress * (1.3f + audioScale * 0.9f)
            val alpha = (1f - progress) * (0.12f + audioScale * 0.22f)

            Box(
                modifier = Modifier
                    .size(175.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .border(
                        width = (1.5.dp / scale).coerceAtLeast(0.5.dp),
                        color = animatedColor,
                        shape = CircleShape
                    )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Voice Orb — Halo Cortana Holographic Orb + Circular Soundwaves
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LivePulsingOrb(
    liveState: LiveState,
    audioLevel: Float,
    infiniteTransition: InfiniteTransition
) {
    // Rotation angles for organic hologram swirl (slowed down for buttery smoothness)
    val angle1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(24000, easing = LinearEasing)),
        label = "OrbAngle1"
    )
    val angle2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(34000, easing = LinearEasing)),
        label = "OrbAngle2"
    )

    // Fluid breathing sizes (slowed down and smoothed with cubic-bezier EaseInOut)
    val breathe1 by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(6000, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)),
            RepeatMode.Reverse
        ),
        label = "Breathe1"
    )
    val breathe2 by infiniteTransition.animateFloat(
        initialValue = 1.04f,
        targetValue = 0.96f,
        animationSpec = infiniteRepeatable(
            tween(7500, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)),
            RepeatMode.Reverse
        ),
        label = "Breathe2"
    )

    // Slower, fluid wave phase progression (loop-free and buttery smooth)
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    // Responsive audio scale
    val audioScale by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 150f),
        label = "OrbAudioScale"
    )

    // State-based Halo/Cortana Palettes
    val palette = when (liveState) {
        is LiveState.Listening -> OrbPalette(
            primary = HaloCortanaBlue,
            secondary = HaloPurple,
            highlight = HaloCyan,
            glow = HaloCyan
        )
        is LiveState.UserSpeaking -> OrbPalette(
            primary = HaloAmber,
            secondary = HaloMagenta,
            highlight = HaloWhite,
            glow = HaloAmber
        )
        is LiveState.Processing -> OrbPalette(
            primary = HaloPurple,
            secondary = HaloMagenta,
            highlight = HaloCyan,
            glow = HaloPurple
        )
        is LiveState.AiSpeaking -> OrbPalette(
            primary = HaloCyan,
            secondary = HaloCortanaBlue,
            highlight = HaloGreen,
            glow = HaloCyan
        )
        is LiveState.Error -> OrbPalette(
            primary = HaloRed,
            secondary = HaloMagenta,
            highlight = HaloWhite,
            glow = HaloRed
        )
        else -> OrbPalette(
            primary = Color(0xFF1558D6),      // Main UI Premium Accent Blue!
            secondary = Color(0xFF00F0FF),    // Active hologram cyan gradient!
            highlight = Color(0xFFFFFFFF),    // Bright core
            glow = Color(0xFF1558D6)
        )
    }

    val animPrimary by animateColorAsState(targetValue = palette.primary, animationSpec = tween(600), label = "Primary")
    val animSecondary by animateColorAsState(targetValue = palette.secondary, animationSpec = tween(600), label = "Secondary")
    val animHighlight by animateColorAsState(targetValue = palette.highlight, animationSpec = tween(600), label = "Highlight")
    val animGlow by animateColorAsState(targetValue = palette.glow, animationSpec = tween(600), label = "Glow")

    Box(
        modifier = Modifier
            .size(320.dp)
            .graphicsLayer {
                val totalScale = 1.0f + audioScale * 0.12f
                scaleX = totalScale
                scaleY = totalScale
            },
        contentAlignment = Alignment.Center
    ) {
        // ─── Layer 0: Deep Ambient Nebula Glow (The Singularity Core) ───
        // This replaces the solid orb. It's a massive, highly blurred, intensely colorful glowing cloud.
        Box(
            modifier = Modifier
                .size(260.dp)
                .graphicsLayer {
                    val pulse = 1.0f + audioScale * 0.4f
                    scaleX = pulse
                    scaleY = pulse
                }
                .blur(50.dp)
                .alpha(0.85f + audioScale * 0.15f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animHighlight,
                            animPrimary.copy(alpha = 0.8f),
                            animSecondary.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        // ─── Layer 1: Inner Swirling Plasma ───
        // A subtle secondary glow to give the core movement and depth
        Box(
            modifier = Modifier
                .size(150.dp)
                .graphicsLayer {
                    rotationZ = angle1
                    scaleX = breathe1 * (1.0f + audioScale * 0.5f)
                    scaleY = breathe2 * (1.0f + audioScale * 0.5f)
                    alpha = 0.6f + audioScale * 0.4f
                }
                .blur(30.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(animSecondary, Color.Transparent, animPrimary)
                    ),
                    CircleShape
                )
        )

        // ─── Layer 2: Seamless Circular Equalizer Bars (The Singularity Ring) ───
        // Bold, thick, inward & outward radiating bars
        val barCount = 48 // Reduced count for thicker, bolder bars
        Canvas(modifier = Modifier.size(360.dp)) { // Increased canvas size for larger soundwave
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = 100.dp.toPx() // Invisible boundary where bars start

            for (i in 0 until barCount) {
                val angleDeg = i * (360f / barCount)
                val angleRad = (angleDeg * Math.PI / 180.0).toFloat()
                val cosA = cos(angleRad)
                val sinA = sin(angleRad)

                // Perfect circular looping using strictly INTEGER multipliers for wavePhase
                val wave1 = sin(angleRad * 3.0f - wavePhase) * 0.25f
                val wave2 = cos(angleRad * 5.0f + wavePhase * 1.0f) * 0.15f
                val wave3 = sin(angleRad * 2.0f + wavePhase * 2.0f) * 0.20f
                
                // Ambient idle movement + active audio height factor
                val audioSpread = sin(angleRad * 4.0f - wavePhase * 1.0f) * 0.4f + 0.6f
                val audioSpike = audioScale * audioSpread * 1.2f // Larger spike
                
                val totalHeightFactor = (0.15f + wave1 + wave2 + wave3 + audioSpike).coerceIn(0.02f, 1.8f)
                
                // Bar lengths
                val maxOuterHeight = 45.dp.toPx()
                val maxInnerHeight = 25.dp.toPx()
                
                // Bars extend both OUTWARD and INWARD from the base radius
                val outerExt = totalHeightFactor * maxOuterHeight
                val innerExt = (totalHeightFactor * maxInnerHeight * 0.6f) + (audioScale * maxInnerHeight * 0.8f)
                
                val startRadius = baseRadius - innerExt
                val endRadius = baseRadius + outerExt
                
                val startX = center.x + startRadius * cosA
                val startY = center.y + startRadius * sinA
                val endX = center.x + endRadius * cosA
                val endY = center.y + endRadius * sinA

                // Color interpolation: Gradient matches the core
                val colorProgress = ((sin(angleRad + wavePhase) + 1f) / 2f)
                val baseColor = androidx.compose.ui.graphics.lerp(animPrimary, animSecondary, colorProgress)
                val barColor = androidx.compose.ui.graphics.lerp(baseColor, animHighlight, (audioScale * 0.4f).coerceIn(0f, 1f))

                drawLine(
                    color = barColor.copy(alpha = 0.9f), // Highly visible premium bars
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 4.5.dp.toPx(), // Thicker, bolder bars
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// State Label — Elegant Dark Legible Typography
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun LiveStateLabel(liveState: LiveState) {
    val (text, subtext) = when (liveState) {
        is LiveState.Listening -> "Ready" to "Tap to speak"
        is LiveState.UserSpeaking -> {
            val partial = liveState.partialText
            if (partial.isBlank()) "Listening..." to null
            else partial to "Tap to send"
        }
        is LiveState.Processing -> "Analyzing Core..." to null
        is LiveState.AiSpeaking -> liveState.responseText to "Tap to interrupt"
        is LiveState.Error -> "Re-establishing connection..." to null
        else -> "Standby" to null
    }

    val primaryColor by animateColorAsState(
        targetValue = when (liveState) {
            is LiveState.Listening -> TextSecondary
            is LiveState.UserSpeaking -> TextPrimary
            is LiveState.Processing -> HaloPurple
            is LiveState.AiSpeaking -> TextPrimary
            is LiveState.Error -> HaloRed
            else -> TextDim
        },
        animationSpec = tween(500),
        label = "LabelColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 40.dp)
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (fadeIn(tween(400)) + slideInVertically { it / 4 }) togetherWith
                    (fadeOut(tween(300)) + slideOutVertically { -it / 4 })
            },
            label = "StateLabel"
        ) { label ->
            Text(
                text = label,
                style = TextStyle(
                    fontSize = when (liveState) {
                        is LiveState.UserSpeaking -> 18.sp
                        is LiveState.AiSpeaking -> 15.sp
                        else -> 16.sp
                    },
                    fontWeight = when (liveState) {
                        is LiveState.UserSpeaking, is LiveState.AiSpeaking -> FontWeight.SemiBold
                        else -> FontWeight.Medium
                    },
                    color = primaryColor,
                    textAlign = TextAlign.Center,
                    letterSpacing = when (liveState) {
                        is LiveState.Listening -> 2.sp
                        is LiveState.Processing -> 1.5.sp
                        else -> 0.sp
                    },
                    lineHeight = 24.sp
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Subtext action hints
        AnimatedVisibility(
            visible = subtext != null,
            enter = fadeIn(tween(400)) + expandVertically(),
            exit = fadeOut(tween(300)) + shrinkVertically()
        ) {
            subtext?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextDim,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }

        // Circular processing progress indicators
        if (liveState is LiveState.Processing) {
            Spacer(Modifier.height(4.dp))
            ProcessingDots(infiniteTransition = rememberInfiniteTransition(label = "ProcDots"))
        }
    }
}

@Composable
private fun ProcessingDots(infiniteTransition: InfiniteTransition) {
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Dot1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 150, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Dot2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Dot3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(dot1, dot2, dot3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(HaloPurple, CircleShape)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Transcript — Minimalist Light Entries
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LiveTranscript(
    transcript: List<LiveSessionOrchestrator.TranscriptEntry>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) {
            listState.animateScrollToItem(transcript.lastIndex)
        }
    }

    if (transcript.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "Holographic link stabilized. Transcripts active.",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextDim,
                    textAlign = TextAlign.Center
                )
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 44.dp, bottom = 8.dp)
        ) {
            items(transcript) { entry ->
                LiveTranscriptItem(entry)
            }
        }
    }
}

@Composable
private fun LiveTranscriptItem(entry: LiveSessionOrchestrator.TranscriptEntry) {
    val alignment = if (entry.isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Text(
            text = if (entry.isUser) "You" else "Scypheon",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (entry.isUser) HaloCortanaBlue.copy(alpha = 0.8f) else TextDim,
                letterSpacing = 0.5.sp
            ),
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (entry.isUser) {
                HaloCortanaBlue.copy(alpha = 0.08f)
            } else {
                Color.Black.copy(alpha = 0.04f)
            },
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = entry.text,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextPrimary,
                    lineHeight = 21.sp,
                    textAlign = if (entry.isUser) TextAlign.End else TextAlign.Start
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Ambient Background — Hologram Glows
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LiveAmbientBackground(
    infiniteTransition: InfiniteTransition,
    liveState: LiveState,
    audioLevel: Float
) {
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AmbientDrift"
    )

    val secondaryDrift by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SecondaryDrift"
    )

    val ambientColor by animateColorAsState(
        targetValue = when (liveState) {
            is LiveState.Listening -> HaloCyan
            is LiveState.UserSpeaking -> HaloAmber
            is LiveState.Processing -> HaloPurple
            is LiveState.AiSpeaking -> HaloGreen
            is LiveState.Error -> HaloRed
            else -> TextDim
        },
        animationSpec = tween(1000),
        label = "AmbientColor"
    )

    // Center-left primary hologram glow
    Box(
        modifier = Modifier
            .size(380.dp)
            .offset(
                x = (-80 + drift * 30).dp,
                y = (180 + drift * 60).dp
            )
            .blur(160.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ambientColor.copy(alpha = 0.08f + audioLevel * 0.04f),
                        Color.Transparent
                    )
                ),
                CircleShape
            )
    )

    // Top-right secondary glow
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.End)
            .size(320.dp)
            .offset(
                x = (60 + secondaryDrift * 30).dp,
                y = (80 + secondaryDrift * 50).dp
            )
            .blur(140.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        HaloAmber.copy(alpha = 0.05f + audioLevel * 0.02f),
                        Color.Transparent
                    )
                ),
                CircleShape
            )
    )
}
