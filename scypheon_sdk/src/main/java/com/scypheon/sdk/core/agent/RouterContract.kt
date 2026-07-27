package com.scypheon.sdk.core.agent

import com.scypheon.sdk.core.agent.ooda.DeviceEnvironment
import com.scypheon.sdk.core.agent.ooda.SessionContext

// --- Supporting Interfaces & Enums for AgenticRouter ---

enum class SafetyVerdict { SAFE, FLAGGED, BLOCKED, OVERRIDDEN }

interface SystemMonitor {
    suspend fun captureSnapshot(): DeviceEnvironment
}

sealed class FinalResponse {
    data class Success(val text: String, val traceId: String, val source: ResponseSource) : FinalResponse()
    data class Blocked(val reason: String, val traceId: String) : FinalResponse()
    data class Error(val fallbackMessage: String, val traceId: String) : FinalResponse()
}

enum class ResponseSource { OODA_FAST_PATH, ORIGA_DEEP_PATH, SAFE_FALLBACK }

// Extending OutputValidator from ActStep
interface RouterOutputValidator {
    data class FinalValidationResult(
        val isValid: Boolean,
        val reason: String,
        val piiDetected: Boolean = false,
        val hallucinationScore: Float = 0.0f
    )
    suspend fun validateFinalResponse(text: String, env: DeviceEnvironment): FinalValidationResult
}

// Extending AuditLogger
interface RouterAuditLogger {
    fun logSecurityBlock(traceId: String, query: String, reason: String)
    fun logPipelineFailure(traceId: String, cause: Throwable?)
    fun logDeepReasoningSuccess(traceId: String, reason: String)
}

// --- JIT Sandboxing Architecture Enums ---

enum class ConsentLevel { NONE, IMPLICIT, EXPLICIT_BIOMETRIC }
enum class NetworkPolicy { DENIED, INTERNAL_ONLY, EXTERNAL_ALLOWED }
enum class FileSystemScope { INTERNAL_APP, INTERNAL_CACHE, EXTERNAL_ALLOWED }
enum class PiiExposureLevel { NONE, ANONYMIZED, RAW }

data class ToolPermission(
    val toolName: String,
    val requiredConsent: ConsentLevel,
    val networkAccess: NetworkPolicy,
    val fileSystemScope: FileSystemScope,
    val piiExposure: PiiExposureLevel,
    val requiresPqcSignature: Boolean,
    val maxCallsPerSession: Int
)

sealed class AuthorizationResult {
    data class Allowed(val sanitizedCall: com.scypheon.sdk.core.agent.tool.ToolCall, val permission: ToolPermission) : AuthorizationResult()
    data class Denied(val reason: String) : AuthorizationResult()
}

@javax.inject.Singleton
class RouterContract @javax.inject.Inject constructor(
    private val consentManager: com.scypheon.sdk.core.security.ConsentManager,
    private val auditChain: com.scypheon.sdk.core.security.AuditChain,
    private val piiAnonymizer: com.scypheon.sdk.core.telemetry.PIIAnonymizer
) {
    private val permissions = mapOf(
        "clinical_dosage" to ToolPermission(
            toolName = "clinical_dosage",
            requiredConsent = ConsentLevel.IMPLICIT,
            networkAccess = NetworkPolicy.DENIED,
            fileSystemScope = FileSystemScope.INTERNAL_APP,
            piiExposure = PiiExposureLevel.ANONYMIZED,
            requiresPqcSignature = true,
            maxCallsPerSession = 20
        ),
        "web_search" to ToolPermission(
            toolName = "web_search",
            requiredConsent = ConsentLevel.IMPLICIT,
            networkAccess = NetworkPolicy.EXTERNAL_ALLOWED,
            fileSystemScope = FileSystemScope.INTERNAL_CACHE,
            piiExposure = PiiExposureLevel.ANONYMIZED,
            requiresPqcSignature = false,
            maxCallsPerSession = 50
        ),
        "execute_safe_command" to ToolPermission(
            toolName = "execute_safe_command",
            requiredConsent = ConsentLevel.EXPLICIT_BIOMETRIC,
            networkAccess = NetworkPolicy.DENIED,
            fileSystemScope = FileSystemScope.INTERNAL_APP,
            piiExposure = PiiExposureLevel.RAW,
            requiresPqcSignature = true,
            maxCallsPerSession = 5
        )
    )

    suspend fun authorize(toolCall: com.scypheon.sdk.core.agent.tool.ToolCall, sessionContext: SessionContext): AuthorizationResult {
        val perm = permissions[toolCall.toolName] 
            ?: return AuthorizationResult.Denied("Unknown tool: ${toolCall.toolName}")
        
        // 1. Check rate limit
        val callCount = sessionContext.getToolCallCount(toolCall.toolName)
        if (callCount >= perm.maxCallsPerSession) {
            auditChain.logEvent("SECURITY_BLOCK", "Rate limit exceeded for ${toolCall.toolName}")
            return AuthorizationResult.Denied("Rate limit exceeded")
        }
        
        // 2. Check consent
        if (perm.requiredConsent == ConsentLevel.EXPLICIT_BIOMETRIC) {
            val consentResult = consentManager.requestBiometricConsent(
                reason = "Tool '${toolCall.toolName}' requires biometric verification"
            )
            if (!consentResult.granted) {
                return AuthorizationResult.Denied("Biometric consent denied")
            }
        }
        
        // 3. Sanitize arguments (PII protection)
        val sanitizedArgs = if (perm.piiExposure == PiiExposureLevel.ANONYMIZED) {
            toolCall.arguments.mapValues { (_, v) ->
                if (v is String) piiAnonymizer.anonymize(v) else v
            }
        } else {
            toolCall.arguments
        }
        
        return AuthorizationResult.Allowed(
            sanitizedCall = toolCall.copy(arguments = sanitizedArgs),
            permission = perm
        )
    }

    fun getPermission(toolName: String): ToolPermission {
        return permissions[toolName] ?: throw IllegalArgumentException("Unknown tool: $toolName")
    }
}
