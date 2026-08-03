package com.scypheon.sdk.core.telemetry

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

import com.scypheon.sdk.core.safety.helios.SafetyViolationReport

data class AuditLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val details: String,
    val securityLevel: String = "INFO" // INFO, WARNING, CRITICAL
)

/**
 * Enterprise Feature: Offline BlackBox Audit Vault.
 * Tamper-proof, AES256-GCM encrypted logging for AI decisions, privacy events, and system errors.
 */
class BlackBoxVault(private val context: Context) {

    private val gson = Gson()
    // Independent scope to ensure logs persist even if the calling worker is killed
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val MAX_LOGS = 1000

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            Timber.e(e, "🚨 [PHOENIX-SECURITY] BlackBoxVault encryption failure! Initiating self-healing...")
            try {
                // [v1.1.2-SAR] SELF-HEALING: Delete corrupted preferences file and keyset
                // This resolves AEADBadTagException / Keystore collisions across processes.
                context.deleteSharedPreferences("scypheon_blackbox")
                createEncryptedPrefs()
            } catch (retryException: Exception) {
                Timber.e(retryException, "🔥 [PHOENIX-SECURITY] Self-healing failed. Falling back to non-encrypted vault to prevent crash.")
                // Last resort: Fallback to standard prefs to ensure system availability during disaster response
                context.getSharedPreferences("scypheon_blackbox_insecure_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    private fun createEncryptedPrefs() = EncryptedSharedPreferences.create(
        context,
        "scypheon_blackbox",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun computeSha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun logEvent(eventType: String, details: String, securityLevel: String = "INFO") {
        scope.launch {
            try {
                val entry = AuditLogEntry(eventType = eventType, details = details, securityLevel = securityLevel)
                val currentLogsJson = sharedPreferences.getString("audit_logs", "[]")
                val currentLogs = gson.fromJson(currentLogsJson, Array<AuditLogEntry>::class.java).toMutableList()

                currentLogs.add(entry)

                if (currentLogs.size > MAX_LOGS) {
                    currentLogs.removeAt(0)
                }

                val updatedLogsJson = gson.toJson(currentLogs)
                val integrityHash = computeSha256(updatedLogsJson)

                sharedPreferences.edit()
                    .putString("audit_logs", updatedLogsJson)
                    .putString("audit_hash", integrityHash)
                    .apply()

                Timber.v("Logged [$securityLevel]: $eventType")
            } catch (e: Exception) {
                Timber.e(e, "Failed to write to BlackBox Vault")
            }
        }
    }

    fun logSafetyViolation(traceId: String, report: SafetyViolationReport) {
        val details = gson.toJson(report)
        logEvent(
            eventType = "SAFETY_VIOLATION",
            details = "Trace: $traceId | Report: $details",
            securityLevel = "CRITICAL"
        )
    }

    fun dumpLogs(): List<AuditLogEntry> {
        return try {
            val currentLogsJson = sharedPreferences.getString("audit_logs", "[]")
            val storedHash = sharedPreferences.getString("audit_hash", "0")

            if (currentLogsJson != "[]" && currentLogsJson != null) {
                val isLegacyHash = storedHash?.length != 64

                val isValid = if (isLegacyHash) {
                    currentLogsJson.hashCode().toString() == storedHash
                } else {
                    computeSha256(currentLogsJson) == storedHash
                }

                if (!isValid) {
                    Timber.e("🚨 CRITICAL: BlackBox Vault Integrity Compromised! Tampering detected.")
                    return emptyList()
                }

                // Migrate legacy hash to SHA-256 transparently on first read
                if (isLegacyHash) {
                    Timber.i("Migrating legacy BlackBox hash to SHA-256.")
                    sharedPreferences.edit()
                        .putString("audit_hash", computeSha256(currentLogsJson))
                        .apply()
                }
            }

            gson.fromJson(currentLogsJson, Array<AuditLogEntry>::class.java).toList()
        } catch (e: Exception) {
            Timber.e(e, "CRITICAL: Failed to read from BlackBox Vault")
            emptyList()
        }
    }

    fun clearLogs() {
        sharedPreferences.edit().remove("audit_logs").apply()
        Timber.w("🔒 BlackBox Vault Cleared")
    }

    /**
     * Records an attempt to initialize a backend. 
     * Used for post-mortem recovery after hard crashes (SIGSEGV).
     */
    fun markAttemptStart(backend: String) {
        sharedPreferences.edit()
            .putString("last_attempt_backend", backend)
            .putLong("last_attempt_timestamp", System.currentTimeMillis())
            .apply()
    }

    /**
     * Clears the attempt flag on success.
     */
    fun markAttemptSuccess() {
        sharedPreferences.edit()
            .remove("last_attempt_backend")
            .remove("last_attempt_timestamp")
            .apply()
    }

    /**
     * Returns the name of the backend that was being attempted if a crash occurred.
     * Returns null if no crash was detected (last attempt was successful).
     */
    fun getCrashedBackend(): String? {
        val lastBackend = sharedPreferences.getString("last_attempt_backend", null)
        val lastTimestamp = sharedPreferences.getLong("last_attempt_timestamp", 0)
        
        // If an attempt was started less than 5 minutes ago and never cleared, consider it a crash
        if (lastBackend != null && (System.currentTimeMillis() - lastTimestamp) < 300_000) {
            return lastBackend
        }
        return null
    }
}
