package com.scypheon.sdk.core.telemetry

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise-grade offline telemetry for the host app.
 * Adopts the BlackBox concept from Scypheon PC Framework to write logs securely to internal storage
 * rather than exposing them strictly to logcat or sending them over the network.
 * Ported from PR #3.
 */
@Singleton
class BlackBoxLogger @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext private val context: Context) : Timber.Tree() {

    private val logFile: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        val logDir = File(context.filesDir, "blackbox_telemetry")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        // Rotate logs by date
        val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        logFile = File(logDir, "scypheon_blackbox_${dateStamp}.log")
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val timestamp = dateFormat.format(Date())
        val levelStr = when (priority) {
            android.util.Log.VERBOSE -> "V"
            android.util.Log.DEBUG -> "D"
            android.util.Log.INFO -> "I"
            android.util.Log.WARN -> "W"
            android.util.Log.ERROR -> "E"
            android.util.Log.ASSERT -> "A"
            else -> "?"
        }

        val threadName = Thread.currentThread().name
        
        // 🛡SECURITY AUDIT: Redact sensitive PII before writing to the offline BlackBox log file.
        // This ensures that even if the app's internal storage is accessed, user secrets remain masked.
        val redactedMessage = com.scypheon.sdk.core.security.AegisPrivacyShield.redact(message)
        
        var logMessage = "[$timestamp] [$levelStr] [$threadName] ${tag ?: "Scypheon"}: $redactedMessage\n"

        if (t != null) {
            logMessage += android.util.Log.getStackTraceString(t) + "\n"
        }

        // In a true enterprise environment, this would be encrypted (e.g. AES-256-GCM)
        // For now, we append it directly to the protected internal storage file.
        try {
            FileOutputStream(logFile, true).use {
                it.write(logMessage.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            android.util.Log.e("BlackBox", "Failed to write telemetry: ${e.message}")
        }
    }
}
