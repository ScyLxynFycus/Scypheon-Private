package com.scypheon.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scypheon.sdk.core.agent.xai.MedicalExplanationReport
import com.scypheon.sdk.core.agent.xai.EvidenceSource

/**
 * Premium Glassmorphic Explanation Panel for Scypheon Private.
 * Visualizes the "Proof of Safety" for medical agentic decisions.
 */
@Composable
fun MedicalExplanationPanel(
    report: MedicalExplanationReport,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f) // Glass effect base
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.Black.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            // Header: Confidence & Verification Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Verified",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verified Medical Proof",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${(report.confidenceMetrics.overallScore * 100).toInt()}% Confidence",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Decision Rationale
            Text(
                text = "Logic Steps:",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            
            report.decisionRationale.take(3).forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "•", color = Color(0xFF00E676), modifier = Modifier.padding(end = 8.dp))
                    Text(
                        text = step,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    )
                }
            }

            // Evidence Section (Collapsible)
            Spacer(modifier = Modifier.height(12.dp))
            
            TextButton(
                onClick = { isExpanded = !isExpanded },
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = Color.Cyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isExpanded) "Hide Evidence" else "View ${report.evidenceSources.size} Sources & Audit Hash",
                    color = Color.Cyan,
                    fontSize = 13.sp
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    report.evidenceSources.forEach { source ->
                        EvidenceItem(source)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Audit Hash
                    Surface(
                        color = Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Audit Hash: ${report.auditMetadata.reproducibilityHash}",
                            modifier = Modifier.padding(8.dp),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceItem(source: EvidenceSource) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = source.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = "Source: ${source.source}",
            color = Color.Cyan.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = source.snippet,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp
        )
    }
}
