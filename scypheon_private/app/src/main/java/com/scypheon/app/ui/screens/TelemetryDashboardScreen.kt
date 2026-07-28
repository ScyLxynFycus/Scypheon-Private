package com.scypheon.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scypheon.sdk.core.security.AuditLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════
// [v1.5.0-SAR] AEGIS VAULT — Premium Activity History
// Apple Settings-inspired design with categorized log entries
// ═══════════════════════════════════════════════════════════════════

// Design Tokens
private val AegisBg = Color(0xFFF2F2F7)        // iOS Settings background
private val AegisSurface = Color(0xFFFFFFFF)
private val AegisTextPrimary = Color(0xFF1C1C1E)
private val AegisTextSecondary = Color(0xFF8E8E93)
private val AegisTextTertiary = Color(0xFFC7C7CC)
private val AegisDivider = Color(0xFFE5E5EA)
private val AegisBlue = Color(0xFF007AFF)
private val AegisGreen = Color(0xFF34C759)
private val AegisOrange = Color(0xFFFF9500)
private val AegisRed = Color(0xFFFF3B30)
private val AegisPurple = Color(0xFF5856D6)
private val AegisCyan = Color(0xFF32ADE6)

private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetryDashboardScreen(
    logs: List<AuditLogEntry>,
    onBack: () -> Unit
) {
    // Count stats for the summary card
    val totalEvents = logs.size
    val criticalCount = logs.count { extractSecurityLevel(it.payload) == "CRITICAL" }
    val warningCount = logs.count { extractSecurityLevel(it.payload) == "WARNING" }

    Scaffold(
        containerColor = AegisBg,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AegisSurface.copy(alpha = 0.94f),
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
                        // iOS-style back button with text
                        TextButton(
                            onClick = onBack,
                            colors = ButtonDefaults.textButtonColors(contentColor = AegisBlue)
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "Back",
                                style = TextStyle(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        Text(
                            "Aegis Vault",
                            style = TextStyle(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AegisTextPrimary,
                                letterSpacing = (-0.3).sp
                            )
                        )

                        Spacer(Modifier.weight(1f))

                        // Ghost spacer to balance the back button
                        Spacer(Modifier.width(80.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(AegisDivider)
                    )
                }
            }
        }
    ) { paddingValues ->
        if (logs.isEmpty()) {
            AegisEmptyState(modifier = Modifier.padding(paddingValues))
        } else {
            val reversedLogs = remember(logs) { logs.reversed() }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // ─── Summary Stats Card ───
                item {
                    Spacer(Modifier.height(16.dp))
                    AegisSummaryCard(
                        totalEvents = totalEvents,
                        criticalCount = criticalCount,
                        warningCount = warningCount
                    )
                }

                // ─── Section Header ───
                item {
                    Text(
                        "RECENT ACTIVITY",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AegisTextSecondary,
                            letterSpacing = 0.5.sp
                        ),
                        modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp)
                    )
                }

                // ─── Log Items ───
                itemsIndexed(reversedLogs, key = { _, log -> log.hashCode() }) { index, log ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        color = AegisSurface,
                        // Top rounded corner for first item, bottom for last
                        shape = when {
                            reversedLogs.size == 1 -> RoundedCornerShape(12.dp)
                            index == 0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            index == reversedLogs.lastIndex -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                            else -> androidx.compose.ui.graphics.RectangleShape
                        }
                    ) {
                        Column {
                            AegisLogItem(log)
                            if (index < reversedLogs.lastIndex) {
                                // iOS-style indented divider
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 60.dp)
                                        .height(0.5.dp)
                                        .background(AegisDivider)
                                )
                            }
                        }
                    }
                }

                // ─── Footer ───
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "All activity is stored locally on-device using AES-256 encryption.\nNo data is transmitted externally.",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = AegisTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Summary Card — At-a-glance event stats
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AegisSummaryCard(totalEvents: Int, criticalCount: Int, warningCount: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = AegisSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(AegisBlue, AegisPurple)
                            ),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        "System Overview",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AegisTextPrimary
                        )
                    )
                    Text(
                        "Encrypted BlackBox · Integrity Verified",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = AegisGreen,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(AegisDivider)
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AegisStatItem(
                    value = totalEvents.toString(),
                    label = "Events",
                    color = AegisBlue
                )
                AegisStatItem(
                    value = criticalCount.toString(),
                    label = "Critical",
                    color = if (criticalCount > 0) AegisRed else AegisTextTertiary
                )
                AegisStatItem(
                    value = warningCount.toString(),
                    label = "Warnings",
                    color = if (warningCount > 0) AegisOrange else AegisTextTertiary
                )
                AegisStatItem(
                    value = (totalEvents - criticalCount - warningCount).toString(),
                    label = "Normal",
                    color = AegisGreen
                )
            }
        }
    }
}

