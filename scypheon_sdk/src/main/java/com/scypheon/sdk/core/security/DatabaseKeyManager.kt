package com.scypheon.sdk.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.os.StrictMode
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * DatabaseKeyManager: Manages the cryptographic keys for SQLCipher database encryption.
 * [SECURITY] Supports process-aware key handoff for isolated sandboxes with RAM-wiping.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(@ApplicationContext private val context: Context) {
    
    companion object {
        @Volatile
        private var externalKey: ByteArray? = null
        
        fun setExternalKey(key: ByteArray) {
            // Salin array agar referensi asli dari IPC tidak memodifikasi internal
            externalKey = key.copyOf()
        }

        /**
         * HARUS dipanggil segera setelah SQLCipher berhasil diinisialisasi
         * untuk menghapus kunci dekripsi dari RAM.
         */
        fun wipeExternalKey() {
            externalKey?.let { array ->
                // Timpa dengan 0 (Zero-out) memori sebelum di-Garbage Collect
                for (i in array.indices) {
                    array[i] = 0
                }
            }
            externalKey = null
        }
    }

    private val isIsolatedProcess: Boolean by lazy {
        try {
            val processName = if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.app.Application.getProcessName()
            } else {
                // Fallback for older versions
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val myPid = android.os.Process.myPid()
                activityManager.runningAppProcesses?.find { it.pid == myPid }?.processName ?: ""
            }
            processName.contains(":") || android.os.Process.isIsolated()
        } catch (e: Exception) {
            true
        }
    }

    private val masterKey by lazy {
        if (isIsolatedProcess) {
            android.util.Log.w("KeyManager", "Running in isolated process. Bypassing hardware Keystore.")
            return@lazy MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setUserAuthenticationRequired(false)
                .build()
        }

        val oldPolicy = StrictMode.allowThreadDiskReads()
        try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    private val prefs by lazy {
        if (isIsolatedProcess) {
            throw IllegalStateException("Cannot access EncryptedSharedPreferences in isolated process.")
        }
        val oldPolicy = StrictMode.allowThreadDiskReads()
        try {
            EncryptedSharedPreferences.create(
                context,
                "scypheon_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.e("KeyManager", "EncryptedSharedPreferences failed, clearing and retrying...", e)
            try {
                context.getSharedPreferences("scypheon_secure_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                EncryptedSharedPreferences.create(
                    context,
                    "scypheon_secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                android.util.Log.e("KeyManager", "FATAL: Could not initialize secure preferences", e2)
                throw e2
            }
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    fun getDatabaseKey(): ByteArray {
        externalKey?.let { return it }
        
        if (isIsolatedProcess) {
            // FAIL-FAST: Jangan pernah menggunakan placeholder kosong untuk kriptografi!
            throw IllegalStateException("CRITICAL: Database key not yet injected via IPC into Sandbox Process!")
        }

        val oldPolicy = StrictMode.allowThreadDiskReads()
        val keyHex = try {
            prefs.getString("db_key", null)
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
        
        if (keyHex != null) {
            return hexToByteArray(keyHex)
        }

        val newKey = ByteArray(32)
        java.security.SecureRandom().nextBytes(newKey)
        val newKeyHex = byteArrayToHex(newKey)
        
        val oldPolicyWrite = StrictMode.allowThreadDiskReads()
        try {
            prefs.edit().putString("db_key", newKeyHex).apply()
        } finally {
            StrictMode.setThreadPolicy(oldPolicyWrite)
        }
        
        return newKey
    }

    private fun byteArrayToHex(ba: ByteArray): String {
        return ba.joinToString("") { "%02x".format(it) }
    }

    private fun hexToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
