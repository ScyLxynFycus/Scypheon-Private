package com.scypheon.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun DeepResearchCard(
    stage: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = Color(0xFF161618).copy(alpha = 0.9f),
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .scale(scale)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF4285F4).copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF4285F4),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stage,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Running agentic research for ${seconds}s...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            
            // Phase indicator badge
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "AGENTIC",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4285F4),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