@Composable
private fun AegisStatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
        Text(
            label,
            style = TextStyle(
                fontSize = 11.sp,
                color = AegisTextSecondary,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Log Item — Categorized with icon, timestamp, and color
// ═══════════════════════════════════════════════════════════════════

@Composable
fun TelemetryLogItem(log: AuditLogEntry) {
    AegisLogItem(log)
}

@Composable
private fun AegisLogItem(log: AuditLogEntry) {
    val securityLevel = extractSecurityLevel(log.payload)
    val details = extractDetails(log.payload)

    val (icon, iconBg, iconTint) = remember(log.eventType, securityLevel) {
        resolveLogVisuals(log.eventType, securityLevel)
    }

    val formattedTime = remember(log.timestamp) {
        timeFormatter.format(Date(log.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Category icon badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconBg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Clean event name
            Text(
                text = formatEventName(log.eventType),
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = AegisTextPrimary,
                    letterSpacing = (-0.2).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatDetails(details),
                style = TextStyle(
                    fontSize = 13.sp,
                    color = AegisTextSecondary,
                    lineHeight = 17.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        // Timestamp
        Text(
            text = formattedTime,
            style = TextStyle(
                fontSize = 12.sp,
                color = AegisTextTertiary
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Empty State
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AegisEmptyState(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "EmptyAnim")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RingPulse"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(AegisBlue, AegisPurple)
                        ),
                        shape = CircleShape
                    )
                    .alpha(ringAlpha)
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                AegisBlue.copy(alpha = 0.08f),
                                AegisPurple.copy(alpha = 0.05f)
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = AegisBlue,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "All Clear",
            style = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AegisTextPrimary,
                letterSpacing = (-0.5).sp
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your activity vault is empty.\nAll future AI decisions will be logged here.",
            style = TextStyle(
                fontSize = 15.sp,
                color = AegisTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Utility: Map event types to icons and colors
// ═══════════════════════════════════════════════════════════════════

private data class LogVisuals(val icon: ImageVector, val bg: Color, val tint: Color)

private fun resolveLogVisuals(eventType: String, securityLevel: String): LogVisuals {
    return when {
        securityLevel == "CRITICAL" -> LogVisuals(
            Icons.Default.ErrorOutline,
            AegisRed.copy(alpha = 0.1f),
            AegisRed
        )
        securityLevel == "WARNING" -> LogVisuals(
            Icons.Default.WarningAmber,
            AegisOrange.copy(alpha = 0.1f),
            AegisOrange
        )
        eventType.contains("INFERENCE", ignoreCase = true) -> LogVisuals(
            Icons.Default.Psychology,
            AegisPurple.copy(alpha = 0.1f),
            AegisPurple
        )
        eventType.contains("HOOK", ignoreCase = true) -> LogVisuals(
            Icons.Default.Extension,
            AegisCyan.copy(alpha = 0.1f),
            AegisCyan
        )
        eventType.contains("SAFETY", ignoreCase = true) -> LogVisuals(
            Icons.Default.Shield,
            AegisRed.copy(alpha = 0.1f),
            AegisRed
        )
        eventType.contains("MEMORY", ignoreCase = true) || eventType.contains("GRAPH", ignoreCase = true) -> LogVisuals(
            Icons.Default.Hub,
            AegisGreen.copy(alpha = 0.1f),
            AegisGreen
        )
        eventType.contains("MODEL", ignoreCase = true) || eventType.contains("ENGINE", ignoreCase = true) -> LogVisuals(
            Icons.Default.Memory,
            AegisBlue.copy(alpha = 0.1f),
            AegisBlue
        )
        else -> LogVisuals(
            Icons.Default.Circle,
            AegisGreen.copy(alpha = 0.1f),
            AegisGreen
        )
    }
}

private fun formatEventName(raw: String): String {
    return raw
        .replace("_", " ")
        .lowercase()
        .replaceFirstChar { it.uppercase() }
}

private fun formatDetails(raw: String): String {
    // Clean up technical jargon for better readability
    return raw
        .replace("Path: ", "")
        .replace("(Turns=", "· ")
        .replace(")", "")
        .trim()
}

private fun extractSecurityLevel(payload: String): String {
    return try {
        org.json.JSONObject(payload).optString("securityLevel", "INFO")
    } catch (e: Exception) {
        "INFO"
    }
}

private fun extractDetails(payload: String): String {
    return try {
        org.json.JSONObject(payload).optString("details", payload)
    } catch (e: Exception) {
        payload
    }
}
