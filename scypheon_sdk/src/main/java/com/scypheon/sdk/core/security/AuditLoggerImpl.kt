package com.scypheon.sdk.core.security

import android.content.Context
import androidx.room.Room
import com.scypheon.sdk.core.agent.ooda.AuditLogger
import com.scypheon.sdk.core.agent.tool.ToolCall
import com.scypheon.sdk.core.agent.tool.ToolResult
import com.scypheon.sdk.core.agent.ooda.OutputValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
<<<<<<< Updated upstream
import com.scypheon.sdk.core.agent.tool.ToolCall
import com.scypheon.sdk.core.agent.tool.ToolResult

=======

/**
 * AuditLoggerImpl: Enterprise-grade tamper-evident audit trail.
 * Uses a Room database with a SHA-256 hash chain to ensure log integrity.
 */
>>>>>>> Stashed changes
@Singleton
class AuditLoggerImpl @Inject constructor(
    @ApplicationContext context: Context
) : AuditLogger, com.scypheon.sdk.core.agent.RouterAuditLogger {
    
    private val database = Room.databaseBuilder(
        context,
        AuditDatabase::class.java,
        "audit_secure.db"
    ).fallbackToDestructiveMigration().build()
    
    private val dao = database.auditLogDao()
    
    // Dedicated scope for audit operations (never blocks caller)
    private val auditScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var lastHash: String = "GENESIS_BLOCK"

    override fun logToolExecution(
        executionId: String,
        toolCall: ToolCall,
        result: ToolResult,
        validation: OutputValidator.ValidationResult,
        latencyMs: Long
    ) {
        val payload = buildString {
            append("tool=${toolCall.toolName} | ")
            append("status=${if (result is ToolResult.Success) "SUCCESS" else "FAILED"} | ")
            append("isValid=${validation.isValid} | ")
            append("latency=${latencyMs}ms")
        }
        appendLog(executionId, "TOOL_EXECUTION", payload)
    }

    override fun logSecurityBlock(traceId: String, query: String, reason: String) {
        appendLog(traceId, "SECURITY_BLOCK", "reason=$reason | query=$query")
    }

    override fun logPipelineFailure(traceId: String, cause: Throwable?) {
        appendLog(traceId, "PIPELINE_FAILURE", cause?.message ?: "Unknown error")
    }

    override fun logDeepReasoningSuccess(traceId: String, reason: String) {
        appendLog(traceId, "DEEP_REASONING", reason)
    }

    override fun logPipelineStart(traceId: String, metadata: String) {
        appendLog(traceId, "PIPELINE_START", metadata)
    }

    override fun logPipelineCompletion(traceId: String, success: Boolean, failureReason: String?, latencyMs: Long) {
        appendLog(traceId, "PIPELINE_COMPLETE", "success=$success | reason=$failureReason | latency=${latencyMs}ms")
    }

    override fun logDegradation(traceId: String, category: String, reason: String) {
        appendLog(traceId, "SYSTEM_DEGRADATION", "category=$category | reason=$reason")
    }

    override fun logExecutionError(executionId: String, toolName: String, error: Throwable) {
        appendLog(executionId, "TOOL_ERROR", "tool=$toolName | error=${error.message}")
    }

    private fun appendLog(traceId: String, eventType: String, payload: String) {
        auditScope.launch {
            try {
                val previousHash = dao.getLastHash() ?: lastHash
                val timestamp = System.currentTimeMillis()
                val chainInput = "$previousHash|$traceId|$eventType|$payload|$timestamp"
                val newHash = sha256(chainInput)
                lastHash = newHash
                
                dao.insert(AuditLogEntry(
                    id = UUID.randomUUID().toString(),
                    traceId = traceId,
                    timestamp = timestamp,
                    eventType = eventType,
                    payload = payload,
                    chainHash = newHash
                ))
            } catch (e: Exception) {
                Timber.e(e, "AuditLogger: Failed to write audit log to database")
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
