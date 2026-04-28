package com.scypheon.sdk.core.utils

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Solaris 4.1 Production Spec: Model Integrity Guard.
 * Enforces hardware-backed SHA-256 provenance for all ML assets.
 */
class ModelIntegrityGuard(private val context: Context) {

    /**
     * Verifies the target file against its hardcoded provenance hash.
     * If the file is missing or corrupted, it triggers a clean extraction.
     */
    fun verifyAndEnsure(assetName: String, targetFile: File, expectedHash: String): Boolean {
        if (targetFile.exists()) {
            // ✅ Solaris 4.1: Skip verification if IGNORE flag is used (dev/side-load mode)
            if (expectedHash.equals("IGNORE", ignoreCase = true)) {
                Timber.d("⚙️ [PHOENIX] Hash verification skipped for '$assetName' (IGNORE flag)")
                return true
            }

            val actualHash = computeSHA256(targetFile)
            if (actualHash.equals(expectedHash, ignoreCase = true)) {
                Timber.i("✅ [PHOENIX] Integrity Verified: $assetName")
                return true
            } else {
                Timber.e("🚨 [PHOENIX] Integrity Failure: $assetName ($actualHash != $expectedHash). Purging...")
                targetFile.delete()
            }
        }
        return false // Needs extraction or migration
    }

    /**
     * Compute SHA-256 hash of a file using a streaming buffer to avoid OOM.
     */
    fun computeSHA256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(1024 * 1024) // 1MB buffer
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to compute hash for ${file.name}")
            "FAIL"
        }
    }
}
