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
    private val dao: AuditChainDao
) {
    companion object {
        private val nativeInitMutex = Mutex()
        
        // HARUS Volatile untuk memory visibility pada double-checked locking
        @Volatile
        private var nativeLoaded = false

        private suspend fun ensureNativeLoaded() {
            if (nativeLoaded) return
            nativeInitMutex.withLock {
                if (nativeLoaded) return
                withContext(Dispatchers.IO) {
                    try {
                        System.loadLibrary("ggml-base")
                        System.loadLibrary("ggml")
                        System.loadLibrary("llama")
                        System.loadLibrary("scypheon-native")
                        nativeLoaded = true
                        Timber.i("🛡️ Audit Chain: Native JNI engine locked and loaded.")
                    } catch (e: UnsatisfiedLinkError) {
                        Timber.e(e, "💀 FATAL: Audit Chain native library missing. Integrity compromised.")
                    }
                }
            }
        }
    }

    // Mutex terpisah khusus untuk antrean penulisan database (mencegah fork)
    private val chainMutex = Mutex()

    private external fun nativeSignEntry(previousHash: String, entryData: String): String

    /**
     * @throws IllegalStateException jika native gagal di-load atau database gagal ditulis.
     * Menggunakan konsep Fail-Safe: Jika tidak bisa diaudit, operasi harus gagal.
     */
    suspend fun logEvent(actionType: String, payload: String) = withContext(Dispatchers.IO) {
        ensureNativeLoaded()
        
        if (!nativeLoaded) {
            throw IllegalStateException("Audit Chain gagal diinisialisasi. Menolak pencatatan untuk menjaga integritas data medis.")
        }

        // Kunci proses read-hash-write agar bersifat Atomic
        chainMutex.withLock {
            try {
                val lastEntry = dao.getLastEntry()
                val previousHash = lastEntry?.hash ?: "0".repeat(64)
                val timestamp = System.currentTimeMillis()
                
                // Format: actionType|timestamp|payload
                val dataToSign = "$actionType|$timestamp|$payload"
                val newHash = nativeSignEntry(previousHash, dataToSign)
                
                val entry = AuditEntry(
                    timestamp = timestamp,
                    actionType = actionType,
                    payload = payload,
                    hash = newHash,
                    previousHash = previousHash
                )
                
                dao.insert(entry)
                Timber.d("🔒 Audit Chain: Event [$actionType] verified & locked (Hash: ${newHash.take(8)}...)")
            } catch (e: Exception) {
                Timber.e(e, "❌ FATAL Audit Insertion Failure")
                // Propagasi error ke pemanggil agar transaksi medis bisa di-rollback
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
        
        // Kunci database selama proses verifikasi agar tidak ada penulisan di tengah jalan
        chainMutex.withLock {
            val entries = dao.getAllEntries()
            var currentPrevHash = "0".repeat(64)
            
            for (entry in entries) {
                if (entry.previousHash != currentPrevHash) {
                    Timber.w("🚨 Audit Breach: Previous hash mismatch on event ${entry.actionType}")
                    return@withContext false
                }
                
                val dataToSign = "${entry.actionType}|${entry.timestamp}|${entry.payload}"
                val calculatedHash = nativeSignEntry(currentPrevHash, dataToSign)
                
                if (entry.hash != calculatedHash) {
                    Timber.w("🚨 Audit Breach: Hash signature invalid on event ${entry.actionType}")
                    return@withContext false
                }
                
                currentPrevHash = entry.hash
            }
            Timber.i("✅ Audit Chain Validated: ${entries.size} blocks intact.")
            true
        }
    }
}
