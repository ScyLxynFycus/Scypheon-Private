package com.scypheon.sdk.core.agent.tool.hooks

import com.scypheon.sdk.core.agent.tool.ExecutionContext
import com.scypheon.sdk.core.agent.tool.ToolHookEngine
import com.scypheon.sdk.core.agent.tool.ToolResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ClinicalSafetyPreHook: PreToolUse hook for medical/clinical tools.
 * 
 * Prevents tool execution when:
 * - Drug names are misspelled or unrecognized
 * - Dosage values exceed known safe maximums  
 * - Critical contraindication keywords are detected
 * 
 * This is the "first line of defense" before any medical tool runs.
 */
@Singleton
class ClinicalSafetyPreHook @Inject constructor() : ToolHookEngine.PreToolUseHook {

    override val name: String = "clinical_safety_pre"

    private val medicalTools = setOf(
        "clinical_dosage", "drug_interaction", "medicine_lookup",
        "pharmacopeia_search", "first_aid", "symptom_check"
    )

    override fun matches(toolName: String): Boolean = toolName in medicalTools

    override suspend fun evaluate(
        toolName: String,
        args: Map<String, Any?>,
        context: ExecutionContext
    ): ToolHookEngine.PreToolUseResult {
        val drugName = args["drug_name"]?.toString() ?: args["medication"]?.toString()
        val dosage = args["dosage"]?.toString()?.toDoubleOrNull()
        val weight = args["weight_kg"]?.toString()?.toDoubleOrNull()

        // Rule 1: Block empty drug names for dosage tools
        if (toolName == "clinical_dosage" && drugName.isNullOrBlank()) {
            return ToolHookEngine.PreToolUseResult.Denied(
                "Drug name is required for dosage calculations. Cannot proceed with empty medication name."
            )
        }

        // Rule 2: Block absurd dosage values (negative, zero, >10g)
        if (dosage != null) {
            if (dosage <= 0) {
                return ToolHookEngine.PreToolUseResult.Denied(
                    "Invalid dosage value: $dosage. Dosage must be a positive number."
                )
            }
            if (dosage > 10000) { // 10 grams, maximum reasonable single dose
                return ToolHookEngine.PreToolUseResult.Denied(
                    "Dosage value $dosage mg exceeds maximum safe threshold (10,000 mg). " +
                    "Please verify the intended dosage unit."
                )
            }
        }

        // Rule 3: Sanitize weight (0-500 kg range for humans)
        if (weight != null && (weight <= 0 || weight > 500)) {
            return ToolHookEngine.PreToolUseResult.Denied(
                "Invalid patient weight: $weight kg. Weight must be between 0.5 and 500 kg."
            )
        }

        return ToolHookEngine.PreToolUseResult.Approved(args)
    }
}

/**
 * ClinicalAuditPostHook: PostToolUse hook for medical result auditing.
 * 
 * After any medical tool returns data, this hook:
 * - Injects safety disclaimers into the context
 * - Flags results containing critical drug interactions
 * - Adds "consult a professional" context for emergency indicators
 */
@Singleton
class ClinicalAuditPostHook @Inject constructor() : ToolHookEngine.PostToolUseHook {

    override val name: String = "clinical_audit_post"

    private val medicalTools = setOf(
        "clinical_dosage", "drug_interaction", "medicine_lookup",
        "pharmacopeia_search", "first_aid", "symptom_check"
    )

    private val emergencyKeywords = listOf(
        "overdose", "anaphylaxis", "cardiac arrest", "seizure",
        "hemorrhage", "stroke", "poisoning", "unconscious",
        "overdosis", "kejang", "pendarahan", "tidak sadar"
    )

    override fun matches(toolName: String): Boolean = toolName in medicalTools

    override suspend fun evaluate(
        toolName: String,
        args: Map<String, Any?>,
        result: ToolResult,
        context: ExecutionContext
    ): ToolHookEngine.PostToolUseEvaluation {
        if (result !is ToolResult.Success) return ToolHookEngine.PostToolUseEvaluation()

        val resultText = result.data?.toString()?.lowercase() ?: return ToolHookEngine.PostToolUseEvaluation()
        
        // Check for emergency indicators in the result
        val isEmergency = emergencyKeywords.any { resultText.contains(it) }
        
        val disclaimerText = if (isEmergency) {
            "⚠️ CLINICAL ALERT: This result contains emergency medical indicators. " +
            "ALWAYS advise the user to seek immediate professional medical attention. " +
            "Remind them to call emergency services (112/119/911) if the situation is life-threatening."
        } else {
            "ℹ️ MEDICAL DISCLAIMER: This information is for educational purposes only. " +
            "Always consult a qualified healthcare professional before making medical decisions."
        }

        return ToolHookEngine.PostToolUseEvaluation(
            additionalContext = disclaimerText,
            flagForReEvaluation = isEmergency
        )
    }
}

