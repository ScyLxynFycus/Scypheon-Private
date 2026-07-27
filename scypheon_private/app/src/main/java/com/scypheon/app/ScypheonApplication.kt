package com.scypheon.app

import android.app.Application
import android.os.StrictMode
import com.scypheon.app.security.ScypheonIdentityManager
import com.scypheon.app.startup.DatabaseReadySignal
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ScypheonApplication: Hardened Enterprise Application Entry Point.
 * Implements StrictMode compliance, lazy dependency pre-warming, and isolated process safety.
 */
@HiltAndroidApp
class ScypheonApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var identityManager: ScypheonIdentityManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        Timber.i("🛡️ Scypheon Application starting (Enterprise Mode). Version: ${BuildConfig.VERSION_NAME}")

        // 1. Isolated Process Check (Critical: Bypasses heavy init)
        if (!isMainProcess()) {
            Timber.i("🛡️ Sandbox process detected. Skipping heavy initialization.")
            DatabaseReadySignal.markReady()
            return
        }

        // 2. Load Native Dependencies (SQLCipher)
        val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load native libraries")
        } finally {
            android.os.StrictMode.setThreadPolicy(oldPolicy)
        }

        // 3. Solaris Telemetry & Identity Initialization (Asynchronous)
        val oldPolicyTelemetry = android.os.StrictMode.allowThreadDiskReads()
        try {
            com.scypheon.sdk.core.utils.SolarisTelemetry.init(this)
        } finally {
            android.os.StrictMode.setThreadPolicy(oldPolicyTelemetry)
        }
        com.scypheon.sdk.core.utils.SolarisTelemetry.record("app_start", 1)

        appScope.launch {
            initializeBackgroundServices()
        }

        // 4. Production-Grade Logging & StrictMode (Main Process Only, Deferred)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            enableStrictMode()
        }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
        Timber.i("🛡️ StrictMode monitoring active (Enterprise Policy 1.1)")
    }

    private suspend fun initializeBackgroundServices() {
        try {
            // Identity Key Initialization (Requires API 31+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                identityManager.initializeIdentityKey()
                Timber.i("🔐 Identity Key initialized for mesh network.")
            }

            // Database Pre-Warming
            val entryPoint = EntryPointAccessors.fromApplication(
                this@ScypheonApplication,
                DatabaseEntryPoint::class.java
            )
            val db = entryPoint.appDatabase()
            db.query("SELECT 1", null).use { it.moveToFirst() }
            
            // Pre-warm preferences to avoid StrictMode violations on UI thread
            entryPoint.getHardwarePreferences().isMemoryOptimized()
            
            // Pre-warm / Extract embedding model from assets to stealth storage
            Timber.i("🛰️ Pre-warming stealth embedding extraction...")
            val extracted = com.scypheon.sdk.core.utils.AssetExtractor.extractAndVerify(this@ScypheonApplication, ".gateway_sync.bin")
            if (!extracted) {
                Timber.w("⚠️ Stealth embedding model not found in assets. Attempting shadow sync download...")
                com.scypheon.sdk.core.utils.ShadowSyncManager.ensureSynced(this@ScypheonApplication)
            }
            
            Timber.i("🛡️ Background services pre-warming completed.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize background services — proceeding anyway")
        } finally {
            DatabaseReadySignal.markReady()
        }
    }

    private fun isMainProcess(): Boolean {
        // [PHOENIX] Use modern API 28+ to avoid disk-heavy ActivityManager lookup
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            // Fallback for older APIs (though minSdk is 28)
            "com.scypheon.app" 
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
