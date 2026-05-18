package com.scypheon.sdk.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class MeshSignatureManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val KEY_ALIAS = "scypheon_mesh_identity"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    init {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateIdentityKey()
        }
    }

    private fun generateIdentityKey() {
        try {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
            )
            kpg.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            )
            kpg.generateKeyPair()
            Timber.d("🔒 Mesh: Generated new EC identity keypair")
        } catch (e: Exception) {
            Timber.e(e, "❌ Mesh: Failed to generate identity key")
        }
    }

    fun signData(data: String): String {
        return try {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as java.security.PrivateKey
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update(data.toByteArray())
            byteArrayToHex(signature.sign())
        } catch (e: Exception) {
            Timber.e(e, "❌ Mesh: Signing failure")
            "ERROR_SIGNING"
        }
    }

    fun verifySignature(data: String, signatureHex: String, publicKeyBytes: ByteArray? = null): Boolean {
        return try {
            val publicKey = if (publicKeyBytes != null) {
                // In a real multi-device mesh, we would parse the public key from the message
                // For now, we assume local verification for the demo
                keyStore.getCertificate(KEY_ALIAS).publicKey
            } else {
                keyStore.getCertificate(KEY_ALIAS).publicKey
            }
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(publicKey)
            signature.update(data.toByteArray())
            signature.verify(hexToByteArray(signatureHex))
        } catch (e: Exception) {
            Timber.e(e, "❌ Mesh: Verification failure")
            false
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
