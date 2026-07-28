package com.scypheon.sdk.core.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * AuditChain: Cryptographically linked event logger for medical data provenance.
 * [SAFETY & TRUST] Ensures that no medical record can be tampered with without detection.
 */
@Singleton
class AuditChain @Inject constructor(
    private val dao: AuditChainDao,
    private val pqcSigner: PqcAuditSigner
) {
    companion object {
        private val nativeInitMutex = Mutex()
        
        // MUST be Volatile for memory visibility in double-checked locking
        @Volatile
        private var nativeLoaded = false

        private suspend fun ensureNativeLoaded() {
            if (nativeLoaded) return
            nativeInitMutex.withLock {
                if (nativeLoaded) return
                withContext(Dispatchers.IO) {
                    try {
                        val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
                        try {
                            System.loadLibrary("ggml-base")
                            System.loadLibrary("ggml")
                            System.loadLibrary("llama")
                            System.loadLibrary("scypheon_native")
                        } finally {
                            android.os.StrictMode.setThreadPolicy(oldPolicy)
                        }
                        nativeLoaded = true
                        Timber.i("🛡️ [AuditChain] Native libraries loaded successfully.")
                    } catch (e: UnsatisfiedLinkError) {
                        Timber.e(e, "💀 FATAL: Audit Chain native library missing. Integrity compromised.")
                    }
                }
            }
        }
    }

    // Separate Mutex specifically for database write queue (prevents forking)
    private val chainMutex = Mutex()

    private external fun nativeComputeChainHash(previousHash: String, entryData: String): String

    /**
     * @throws IllegalStateException if native fails to load or database write fails.
     * Uses Fail-Safe concept: If it cannot be audited, the operation must fail.
     */
    suspend fun logEvent(actionType: String, payload: String) = withContext(Dispatchers.IO) {
        ensureNativeLoaded()
        
        if (!nativeLoaded) {
            throw IllegalStateException("Audit Chain gagal diinisialisasi. Menolak pencatatan untuk menjaga integritas data medis.")
        }

        // Lock read-hash-write process to ensure Atomicity
        chainMutex.withLock {
            try {
                val lastEntry = dao.getLastEntry()
                val previousHash = lastEntry?.hash ?: "0".repeat(64)
                val timestamp = System.currentTimeMillis()
                
                // Format: actionType|timestamp|payload
                val dataToSign = "$actionType|$timestamp|$payload"
                val newHash = nativeComputeChainHash(previousHash, dataToSign)
                
                // Map event type
                val eventTypeEnum = when (actionType) {
                    "CONSENT_GRANTED", "CONSENT_AUTO_GRANTED" -> AuditableEventType.TOOL_EXECUTION_CRITICAL
                    "CONSENT_DENIED" -> AuditableEventType.SAFETY_VIOLATION_BLOCK
                    "CLINICAL_OVERRIDE" -> AuditableEventType.SAFETY_VIOLATION_BLOCK
                    else -> AuditableEventType.TOOL_EXECUTION_INFO
                }

                // Sign Event using ML-DSA
                val event = AuditEvent(
                    id = newHash,
                    timestamp = timestamp,
                    eventType = eventTypeEnum,
                    payload = payload
                )
                val pqcSigBytes = pqcSigner.signEvent(event)

                val entry = AuditEntry(
                    timestamp = timestamp,
                    actionType = actionType,
                    payload = payload,
                    hash = newHash,
                    previousHash = previousHash,
                    pqcSignature = pqcSigBytes,
                    pqcAlgorithm = pqcSigner.getAlgorithmName()
                )
                
                dao.insert(entry)
                Timber.d("🔒 Audit Chain: Event [$actionType] verified, PQC signed & locked (Hash: ${newHash.take(8)}...)")
            } catch (e: Exception) {
                Timber.e(e, "❌ FATAL Audit Insertion Failure")
                // Propagate error to caller so medical transaction can be rolled back
                throw IllegalStateException("Gagal merekam jejak audit: ${e.message}", e)
            }
        }
    }

    suspend fun verifyChain(): Boolean = withContext(Dispatchers.IO) {
        ensureNativeLoaded()
        if (!nativeLoaded) {
            Timber.e("Verification failed: Native engine missing.")
            return@withContext false
        }
        
        // Lock database during verification process to prevent writes mid-way
        chainMutex.withLock {
            val entries = dao.getAllEntries()
            var currentPrevHash = "0".repeat(64)
            val publicKey = pqcSigner.exportPublicKey()
            
            for (entry in entries) {
                if (entry.previousHash != currentPrevHash) {
                    Timber.w("🚨 Audit Breach: Previous hash mismatch on event ${entry.actionType}")
                    return@withContext false
                }
                
                val dataToSign = "${entry.actionType}|${entry.timestamp}|${entry.payload}"
                val calculatedHash = nativeComputeChainHash(currentPrevHash, dataToSign)
                
                if (entry.hash != calculatedHash) {
                    Timber.w("🚨 Audit Breach: Hash signature invalid on event ${entry.actionType}")
                    return@withContext false
                }

                // Verify PQC Signature if present
                if (entry.pqcSignature != null) {
                    val eventTypeEnum = when (entry.actionType) {
                        "CONSENT_GRANTED", "CONSENT_AUTO_GRANTED" -> AuditableEventType.TOOL_EXECUTION_CRITICAL
                        "CONSENT_DENIED" -> AuditableEventType.SAFETY_VIOLATION_BLOCK
                        "CLINICAL_OVERRIDE" -> AuditableEventType.SAFETY_VIOLATION_BLOCK
                        else -> AuditableEventType.TOOL_EXECUTION_INFO
                    }
                    val event = AuditEvent(
                        id = entry.hash,
                        timestamp = entry.timestamp,
                        eventType = eventTypeEnum,
                        payload = entry.payload
                    )
                    val pqcValid = pqcSigner.verifyEvent(event, entry.pqcSignature, publicKey)
                    if (!pqcValid) {
                        Timber.w("🚨 Audit Breach: PQC ML-DSA signature verification failed on event ${entry.actionType}")
                        return@withContext false
                    }
                }
                
                currentPrevHash = entry.hash
            }
            Timber.i("✅ Audit Chain & PQC Signatures Validated: ${entries.size} blocks intact.")
            true
        }
    }
}
