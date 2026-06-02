package com.scypheon.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import dagger.hilt.android.EntryPointAccessors
import com.scypheon.app.startup.DatabaseReadySignal
import androidx.work.Configuration

import com.scypheon.app.security.ScypheonIdentityManager
import javax.inject.Inject

@HiltAndroidApp
class ScypheonApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var identityManager: ScypheonIdentityManager

    /**
     * [v1.5.0-SAR] WorkManager Configuration Provider.
     *
     * Required because we disabled the default WorkManagerInitializer in the manifest
     * (tools:node="remove") for Hilt compatibility. Without this, LeakCanary's
     * WorkManagerHeapAnalyzer crashes with IllegalStateException, which triggers
     * repeated System.gc() calls that cause the "Skipped 568 frames" Choreographer drop.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Load SQLCipher native libraries explicitly to prevent UnsatisfiedLinkError
        try {
            System.loadLibrary("sqlcipher")
            Timber.i("SQLCipher native libraries loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load SQLCipher native libraries")
        }

        // Enterprise logging initialization
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // In production, we would plant a tree that sends logs to a secure crash reporter
            // while strictly adhering to the zero-telemetry policy in AndroidManifest.xml
        }

        Timber.i("Scypheon Engine initialized. Version: ${BuildConfig.VERSION_NAME}")

        if (!isMainProcess()) {
            Timber.i("🛰️ [SAR] Sandbox process detected. Skipping heavy initialization.")
            DatabaseReadySignal.markReady()
            return
        }

        // Initialize Ed25519 Identity Key for offline mesh communication asynchronously
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    identityManager.initializeIdentityKey()
                    Timber.i("🔐 ScypheonIdentityManager: Ed25519 Identity Key is ready for mesh network.")
                } else {
                    Timber.w("🔐 ScypheonIdentityManager: Mesh identity requires Android 12+ (API 31).")
                }
            } catch (e: Exception) {
                Timber.e(e, "🔐 ScypheonIdentityManager: Failed to initialize Identity Key")
            }
        }
        if (BuildConfig.DEBUG) {
            // Phase 1: Production Hardening - StrictMode Intervention (Main Process Only)
            // [v1.5.0-SAR] Removed penaltyFlashScreen() — it causes visual disruption
            // during normal EncryptedSharedPreferences access which does unavoidable
            // synchronous File.exists() checks internally.
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
            Timber.i("🛡️ StrictMode monitoring active (Enterprise Policy 1.5)")
        }

        // Master Reset: Removed [v1.0.6-SAR] - Blacklist must be persistent to prevent crash loops on Mali devices

        // Phase 2: Startup Optimization - Asynchronous Dependency Pre-Warming
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    this@ScypheonApplication,
                    DatabaseEntryPoint::class.java
                )
                val db = entryPoint.appDatabase()
                db.query("SELECT 1", null).close()
                
                // [v1.0.6-SAR] Pre-warm SharedPreferences to avoid StrictMode violations on UI thread
                entryPoint.getHardwarePreferences().isMemoryOptimized()
                
                Timber.i("🛡️ Database and Preferences pre-warming completed on background thread")
            } catch (e: Exception) {
                Timber.e(e, "Failed to pre-warm database")
            } finally {
                DatabaseReadySignal.markReady()
            }
        }
    }

    private fun isMainProcess(): Boolean {
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            // Fallback for older APIs
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.find { it.pid == android.os.Process.myPid() }?.processName
        }
        return processName == packageName
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface DatabaseEntryPoint {
        fun appDatabase(): com.scypheon.sdk.core.system.AppDatabase
        fun getHardwarePreferences(): com.scypheon.sdk.core.utils.HardwarePreferences
    }
}
