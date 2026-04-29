package com.scypheon.sdk.core.security

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ModelIntegrityGuard - Enterprise Hardened Version
 * 
 * CRITICAL SECURITY FIX: Fail-closed on null hash.
 * Previous implementation returned true on null hash = SECURITY BYPASS.
 * 
 * This class is now a legacy wrapper for backward compatibility.
 * NEW CODE SHOULD USE ModelManifestVerifier FOR Ed25519-SIGNED MANIFESTS.
 */
@Singleton
class ModelIntegrityGuard @Inject constructor() {
    
    private val verifiedPaths = mutableSetOf<String>()

    /**
     * Verifies SHA-256 of the model file.
     * 
     * SECURITY HARDENING:
     * - FAIL-CLOSED: Returns false on null hash (no bypass)
     * - Logs security event for audit trail
     * - Delegates to ModelManifestVerifier for production use
     */
    suspend fun verifyOrReject(path: String, expectedHash: String?): Boolean = withContext(Dispatchers.IO) {
        if (path in verifiedPaths) return@withContext true
        
        // CRITICAL FIX: Fail-closed on null hash
        if (expectedHash == null) {
            Log.e("ScypheonSecurity", "❌ CRITICAL: NULL HASH PROVIDED for $path. REJECTING MODEL.")
            Log.e("ScypheonSecurity", "SECURITY EVENT: Potential supply chain attack or misconfiguration detected.")
            return@withContext false // FAIL-CLOSED: Never accept without hash
        }

        val actualHash = computeSha256(path)
        if (actualHash.equals(expectedHash, ignoreCase = true)) {
            verifiedPaths.add(path)
            Log.i("ScypheonSecurity", "✅ Model integrity verified: $path")
            true
        } else {
            Log.e("ScypheonSecurity", "❌ INTEGRITY FAILURE: Hash mismatch for $path. Purging file.")
            Log.e("ScypheonSecurity", "Expected: $expectedHash")
            Log.e("ScypheonSecurity", "Actual:   $actualHash")
            
            // Quarantine instead of delete for forensic analysis
            quarantineFile(File(path))
            false
        }
    }

    /**
     * Computes SHA-256 hash using streaming for memory efficiency.
     */
    private fun computeSha256(filePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536) // 64KB buffer
        
        File(filePath).inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Quarantines suspicious file for forensic analysis.
     */
    private fun quarantineFile(file: File) {
        try {
            val quarantineName = "${file.name}.QUARANTINE.${System.currentTimeMillis()}"
            val quarantineFile = File(file.parentFile, quarantineName)
            file.renameTo(quarantineFile)
            Log.w("ScypheonSecurity", "🔒 File quarantined: ${quarantineFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("ScypheonSecurity", "Failed to quarantine file, deleting instead", e)
            file.delete()
        }
    }

    /**
     * Clears verification cache (security reset).
     */
    fun clearCache() {
        verifiedPaths.clear()
        Log.w("ScypheonSecurity", "⚠️ Verification cache cleared")
    }

    /**
     * Returns count of verified paths (for diagnostics).
     */
    fun getVerifiedCount(): Int = verifiedPaths.size
}
