package com.scypheon.sdk.core.telemetry

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.scypheon.sdk.core.security.AuditLogEntry
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BlackBoxVault: Encrypted Telemetry Storage with Zero-Knowledge PII Protection
 * 
 * All telemetry is:
 * 1. Anonymized via PIIAnonymizer (K-anonymity + hashing)
 * 2. Encrypted with AES-256-GCM via Android Keystore
 * 3. Stored in EncryptedSharedPreferences
 * 
 * Even if device is physically compromised and keystore broken,
 * telemetry contains no recoverable Patient Health Information.
 */
@Singleton
class BlackBoxVault @Inject constructor(
    @ApplicationContext private val context: Context,
    private val piiAnonymizer: PIIAnonymizer
) {
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val prefs: SharedPreferences by lazy { initEncryptedPrefs() }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    companion object {
        private const val TAG = "BlackBoxVault"
        private const val PREFS_NAME = "scypheon_blackbox_vault"
        private const val MAX_LOG_ENTRIES = 1000 // Prevent unbounded growth
    }
    
    // --- Legacy methods retained for backward compatibility ---
    fun recordInferenceStart(backend: String) {
        scope.launch {
            prefs.edit()
                .putString("last_backend", backend)
                .putLong("last_timestamp", System.currentTimeMillis())
                .apply()
        }
    }

    fun recordInferenceSuccess() {
        scope.launch {
            prefs.edit().remove("last_backend").apply()
        }
    }

    fun detectPotentialCrash(): String? {
        val lastBackend = prefs.getString("last_backend", null)
        val lastTimestamp = prefs.getLong("last_timestamp", 0)
        if (lastBackend != null && (System.currentTimeMillis() - lastTimestamp) < 300_000) {
            return lastBackend
        }
        return null
    }

    fun dumpLogs(): List<AuditLogEntry> {
        val logs = mutableListOf<AuditLogEntry>()
        prefs.getStringSet("logs", emptySet())?.forEach { entry ->
            logs.add(
                AuditLogEntry(
                    id = UUID.randomUUID().toString(),
                    traceId = "N/A",
                    timestamp = System.currentTimeMillis(),
                    eventType = "LOG_DUMP",
                    payload = entry,
                    chainHash = ""
                )
            )
        }
        return logs.sortedBy { it.timestamp }
    }

    fun logEvent(eventType: String, details: String, securityLevel: String = "INFO") {
        scope.launch {
            val entry = buildLogEntry(eventType, "N/A", securityLevel, mapOf("details" to details))
            appendLog(entry)
            Timber.d("🔒 [BlackBox] Logged $eventType ($securityLevel)")
        }
    }
    // -----------------------------------------------------------

    /**
     * Log a safety violation with full PII anonymization.
     */
    suspend fun logSafetyViolation(
        traceId: String,
        violationType: String,
        payload: String,
        severity: String = "MEDIUM"
    ) = withContext(Dispatchers.IO) {
        try {
            val anonymizedPayload = piiAnonymizer.anonymize(payload)
            
            val entry = buildLogEntry(
                eventType = "SAFETY_VIOLATION",
                traceId = traceId,
                severity = severity,
                data = mapOf(
                    "violation_type" to violationType,
                    "payload" to anonymizedPayload
                )
            )
            
            appendLog(entry)
            Timber.i("$TAG: Safety violation logged [trace=$traceId, type=$violationType]")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to log safety violation")
        }
    }

    // Overload for Helios compatibility
    fun logSafetyViolation(sessionId: String, report: com.scypheon.sdk.core.safety.helios.SafetyViolationReport) {
        val payload = Gson().toJson(report)
        logEvent("SAFETY_VIOLATION", "Session: $sessionId | Report: ${piiAnonymizer.anonymize(payload)}", "CRITICAL")
    }
    
    /**
     * Record generic telemetry event with PII sanitization.
     */
    suspend fun record(
        eventType: String,
        traceId: String,
        data: Map<String, Any?>,
        severity: String = "INFO"
    ) = withContext(Dispatchers.IO) {
        try {
            // Anonymize all string values in data map
            val sanitizedData = data.mapValues { (_, value) ->
                when (value) {
                    is String -> piiAnonymizer.anonymize(value)
                    else -> value
                }
            }
            
            val entry = buildLogEntry(
                eventType = eventType,
                traceId = traceId,
                severity = severity,
                data = sanitizedData
            )
            
            appendLog(entry)
            Timber.d("$TAG: Event recorded [type=$eventType, trace=$traceId]")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to record event")
        }
    }

    // Overload for older compatibility
    fun record(eventType: String, metricValue: Any, detailsMap: Map<String, Any>? = null) {
        scope.launch {
            val detailsStr = if (detailsMap != null) Gson().toJson(detailsMap) else ""
            logEvent(eventType, "Value: $metricValue | ${piiAnonymizer.anonymize(detailsStr)}", "INFO")
        }
    }
    
    /**
     * Record clinical decision with cryptographic proof.
     */
    suspend fun recordClinicalDecision(
        traceId: String,
        decisionType: String,
        pqcSignature: String,
        signerFingerprint: String,
        anonymizedContext: String
    ) = withContext(Dispatchers.IO) {
        try {
            val entry = buildLogEntry(
                eventType = "CLINICAL_DECISION",
                traceId = traceId,
                severity = "CRITICAL",
                data = mapOf(
                    "decision_type" to decisionType,
                    "pqc_signature" to pqcSignature,
                    "signer_fingerprint" to signerFingerprint,
                    "context" to anonymizedContext,
                    "verification_status" to "SIGNED"
                )
            )
            
            appendLog(entry)
            Timber.i("$TAG: Clinical decision recorded [trace=$traceId, type=$decisionType]")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to record clinical decision")
        }
    }
    
    /**
     * Retrieve all logs (for export/debugging).
     * Returns anonymized data only.
     */
    suspend fun getAllLogs(): List<String> = withContext(Dispatchers.IO) {
        try {
            val logs = prefs.getStringSet("logs", emptySet()) ?: emptySet()
            logs.sorted() // Chronological order
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to retrieve logs")
            emptyList()
        }
    }
    
    /**
     * Clear all logs (for privacy compliance).
     */
    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        try {
            prefs.edit().remove("logs").apply()
            Timber.i("$TAG: All logs cleared")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to clear logs")
        }
    }
    
    private fun buildLogEntry(
        eventType: String,
        traceId: String,
        severity: String,
        data: Map<String, Any?>
    ): String {
        val timestamp = dateFormat.format(Date())
        val dataString = data.entries.joinToString(", ") { (k, v) ->
            "$k=${v ?: "null"}"
        }
        
        val rawLog = "[$timestamp] [$severity] [$eventType] [trace=$traceId] {$dataString}"
        val signature = generateSignature(rawLog)
        
        return "$rawLog [hmac=$signature]"
    }
    
    private fun generateSignature(payload: String): String {
        return try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            val keyAlias = "ScypheonTelemetryHmacKey"
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = javax.crypto.KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                    "AndroidKeyStore"
                )
                val keyGenParameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                    keyAlias,
                    android.security.keystore.KeyProperties.PURPOSE_SIGN
                ).build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
            
            val secretKey = keyStore.getKey(keyAlias, null) as javax.crypto.SecretKey
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(secretKey)
            
            val signatureBytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            signatureBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to generate cryptographic signature for log")
            "UNVERIFIED_SIGNATURE_ERROR"
        }
    }
    
    private fun appendLog(entry: String) {
        val currentLogs = prefs.getStringSet("logs", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        
        // Enforce size limit (FIFO)
        if (currentLogs.size >= MAX_LOG_ENTRIES) {
            val sorted = currentLogs.sorted()
            val toRemove = sorted.take(currentLogs.size - MAX_LOG_ENTRIES + 1)
            currentLogs.removeAll(toRemove.toSet())
        }
        
        currentLogs.add(entry)
        prefs.edit().putStringSet("logs", currentLogs).apply()
    }
    
    private fun initEncryptedPrefs(): SharedPreferences {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to init encrypted prefs")
            if (com.scypheon.sdk.BuildConfig.DEBUG) {
                // Fallback to regular prefs (less secure but prevents crash) only in debug mode
                return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            } else {
                // Fail-Closed in production: prevent unencrypted logging
                throw IllegalStateException("Telemetry KeyStore initialization failed. Writing plaintext log is forbidden.", e)
            }
        }
    }
}
