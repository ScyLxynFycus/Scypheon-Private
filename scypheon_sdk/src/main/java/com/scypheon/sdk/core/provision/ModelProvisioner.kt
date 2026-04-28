package com.scypheon.sdk.core.provision

import android.app.DownloadManager
import android.content.Context
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
 * ModelProvisioner handles the lifecycle of AI models on the device,
 * including storage capacity checks and gated downloads from Hugging Face.
 */
@Singleton
class ModelProvisioner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: AegisVault
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

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
     * Triggers a download for a gated Hugging Face model.
     * Requires the HF_TOKEN to be set in AegisVault.
     */
    fun downloadGatedModel(modelUrl: String, fileName: String, sizeEstimateBytes: Long): Long {
        if (!hasSufficientSpace(sizeEstimateBytes)) {
            Timber.e("Insufficient storage to download model: $fileName")
            return -1L
        }

        val token = vault.getHfToken()
        if (token == null) {
            Timber.e("HF_TOKEN missing from AegisVault. Cannot download gated model.")
            return -1L
        }

        val request = DownloadManager.Request(Uri.parse(modelUrl))
            .setTitle("Downloading Scypheon Model: $fileName")
            .setDescription("Gemma 4 Elite Pro Model Data")
            .addRequestHeader("Authorization", "Bearer $token")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        return downloadManager.enqueue(request)
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
        val file = getModelPath(fileName)
        return if (file.exists()) file.delete() else false
    }

    /**
     * Queries the status of a download ID from DownloadManager.
     * Returns: DownloadManager.STATUS_SUCCESSFUL, STATUS_RUNNING, etc. or -1 if not found.
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
}
