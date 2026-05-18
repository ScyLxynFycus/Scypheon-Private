package com.scypheon.sdk.core.observability

import com.scypheon.sdk.core.telemetry.BlackBoxVault
import com.scypheon.sdk.core.telemetry.TelemetryDao
import com.scypheon.sdk.core.telemetry.TelemetryEvent
import com.scypheon.sdk.core.annotations.SafetyCritical
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * ObservabilityManager: Unified Telemetry & Audit Orchestrator.
 * 
 * MANDATE:
 * - Synchronizes non-critical telemetry to SQLite.
 * - Routes safety-critical events to the encrypted BlackBoxVault.
 * - Provides clinical-grade observability for medical deployments.
 */
@SafetyCritical
@Singleton
class ObservabilityManager @Inject constructor(
    private val blackBox: BlackBoxVault,
    private val telemetryDao: TelemetryDao
) {

    enum class Severity { INFO, WARNING, CRITICAL, FATAL }

    /**
     * Logs an event with automatic routing based on severity.
     */
    suspend fun logEvent(
        type: String,
        payload: String,
        severity: Severity = Severity.INFO,
        isAudit: Boolean = false
    ) {
        val eventId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // 1. Mandatory Audit Trail for non-repudiation
        if (isAudit || severity >= Severity.WARNING) {
            blackBox.logEvent(
                eventType = type,
                details = payload,
                securityLevel = severity.name
            )
        }

        // 2. Telemetry Persistence for later sync
        try {
            telemetryDao.insert(
                TelemetryEvent(
                    eventId = eventId,
                    type = type,
                    payload = payload,
                    timestamp = timestamp,
                    synced = false
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist telemetry event")
        }

        // 3. Logcat for developer visibility
        when (severity) {
            Severity.INFO -> Timber.i("[$type] $payload")
            Severity.WARNING -> Timber.w("[$type] $payload")
            Severity.CRITICAL, Severity.FATAL -> Timber.e("🚨 [$type] $payload")
        }
    }

    /**
     * Specialized logging for clinical events.
     */
    suspend fun logClinicalDecision(
        agent: String,
        decision: String,
        groundingSuccess: Boolean,
        reasoning: String
    ) {
        val severity = if (groundingSuccess) Severity.INFO else Severity.CRITICAL
        val payload = "Agent: $agent | Decision: $decision | Grounding: $groundingSuccess | Reasoning: $reasoning"
        
        logEvent(
            type = "CLINICAL_DECISION",
            payload = payload,
            severity = severity,
            isAudit = true
        )
    }

    /**
     * Reports hardware anomalies or inference hangs.
     */
    suspend fun reportSystemAnomaly(component: String, detail: String) {
        logEvent(
            type = "SYSTEM_ANOMALY",
            payload = "Component: $component | Detail: $detail",
            severity = Severity.CRITICAL,
            isAudit = true
        )
    }
}
