package com.scypheon.sdk.core.security

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.scypheon.sdk.core.agent.tool.ToolCall
import com.scypheon.sdk.core.agent.tool.ToolResult

@Singleton
class AuditLoggerImpl @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext context: Context) : 
    com.scypheon.sdk.core.agent.RouterAuditLogger, 
    com.scypheon.sdk.core.agent.ooda.AuditLogger {
        
    private val database = Room.databaseBuilder(context, AuditDatabase::class.java, "audit_secure.db")
        .fallbackToDestructiveMigration()
        .build()
    private val dao = database.auditLogDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun logToolExecution(
        executionId: String, 
        toolCall: ToolCall, 
        result: ToolResult, 
        validation: com.scypheon.sdk.core.agent.ooda.OutputValidator.ValidationResult, 
        latencyMs: Long
    ) {
        val payload = """{"executionId":"$executionId","tool":"${toolCall.toolName}","latency":$latencyMs,"success":${result.isSuccess}}"""
        appendLog(executionId, "TOOL_EXECUTION", payload)
    }
    
    override fun logExecutionError(executionId: String, toolName: String, error: Throwable) {
        val payload = """{"tool":"$toolName","error":"${error.message?.replace("\"", "\\\"")}"}"""
        appendLog(executionId, "TOOL_ERROR", payload)
    }

    override fun logPipelineStart(traceId: String, metadata: String) {
        val payload = """{"metadata":"$metadata"}"""
        appendLog(traceId, "PIPELINE_START", payload)
    }

    override fun logPipelineCompletion(traceId: String, success: Boolean, failureReason: String?, latencyMs: Long) {
        val payload = """{"success":$success,"failureReason":"${failureReason?.replace("\"", "\\\"") ?: "none"}","latency":$latencyMs}"""
        appendLog(traceId, "PIPELINE_COMPLETE", payload)
    }

    override fun logSecurityBlock(traceId: String, query: String, reason: String) {
        val payload = """{"reason":"$reason","queryLength":${query.length}}"""
        appendLog(traceId, "SECURITY_BLOCK", payload)
    }

    override fun logPipelineFailure(traceId: String, cause: Throwable?) {
        val payload = """{"error":"${cause?.message?.replace("\"", "\\\"")}"}"""
        appendLog(traceId, "PIPELINE_FAILURE", payload)
    }

    override fun logDeepReasoningSuccess(traceId: String, reason: String) {
        val payload = """{"delegationReason":"$reason"}"""
        appendLog(traceId, "DEEP_REASONING", payload)
    }

    private fun appendLog(traceId: String, eventType: String, payload: String) {
        scope.launch {
            val previousHash = dao.getLastHash() ?: "GENESIS"
            val chainInput = "$previousHash|$traceId|$eventType|$payload|${System.currentTimeMillis()}"
            val newHash = sha256(chainInput)
            dao.insert(AuditLogEntry(traceId = traceId, timestamp = System.currentTimeMillis(), eventType = eventType, payload = payload, chainHash = newHash))
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
