package com.scypheon.sdk.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore
import android.util.Base64

/**
 * Enterprise A.I. Research Feature: Zero-Knowledge Enclave.
 * Implements hardware-backed AES-256-GCM encryption for the SQLite Vector DB (RAG) and Medical Profile.
 * Even if the elderly user's phone is physically stolen and rooted,
 * hackers cannot read their chat history or medical conditions.
 */
class ZeroKnowledgeEnclave(context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ENCLAVE_KEY_ALIAS = "ScypheonRAGEnclaveKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    init {
        initializeHardwareKey()
    }

    private fun initializeHardwareKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(ENCLAVE_KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )

                val builder = android.security.keystore.KeyGenParameterSpec.Builder(
                    ENCLAVE_KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    try {
                        builder.setIsStrongBoxBacked(true)
                        keyGenerator.init(builder.build())
                        keyGenerator.generateKey()
                        Timber.i("🔒 Zero-Knowledge Enclave: Hardware Keystore AES-256-GCM initialized with StrongBox.")
                    } catch (e: Exception) {
                        Timber.w("🔒 StrongBox unavailable on this device, falling back to standard TEE: ${e.message}")
                        val fallbackBuilder = android.security.keystore.KeyGenParameterSpec.Builder(
                            ENCLAVE_KEY_ALIAS,
                            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                        )
                        .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        
                        keyGenerator.init(fallbackBuilder.build())
                        keyGenerator.generateKey()
                        Timber.i("🔒 Zero-Knowledge Enclave: Hardware Keystore AES-256-GCM initialized with standard TEE.")
                    }
                } else {
                    keyGenerator.init(builder.build())
                    keyGenerator.generateKey()
                    Timber.i("🔒 Zero-Knowledge Enclave: Hardware Keystore AES-256-GCM initialized.")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "🚨 CRITICAL: Failed to initialize Zero-Knowledge Enclave Hardware Key.")
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(ENCLAVE_KEY_ALIAS, null) as SecretKey
    }

    class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Encrypts plaintext (like Chat Messages or Medical Allergies) before storing in SQLite.
     * Format: Base64(IV + CipherText)
     */
    fun encryptData(plaintext: String): String {
        if (plaintext.isBlank()) return plaintext
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

            val iv = cipher.iv
            val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            // Prepend IV to cipher text for decryption later
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "Enclave Encryption Failed.")
            throw CryptoException("Failed to encrypt data in Zero-Knowledge Enclave.", e)
        }
    }

    /**
     * Decrypts ciphertext loaded from SQLite back into plaintext.
     */
    fun decryptData(encryptedBase64: String): String {
        if (encryptedBase64.isBlank()) return encryptedBase64
        
        // Strip all potential whitespace/newlines added by legacy encoders
        val cleanedInput = encryptedBase64.replace("\\s".toRegex(), "")
        
        if (!cleanedInput.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
            throw CryptoException("Invalid ciphertext format: Not Base64.")
        }

        return try {
            val combined = Base64.decode(cleanedInput, Base64.NO_WRAP)
            
            if (combined.size < GCM_IV_LENGTH) {
                throw CryptoException("Ciphertext too short to contain IV.")
            }

            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            val plainTextBytes = cipher.doFinal(cipherText)
            String(plainTextBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Enclave Decryption Failed.")
            throw CryptoException("Failed to decrypt data in Zero-Knowledge Enclave.", e)
        }
    }
}
