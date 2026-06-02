package com.scypheon.sdk.core.provision

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.scypheon.sdk.core.security.AegisVault
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ModelProvisioner handles the lifecycle of AI models on the device.
 * 
 * [v1.5.2-SAR] Supports both public and gated HuggingFace downloads
 * with real-time progress tracking via Android DownloadManager.
 * 
 * Download location: getExternalFilesDir(Downloads)
 * This is automatically scanned by MainViewModel.scanLocalModels()
 */
@Singleton
class ModelProvisioner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: AegisVault
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    // Track active download IDs mapped to model filenames
    private val activeDownloads = mutableMapOf<String, Long>()

    /**
     * Checks if the device has enough free space for the model.
     * @param requiredBytes The expected size of the model in bytes.
     * @return True if there is sufficient space (with 500MB safety buffer).
     */
    fun hasSufficientSpace(requiredBytes: Long): Boolean {
        val stat = StatFs(Environment.getDataDirectory().path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val safetyBuffer = 500 * 1024 * 1024L // 500MB buffer
        return availableBytes > (requiredBytes + safetyBuffer)
    }

    /**
     * Downloads a model from HuggingFace.
     * Works for both public and gated (token-required) models.
     * 
     * @return Download ID from DownloadManager, or -1 on failure
     */
    fun downloadModel(model: ModelMetadata): Long {
        if (!hasSufficientSpace(model.sizeBytes)) {
            Timber.e("📦 [PROVISION] Insufficient storage for ${model.fileName} (need ${model.sizeBytes / 1_000_000} MB)")
            return -1L
        }

        // Check if already downloading
        if (activeDownloads.containsKey(model.fileName)) {
            Timber.w("📦 [PROVISION] ${model.fileName} is already downloading")
            return activeDownloads[model.fileName]!!
        }

        // Check if already exists
        if (isModelOnDisk(model.fileName)) {
            Timber.w("📦 [PROVISION] ${model.fileName} already exists on disk")
            return -1L
        }

        val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
            .setTitle("Downloading: ${model.title}")
            .setDescription("${model.provider} · ${model.quantization} · ${formatSize(model.sizeBytes)}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, model.fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        // Only add auth header for gated models
        if (model.isGated) {
            val token = vault.getHfToken()
            if (token.isNullOrBlank()) {
                Timber.e("📦 [PROVISION] HF token required for gated model: ${model.fileName}")
                return -1L
            }
            request.addRequestHeader("Authorization", "Bearer $token")
        }

        val downloadId = downloadManager.enqueue(request)
        activeDownloads[model.fileName] = downloadId
        Timber.i("📦 [PROVISION] Started download #$downloadId: ${model.title} from ${model.provider}")

        return downloadId
    }

    /**
     * Legacy method for backward compatibility.
     */
    fun downloadGatedModel(modelUrl: String, fileName: String, sizeEstimateBytes: Long): Long {
        val legacyModel = ModelMetadata(
            id = fileName,
            title = fileName,
            description = "",
            sizeBytes = sizeEstimateBytes,
            quantization = "unknown",
            downloadUrl = modelUrl,
            fileName = fileName,
            engineType = if (fileName.endsWith(".gguf")) EngineType.LLAMA_CPP else EngineType.LITE_RT,
            isGated = true // Legacy behavior: assume gated
        )
        return downloadModel(legacyModel)
    }

    /**
     * Query real-time download progress.
     * @return DownloadProgress with bytesDownloaded, totalBytes, and percentage
     */
    fun getDownloadProgress(downloadId: Long): DownloadProgress {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor != null && cursor.moveToFirst()) {
            try {
                val bytesDownloaded = cursor.getLongSafe(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalBytes = cursor.getLongSafe(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val status = cursor.getIntSafe(DownloadManager.COLUMN_STATUS)
                val reason = cursor.getIntSafe(DownloadManager.COLUMN_REASON)

                val percentage = if (totalBytes > 0) {
                    (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f

                return DownloadProgress(
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                    percentage = percentage,
                    status = status,
                    reason = reason
                )
            } finally {
                cursor.close()
            }
        }
        cursor?.close()
        return DownloadProgress(0, 0, 0f, -1, 0)
    }

    fun getCustomDownloadProgress(fileName: String): DownloadProgress? {
        return getProgressForModel(fileName)
    }

    fun resumeDownload(model: ModelMetadata, onProgress: (DownloadProgress) -> Unit) {
        downloadModel(model)
    }

    fun pauseDownload(fileName: String) {
        cancelDownload(fileName)
    }

    fun cancelDownload(fileName: String) {
        val downloadId = activeDownloads[fileName]
        if (downloadId != null) {
            downloadManager.remove(downloadId)
            activeDownloads.remove(fileName)
            Timber.i("📦 [PROVISION] Cancelled download for $fileName")
        }
    }

    fun isModelDownloadingOrPaused(fileName: String): Boolean {
        val downloadId = activeDownloads[fileName] ?: return false
        val progress = getDownloadProgress(downloadId)
        return progress.status == DownloadManager.STATUS_RUNNING || 
               progress.status == DownloadManager.STATUS_PENDING || 
               progress.status == DownloadManager.STATUS_PAUSED
    }

    /**
     * Get download progress for a model by filename.
     */
    fun getProgressForModel(fileName: String): DownloadProgress? {
        val downloadId = activeDownloads[fileName] ?: return null
        return getDownloadProgress(downloadId)
    }

    /**
     * Check if a model is actively being downloaded.
     */
    fun isDownloading(fileName: String): Boolean {
        val downloadId = activeDownloads[fileName] ?: return false
        val progress = getDownloadProgress(downloadId)
        val isActive = progress.status == DownloadManager.STATUS_RUNNING || progress.status == DownloadManager.STATUS_PENDING

        // Clean up completed/failed downloads
        if (!isActive) {
            activeDownloads.remove(fileName)
        }
        return isActive
    }

    /**
     * Clear tracking for a completed download.
     */
    fun clearDownload(fileName: String) {
        activeDownloads.remove(fileName)
    }

    /**
     * Returns the absolute path to a previously downloaded model.
     */
    fun getModelPath(fileName: String): File {
        return File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
    }

    /**
     * Checks if a model file exists on disk.
     */
    fun isModelOnDisk(fileName: String): Boolean {
        return getModelPath(fileName).exists()
    }

    /**
     * Deletes a model file from disk.
     */
    fun deleteModel(fileName: String): Boolean {
        activeDownloads.remove(fileName)
        val file = getModelPath(fileName)
        return if (file.exists()) {
            val deleted = file.delete()
            if (deleted) Timber.i("📦 [PROVISION] Deleted: $fileName")
            deleted
        } else false
    }

    /**
     * Queries the status of a download ID from DownloadManager.
     */
    fun getDownloadStatus(downloadId: Long): Int {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusIndex)
            cursor.close()
            return status
        }
        cursor?.close()
        return -1
    }

    /**
     * Helper to determine engine type based on file extension.
     */
    fun getEngineTypeForFile(fileName: String): EngineType {
        return if (fileName.endsWith(".task") || fileName.endsWith(".litertlm")) {
            EngineType.LITE_RT
        } else {
            EngineType.LLAMA_CPP
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun formatSize(bytes: Long): String {
        val gb = bytes / 1_000_000_000.0
        return if (gb >= 1) "%.1f GB".format(gb) else "%.0f MB".format(bytes / 1_000_000.0)
    }

    private fun Cursor.getLongSafe(column: String): Long {
        val idx = getColumnIndex(column)
        return if (idx >= 0) getLong(idx) else 0L
    }

    private fun Cursor.getIntSafe(column: String): Int {
        val idx = getColumnIndex(column)
        return if (idx >= 0) getInt(idx) else -1
    }

    // ═══════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════

    data class DownloadProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percentage: Float,   // 0.0 to 1.0
        val status: Int,         // DownloadManager.STATUS_*
        val reason: Int          // DownloadManager.ERROR_* or PAUSED_*
    ) {
        val isComplete: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
        val isFailed: Boolean get() = status == DownloadManager.STATUS_FAILED
        val isRunning: Boolean get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING

        fun formatDownloaded(): String {
            val mb = bytesDownloaded / 1_000_000.0
            return if (mb >= 1000) "%.1f GB".format(mb / 1000.0) else "%.0f MB".format(mb)
        }

        fun formatTotal(): String {
            val mb = totalBytes / 1_000_000.0
            return if (mb >= 1000) "%.1f GB".format(mb / 1000.0) else "%.0f MB".format(mb)
        }
    }
}
