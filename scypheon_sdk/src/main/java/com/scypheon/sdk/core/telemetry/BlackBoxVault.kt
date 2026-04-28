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
class BlackBoxVault(context: Context) {

    private val gson = Gson()
    // Independent scope to ensure logs persist even if the calling worker is killed
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val MAX_LOGS = 1000

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "scypheon_blackbox",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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
                val integrityHash = updatedLogsJson.hashCode().toString()

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

    fun dumpLogs(): List<AuditLogEntry> {
        return try {
            val currentLogsJson = sharedPreferences.getString("audit_logs", "[]")
            val storedHash = sharedPreferences.getString("audit_hash", "0")

            // Validate Tamper Evidence
            if (currentLogsJson != "[]" && currentLogsJson.hashCode().toString() != storedHash) {
                Timber.e("🚨 CRITICAL: BlackBox Vault Integrity Compromised! Tampering detected.")
                return emptyList()
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
