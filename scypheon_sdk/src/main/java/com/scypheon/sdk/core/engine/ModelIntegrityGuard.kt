package com.scypheon.sdk.core.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelIntegrityGuard @Inject constructor() {
    
    private val verifiedPaths = mutableSetOf<String>()

    /**
     * Verifies SHA-256 of the model file.
     * In Enterprise production, expectedHash should be pulled from a signed manifest.
     */
    suspend fun verifyOrReject(path: String, expectedHash: String?): Boolean = withContext(Dispatchers.IO) {
        if (path in verifiedPaths) return@withContext true
        
        if (expectedHash == null) {
            Log.w("ScypheonSecurity", "⚠️ NO HASH PROVIDED for $path. Security bypass enabled for Dev mode.")
            return@withContext true 
        }

        val actualHash = computeSha256(path)
        if (actualHash.equals(expectedHash, ignoreCase = true)) {
            verifiedPaths.add(path)
            true
        } else {
            Log.e("ScypheonSecurity", "❌ INTEGRITY FAILURE: Hash mismatch for $path. Purging file.")
            File(path).delete()
            false
        }
    }

    private fun computeSha256(filePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        File(filePath).inputStream().use { input ->
            val buffer = ByteArray(65536) // Larger buffer for faster multi-GB hashing
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
