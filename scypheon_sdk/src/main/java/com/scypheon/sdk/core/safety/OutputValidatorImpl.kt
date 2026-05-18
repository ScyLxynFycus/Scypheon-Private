package com.scypheon.sdk.core.safety

import com.scypheon.sdk.core.agent.ooda.DeviceEnvironment
import com.scypheon.sdk.core.agent.RouterOutputValidator
import com.scypheon.sdk.core.grounding.MedicalGroundingEngine
import com.scypheon.sdk.core.agent.tool.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutputValidatorImpl @Inject constructor(
    private val groundingEngine: MedicalGroundingEngine
) : RouterOutputValidator, com.scypheon.sdk.core.agent.ooda.OutputValidator {
    companion object {
        private val PII_PATTERNS = listOf(
            Regex("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b"),
            Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
            Regex("\\b\\d{16}\\b"),
            Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b")
        )
        private const val HALLUCINATION_THRESHOLD = 0.65f
    }

    override suspend fun validateFinalResponse(text: String, env: DeviceEnvironment): RouterOutputValidator.FinalValidationResult = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext RouterOutputValidator.FinalValidationResult(false, "Empty response", piiDetected = false, hallucinationScore = 0.0f)

        val piiMatch = PII_PATTERNS.firstOrNull { it.containsMatchIn(text) }
        if (piiMatch != null) {
            return@withContext RouterOutputValidator.FinalValidationResult(false, "PII detected", piiDetected = true, hallucinationScore = 0.0f)
        }

        val grounding = groundingEngine.verify(text)
        if (grounding.confidence < HALLUCINATION_THRESHOLD) {
            return@withContext RouterOutputValidator.FinalValidationResult(
                isValid = false,
                reason = "Low medical grounding confidence: ${grounding.confidence}",
                hallucinationScore = 1.0f - grounding.confidence
            )
        }

        RouterOutputValidator.FinalValidationResult(true, "Validation passed", hallucinationScore = 1.0f - grounding.confidence)
    }

    override suspend fun validate(
        result: ToolResult,
        session: com.scypheon.sdk.core.agent.ooda.SessionContext,
        environment: DeviceEnvironment
    ): com.scypheon.sdk.core.agent.ooda.OutputValidator.ValidationResult = withContext(Dispatchers.Default) {
        val text = when (result) {
            is ToolResult.Success -> result.data?.toString() ?: ""
            is ToolResult.Fallback -> result.data?.toString() ?: ""
            is ToolResult.Error -> ""
            is ToolResult.AwaitingApproval -> ""
        }
        
        if (text.isBlank() && result is ToolResult.Error) {
             return@withContext com.scypheon.sdk.core.agent.ooda.OutputValidator.ValidationResult(false, "Tool error: ${result.reason}", "Execution failed.")
        }
        
        if (text.isBlank()) return@withContext com.scypheon.sdk.core.agent.ooda.OutputValidator.ValidationResult(false, "Empty tool response", "Execution returned no data.")
        
        val piiMatch = PII_PATTERNS.firstOrNull { it.containsMatchIn(text) }
        if (piiMatch != null) {
            return@withContext com.scypheon.sdk.core.agent.ooda.OutputValidator.ValidationResult(false, "PII detected in tool output", "Data redacted.", piiDetected = true)
        }
        
        val grounding = groundingEngine.verify(text)
        if (grounding.confidence < HALLUCINATION_THRESHOLD) {
             return@withContext com.scypheon.sdk.core.agent.ooda.OutputValidator.ValidationResult(
                isValid = false,
                reason = "Low grounding confidence",
                safeFallbackMessage = "Cannot verify data.",
                hallucinationScore = 1.0f - grounding.confidence
            )
        }
        com.scypheon.sdk.core.agent.ooda.OutputValidator.ValidationResult(true, "Validation passed", text, hallucinationScore = 1.0f - grounding.confidence)
    }
}
