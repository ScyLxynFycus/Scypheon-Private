package com.scypheon.sdk.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StrictMode
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * DatabaseKeyManager: Manages the cryptographic keys for SQLCipher database encryption.
 * Production-grade implementation with isolated process hardening and software-backed fallbacks.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val FALLBACK_KEY_ALIAS = "scypheon_isolated_db_key"
        private const val PBKDF2_ITERATIONS = 10000
        private const val KEY_LENGTH_BITS = 256

        @Volatile
        private var externalKey: ByteArray? = null
        
        fun setExternalKey(key: ByteArray) {
            externalKey = key.copyOf()
        }

        fun wipeExternalKey() {
            externalKey?.let { array ->
                for (i in array.indices) { array[i] = 0 }
            }
            externalKey = null
        }
    }

    private val isIsolatedProcess: Boolean by lazy {
        try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            android.os.Process.myUid() != appInfo.uid
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private val masterKeyAlias by lazy {
        if (isIsolatedProcess) {
            android.util.Log.w("DatabaseKeyManager", "Running in isolated process. Bypassing hardware Keystore.")
            getIsolatedProcessKey()
        } else {
            getHardwareBackedKey()
        }
    }

    private val masterKey by lazy {
        val oldPolicy = StrictMode.allowThreadDiskReads()
        try {
            MasterKey.Builder(context, masterKeyAlias)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    private val prefs by lazy {
        if (isIsolatedProcess) {
            return@lazy null // Cannot access EncryptedSharedPreferences in isolated process
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
            android.util.Log.e("DatabaseKeyManager", "EncryptedSharedPreferences failed, clearing and retrying...", e)
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
                android.util.Log.e("DatabaseKeyManager", "FATAL: Could not initialize secure preferences", e2)
                null
            }
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    /**
     * Production-grade key management.
     * Main process: Use hardware-backed KeyStore.
     * Isolated process: Use PBKDF2-derived key (software-backed).
     */
    fun getOrCreateMasterKey(): String = masterKeyAlias

    fun getDatabaseKey(): ByteArray {
        externalKey?.let { return it }
        
        if (isIsolatedProcess) {
            // In isolated process, we expect the key to be injected via IPC (externalKey)
            throw IllegalStateException("CRITICAL: Database key not yet injected via IPC into Sandbox Process!")
        }

        val p = prefs ?: throw IllegalStateException("Secure preferences unavailable")
        val oldPolicy = StrictMode.allowThreadDiskReads()
        val keyHex = try {
            p.getString("db_key", null)
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
            p.edit().putString("db_key", newKeyHex).apply()
        } finally {
            StrictMode.setThreadPolicy(oldPolicyWrite)
        }
        
        return newKey
    }

    private fun getHardwareBackedKey(): String {
        return try {
            val alias = MasterKey.DEFAULT_MASTER_KEY_ALIAS
            MasterKey.Builder(context, alias)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            alias
        } catch (e: Exception) {
            android.util.Log.w("DatabaseKeyManager", "KeyStore unavailable, using fallback", e)
            getIsolatedProcessKey()
        }
    }

    private fun getIsolatedProcessKey(): String {
        val deviceFingerprint = Build.FINGERPRINT
        val appSignature = getAppSignatureHash()
        val salt = "$deviceFingerprint:$appSignature".toByteArray()
        
        val spec = PBEKeySpec(
            "scypheon_enterprise_2026".toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BITS
        )
        
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec)
        
        val keyHash = MessageDigest.getInstance("SHA-256")
            .digest(key.encoded)
            .joinToString("") { "%02x".format(it) }
        
        return "$FALLBACK_KEY_ALIAS:$keyHash"
    }

    private fun getAppSignatureHash(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            
            signatures?.firstOrNull()?.let { sig ->
                MessageDigest.getInstance("SHA-256")
                    .digest(sig.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            } ?: "no_signature"
        } catch (e: Exception) {
            "signature_error"
        }
    }

    private fun byteArrayToHex(ba: ByteArray): String = ba.joinToString("") { "%02x".format(it) }

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
