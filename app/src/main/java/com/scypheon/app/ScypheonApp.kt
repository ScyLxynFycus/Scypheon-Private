package com.scypheon.app

import android.app.Application
import com.scypheon.app.BuildConfig
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.scypheon.app.data.worker.TelemetrySyncWorker
import com.scypheon.app.telemetry.BlackBoxLogger
import android.os.StrictMode
import com.scypheon.sdk.core.utils.SolarisTelemetry

import com.scypheon.sdk.core.utils.NativeLibraryLoader

@HiltAndroidApp
class ScypheonApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // SAR Refinement: Load native libraries in main process
        NativeLibraryLoader.loadSafely()

        // Initialize dual-logging: Logcat (Debug) and BlackBox (Offline Encrypted Telemetry)
        Timber.plant(Timber.DebugTree())
        Timber.plant(BlackBoxLogger(this))

        // 🛡️ [SAR] Phase 3: StrictMode FD Leak Detection
        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        // 🛡️ SOLARIS TELEMETRY: Async flusher for production audit
        SolarisTelemetry.init(this)

        Timber.i("Scypheon Enterprise Host App Initialized")
        setupPeriodicWork()
    }

    private fun setupPeriodicWork() {
        val syncWorkRequest = PeriodicWorkRequestBuilder<TelemetrySyncWorker>(
            15, TimeUnit.MINUTES // Minimum interval allowed by WorkManager
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TelemetrySync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest
        )

        // Model extraction is handled synchronously via ScypheonRepository during the initial
        // splash screen / readiness phase. We remove the async WorkManager trigger here
        // to prevent concurrent I/O file corruption on the models.
    }
}