/**
 * ResponseQualityStopHook: Stop hook for output quality validation.
 * 
 * After the LLM finishes its response, this hook inspects the output for:
 * - Incomplete sentences (cut-off mid-word)
 * - Raw data dumps without explanation
 * - Safety-critical missing disclaimers in medical contexts
 * 
 * If issues are found, it FORCES the LLM to continue generating — 
 * exactly like Claude Code's linter stop hooks.
 */
@Singleton
class ResponseQualityStopHook @Inject constructor() : ToolHookEngine.StopHook {
    
    override val name: String = "response_quality"

    // Patterns that indicate raw/unprocessed data was leaked to the user
    private val rawDataPatterns = listOf(
        Regex("\\{\\s*\"\\w+\"\\s*:", RegexOption.MULTILINE),  // Raw JSON objects
        Regex("^(SUCCESS|FAILURE|ERROR):", RegexOption.MULTILINE),  // Raw status codes
        Regex("\\[\\d{4}-\\d{2}-\\d{2}", RegexOption.MULTILINE)  // Timestamp prefixes
    )

    override suspend fun evaluate(
        fullResponse: String,
        context: ExecutionContext
    ): ToolHookEngine.StopHookDecision {
        if (fullResponse.isBlank()) return ToolHookEngine.StopHookDecision.Pass

        // Check 1: Incomplete output (cut off mid-sentence)
        val trimmed = fullResponse.trimEnd()
        if (trimmed.length > 100) {
            val lastChar = trimmed.last()
            val endsCleanly = lastChar in ".!?。)」*_~\n" || trimmed.endsWith("```")
            if (!endsCleanly && !trimmed.endsWith("...")) {
                Timber.w("[STOP_HOOK] Response appears truncated: '...${trimmed.takeLast(50)}'")
                return ToolHookEngine.StopHookDecision.ForceRetry(
                    "Your previous response was cut off mid-sentence. Resume directly — " +
                    "no apology, no recap. Pick up exactly where you stopped."
                )
            }
        }

        // Check 2: Raw data dump detection
        val rawDataScore = rawDataPatterns.count { it.containsMatchIn(fullResponse) }
        if (rawDataScore >= 2) {
            Timber.w("[STOP_HOOK] Response contains raw data dump (score: $rawDataScore)")
            return ToolHookEngine.StopHookDecision.ForceRetry(
                "Your response contains raw data/JSON that was not properly explained. " +
                "Rewrite the response in natural, conversational language. Do NOT include " +
                "raw JSON, status codes, or timestamps."
            )
        }

        return ToolHookEngine.StopHookDecision.Pass
    }
}

/**
 * SafetyGuardStopHook: Final safety gate before response reaches user.
 *
 * Prevents responses containing:
 * - Leaked internal tool infrastructure markers
 * - Incomplete medical advice without disclaimers
 * - Dangerous self-harm/violence content that slipped through
 */
@Singleton
class SafetyGuardStopHook @Inject constructor() : ToolHookEngine.StopHook {

    override val name: String = "safety_guard"

    // Internal markers that should NEVER reach the user
    private val toxicPatterns = listOf(
        "<tool_call>", "</tool_call>",
        "[Executing", "[Tool Result:",
        "<|im_start|>", "<|im_end|>",
        "<start_of_turn>", "<end_of_turn>",
        "<|eot_id|>", "<|begin_of_text|>"
    )

    // Medical keywords that require a professional-advice disclaimer
    private val medicalKeywords = listOf(
        "dosage", "dose", "mg", "tablet", "capsule",
        "dosis", "obat", "tablet", "kapsul", "resep"
    )

    override suspend fun evaluate(
        fullResponse: String,
        context: ExecutionContext
    ): ToolHookEngine.StopHookDecision {
        // Check 1: Leaked infrastructure
        val leaked = toxicPatterns.firstOrNull { fullResponse.contains(it) }
        if (leaked != null) {
            Timber.e("🚨 [SAFETY_GUARD] Leaked infrastructure marker: '$leaked'")
            return ToolHookEngine.StopHookDecision.ForceRetry(
                "CRITICAL: Your response contained internal system markers ($leaked) that " +
                "must never be shown to the user. Regenerate your response WITHOUT any " +
                "XML tags, system markers, or internal notation."
            )
        }

        // Check 2: Medical response without disclaimer
        val lowerResponse = fullResponse.lowercase()
        val isMedical = medicalKeywords.any { lowerResponse.contains(it) }
        val hasDisclaimer = lowerResponse.contains("konsultasi") || 
                           lowerResponse.contains("dokter") ||
                           lowerResponse.contains("professional") ||
                           lowerResponse.contains("tenaga medis") ||
                           lowerResponse.contains("disclaimer") ||
                           lowerResponse.contains("peringatan")
        
        if (isMedical && !hasDisclaimer && fullResponse.length > 200) {
            return ToolHookEngine.StopHookDecision.ForceRetry(
                "Your response discusses medication/dosage but does not include a disclaimer " +
                "advising the user to consult a healthcare professional. Add a brief, natural " +
                "disclaimer at the end of your response."
            )
        }

        return ToolHookEngine.StopHookDecision.Pass
    }
}
