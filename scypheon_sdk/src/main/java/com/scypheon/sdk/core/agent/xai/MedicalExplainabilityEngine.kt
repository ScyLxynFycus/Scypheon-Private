package com.scypheon.sdk.core.agent.xai

import com.scypheon.sdk.core.agent.tool.ToolCall
import com.scypheon.sdk.core.agent.tool.ToolResult
import kotlinx.datetime.Instant

interface MedicalExplainabilityEngine {
    suspend fun generateExplanation(
        toolCall: ToolCall,
        executionResult: ToolResult,
        patientContext: String? = null,
        depth: ExplanationDepth = ExplanationDepth.STANDARD
    ): MedicalExplanationReport
}

data class MedicalExplanationReport(
    val toolCall: ToolCall,
    val decisionRationale: List<String>,
    val evidenceSources: List<EvidenceSource>,
    val confidenceMetrics: ConfidenceMetrics,
    val patientSummary: String? = null,
    val auditMetadata: AuditMetadata
)

data class EvidenceSource(
    val title: String,
    val snippet: String,
    val source: String, // e.g., "WHO Guidelines", "FDA Label"
    val confidence: Float
)

data class ConfidenceMetrics(
    val overallScore: Float,
    val logicScore: Float,
    val dataScore: Float
)

data class AuditMetadata(
    val timestamp: Instant,
    val modelVersion: String,
    val reproducibilityHash: String
)

enum class ExplanationDepth {
    PATIENT_FRIENDLY,
    STANDARD,
    AUDIT_GRADE
}
