package com.scypheon.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Enterprise WorkManager Task.
 * In a real-world enterprise scenario, this worker would securely upload the
 * BlackBox audit logs to a remote, encrypted SIEM (Security Information and Event Management) server
 * when the device has unmetered Wi-Fi. For the offline private app, it simulates this process.
 */
@HiltWorker
class TelemetrySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val blackBoxVault: BlackBoxVault
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val logs = blackBoxVault.dumpLogs()
            if (logs.isEmpty()) {
                Timber.d("No logs to sync.")
                return@withContext Result.success()
            }

            Timber.i("Simulating encrypted telemetry sync of ${logs.size} logs to Enterprise SIEM...")
            // Simulated network delay
            kotlinx.coroutines.delay(2000)

            // Once synced, clear local vault
            blackBoxVault.clearLogs()
            Timber.i("Telemetry sync successful.")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync telemetry.")
            Result.retry()
        }
    }
}
