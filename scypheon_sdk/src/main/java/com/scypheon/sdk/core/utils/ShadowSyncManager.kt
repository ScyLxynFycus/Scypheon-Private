package com.scypheon.sdk.core.utils

import android.content.Context
import android.net.ConnectivityManager
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🛰️ ShadowSyncManager: Handles stealth deployment of internal assets.
 * Fetches required models and hides them in obfuscated scoped storage paths.
 */
object ShadowSyncManager {
    private const val MODEL_URL = "https://huggingface.co/litert-community/embeddinggemma-300m/resolve/main/embeddinggemma-300M_seq2048_mixed-precision.tflite"
    private const val TARGET_DIR = ".shm"
    private const val TARGET_FILE = ".gateway_sync.bin"

    /**
     * Ensures the TextEmbedder model is available in the hidden directory.
     * Triggers a silent background download if missing.
     */
    suspend fun ensureSynced(context: Context): Boolean = withContext(Dispatchers.IO) {
        val rootDir = File(context.filesDir, TARGET_DIR)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }

        val destination = File(rootDir, TARGET_FILE)
        // Minimum valid size for embeddinggemma-300M (~186 MB uncompressed)
        val minValidSize = 190_000_000L
        if (destination.exists() && destination.length() >= minValidSize) {
            Timber.d("🛰️ [ShadowSync] Gateway model already synced and valid (${destination.length()} bytes).")
            return@withContext true
        } else if (destination.exists()) {
            Timber.w("🛰️ [ShadowSync] Truncated gateway model detected (${destination.length()} bytes < $minValidSize). Re-downloading...")
            destination.delete()
        }

        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connMgr.isActiveNetworkMetered) {
            Timber.w("🛰️ [ShadowSync] Network is METERED. Aborting automatic download of gateway model (~190MB) to prevent data plan overuse.")
            return@withContext false
        }

        Timber.i("🛰️ [ShadowSync] Triggering stealth gateway sync...")
        return@withContext try {
            downloadModel(destination)
            Timber.i("✅ [ShadowSync] Gateway sync completed successfully.")
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ [ShadowSync] Gateway sync failed.")
            false
        }
    }

    private fun downloadModel(target: File) {
        val url = URL(MODEL_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode}")
            }

            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
