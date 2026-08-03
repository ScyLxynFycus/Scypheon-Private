package com.scypheon.sdk.core.agent.ooda

import com.scypheon.sdk.core.agent.tool.ExecutionContext
import com.scypheon.sdk.core.agent.tool.ExecutionContextFactory
import com.scypheon.sdk.core.agent.tool.ToolMesh
import com.scypheon.sdk.core.agent.tool.ToolCall
import com.scypheon.sdk.core.agent.tool.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import android.os.SystemClock
import java.util.UUID

// --- Supporting Interfaces & Data Classes for ActStep ---

interface OutputValidator {
    data class ValidationResult(
        val isValid: Boolean,
        val reason: String,
        val safeFallbackMessage: String,
        val piiDetected: Boolean = false,
        val hallucinationScore: Float = 0.0f
    )
    suspend fun validate(
        result: ToolResult,
        session: SessionContext,
        environment: DeviceEnvironment
    ): ValidationResult
}

interface AuditLogger {
    fun logToolExecution(
        executionId: String,
        toolCall: ToolCall,
        result: ToolResult,
        validation: OutputValidator.ValidationResult,
        latencyMs: Long
    )
    fun logExecutionError(executionId: String, toolName: String, error: Throwable)
    fun logPipelineStart(traceId: String, metadata: String)
    fun logPipelineCompletion(traceId: String, success: Boolean, failureReason: String?, latencyMs: Long)
}

// --- Core ActStep Implementation ---

data class FastPathResult(
    val skillName: String,
    val toolName: String,
    val result: String,
    val latencyMs: Long,
    val validated: Boolean,
    val requiresDeepReasoning: Boolean = false,
    val enrichedQuery: String = "",
    val auditTraceId: String? = null,
    val fallbackReason: String? = null
)

/**
 * Step 4: ACT
 * Executes the selected tool via the ToolMesh, performs robust output validation,
 * and tracks the execution cryptographically for XAI/Audit purposes.
 */
@Singleton
class ActStep @Inject constructor(
    private val toolMesh: ToolMesh,
    private val outputValidator: OutputValidator,
    private val auditLogger: AuditLogger,
    private val executionContextFactory: ExecutionContextFactory
) {
    companion object {
        private const val TOOL_EXECUTION_TIMEOUT_MS = 5000L
    }

    suspend fun execute(
        decision: Decision,
        session: SessionContext,
        environment: DeviceEnvironment,
        orientation: Orientation
    ): FastPathResult = withContext(Dispatchers.IO) {
        val startTime = SystemClock.elapsedRealtime()
        val executionId = UUID.randomUUID().toString()

        if (orientation.requiresDeepReasoning) {
            Timber.i("🚀 [OODA_ACT] Delegating to ORIGA due to complexity.")
            return@withContext FastPathResult(
                skillName = orientation.selectedSkill.type.name,
                toolName = "ORIGA_DELEGATION",
                result = "DELEGATION_REQUIRED",
                latencyMs = 0,
                validated = true,
                requiresDeepReasoning = true,
                enrichedQuery = orientation.refinedQuery,
                auditTraceId = executionId
            )
        }

        Timber.d("🚀 [OODA_ACT] Executing tool: ${decision.toolName} | Trace: $executionId")

        try {
            val execContext = executionContextFactory.create(
                sessionId = session.id,
                environment = environment,
                constraint = orientation.environmentConstraint
            )

            val toolCall = ToolCall(
                toolName = decision.toolName,
                arguments = decision.parameters
            )

            val results = withTimeout(TOOL_EXECUTION_TIMEOUT_MS) {
                toolMesh.dispatch(listOf(toolCall), execContext)
            }

            val execResult = results.firstOrNull()
                ?: ToolResult.Error("Empty execution result", null, 0)

            val validation = outputValidator.validate(execResult, session, environment)

            auditLogger.logToolExecution(
                executionId = executionId,
                toolCall = toolCall,
                result = execResult,
                validation = validation,
                latencyMs = SystemClock.elapsedRealtime() - startTime
            )

            val elapsed = SystemClock.elapsedRealtime() - startTime

            if (!validation.isValid) {
                Timber.w("⚠️ [OODA_ACT] Output validation failed: ${validation.reason}")
                return@withContext FastPathResult(
                    skillName = orientation.selectedSkill.type.name,
                    toolName = decision.toolName,
                    result = validation.safeFallbackMessage,
                    latencyMs = elapsed,
                    validated = false,
                    auditTraceId = executionId,
                    fallbackReason = validation.reason
                )
            }

            val resultData = when (execResult) {
                is ToolResult.Success -> execResult.data?.toString()
                is ToolResult.Fallback -> execResult.data?.toString()
                is ToolResult.Error -> "Error: ${execResult.reason}"
                is ToolResult.AwaitingApproval -> "[AWAITING_APPROVAL] ${execResult.toolName}: ${execResult.reason}"
            }

            Timber.i("✅ [OODA_ACT] Executed & validated in ${elapsed}ms | Trace: $executionId")
            FastPathResult(
                skillName = orientation.selectedSkill.type.name,
                toolName = decision.toolName,
                result = resultData ?: "Success",
                latencyMs = elapsed,
                validated = true,
                auditTraceId = executionId
            )

        } catch (e: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - startTime
            Timber.e(e, "❌ [OODA_ACT] Execution failed: ${decision.toolName} | Trace: $executionId")
            auditLogger.logExecutionError(executionId, decision.toolName, e)
            
            FastPathResult(
                skillName = orientation.selectedSkill.type.name,
                toolName = decision.toolName,
                result = "Execution failed. Falling back to safe response.",
                latencyMs = elapsed,
                validated = false,
                auditTraceId = executionId,
                fallbackReason = e.message ?: "Unknown error"
            )
        }
    }
}
