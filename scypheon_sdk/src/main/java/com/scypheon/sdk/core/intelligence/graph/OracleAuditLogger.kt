package com.scypheon.sdk.core.intelligence.graph

import com.scypheon.sdk.core.telemetry.BlackBoxVault
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OracleAuditLogger (Enterprise Observability):
 * Records every investigative step taken by the Oracle.
 * Ensures the system is auditable and transparent for humanitarian authorities.
 */
@Singleton
class OracleAuditLogger @Inject constructor(
    private val blackBox: BlackBoxVault
) {
    suspend fun logInvestigation(
        sessionId: String, 
        step: String, 
        findingsCount: Int, 
        performanceMs: Long
    ) {
        val auditPayload = """
            [ORACLE_AUDIT]
            Session: $sessionId
            Step: $step
            Findings: $findingsCount
            Latency: ${performanceMs}ms
            Timestamp: ${System.currentTimeMillis()}
        """.trimIndent()
        
        // Corrected method name: recordEvent -> logEvent
        blackBox.logEvent("ORACLE_AUDIT", auditPayload)
        Timber.d("📋 [AUDIT] Oracle step recorded: $step ($performanceMs ms)")
    }
}
