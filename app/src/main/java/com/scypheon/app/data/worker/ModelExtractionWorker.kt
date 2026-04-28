package com.scypheon.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.scypheon.sdk.core.utils.AssetExtractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Extracts AI models from the APK's assets folder to internal storage.
 * Using WorkManager prevents the app from being killed by the OS during heavy
 * I/O operations on the main thread or a detached coroutine, ensuring the setup completes.
 */
@HiltWorker
class ModelExtractionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.i("Starting WorkManager Model Extraction...")
            AssetExtractor.extractModels(applicationContext)
            Timber.i("Model Extraction completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "WorkManager Model Extraction failed")
            Result.retry()
        }
    }
}
