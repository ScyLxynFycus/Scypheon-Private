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

<<<<<<< Updated upstream
import com.scypheon.app.security.ScypheonIdentityManager
import javax.inject.Inject

=======
/**
 * ScypheonApplication: Hardened Enterprise Application Entry Point.
 * Implements StrictMode compliance, lazy dependency pre-warming, and isolated process safety.
 */
>>>>>>> Stashed changes
@HiltAndroidApp
class ScypheonApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var identityManager: ScypheonIdentityManager

<<<<<<< Updated upstream
    /**
     * [v1.5.0-SAR] WorkManager Configuration Provider.
     *
     * Required because we disabled the default WorkManagerInitializer in the manifest
     * (tools:node="remove") for Hilt compatibility. Without this, LeakCanary's
     * WorkManagerHeapAnalyzer crashes with IllegalStateException, which triggers
     * repeated System.gc() calls that cause the "Skipped 568 frames" Choreographer drop.
     */
=======
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

>>>>>>> Stashed changes
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
<<<<<<< Updated upstream

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

=======

        Timber.i("🛡️ Scypheon Application starting (Enterprise Mode). Version: ${BuildConfig.VERSION_NAME}")

        // 1. Isolated Process Check (Critical: Bypasses heavy init)
>>>>>>> Stashed changes
        if (!isMainProcess()) {
            Timber.i("🛡️ Sandbox process detected. Skipping heavy initialization.")
            DatabaseReadySignal.markReady()
            return
        }

<<<<<<< Updated upstream
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
=======
        // 2. Load Native Dependencies (SQLCipher)
        val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load native libraries")
        } finally {
            android.os.StrictMode.setThreadPolicy(oldPolicy)
>>>>>>> Stashed changes
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
