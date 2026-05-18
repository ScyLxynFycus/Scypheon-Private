package com.scypheon.sdk.core.security

import android.util.Base64
import java.io.InputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignatureVerifier @Inject constructor() {
    
    // In production, this would be injected or loaded from a secure hardware-backed store.
    // This public key corresponds to the private key used in our CI/CD signing pipeline.
    private val publicKeyPem: String = """
        -----BEGIN PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0Z3VS5JJcds3xfn/ygWe
        GgE3F1Q6x6X0Vv9XZq9F8qX0Vv9XZq9F8qX0Vv9XZq9F8qX0Vv9XZq9F8qX0Vv9
        -----END PUBLIC KEY-----
    """.trimIndent()

    @Throws(SignatureVerificationException::class)
    fun verify(dbStream: InputStream, signatureStream: InputStream): Boolean {
        try {
            // 1. Calculate SHA-256 of the database file
            val md = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var read: Int
            while (dbStream.read(buffer).also { read = it } > 0) {
                md.update(buffer, 0, read)
            }
            val calculatedHash = md.digest()

            // 2. Load the signature
            val signatureBytes = signatureStream.readBytes()

            // 3. Verify signature against the hash
            val publicKey = parsePublicKey(publicKeyPem)
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(publicKey)
            sig.update(calculatedHash)
            
            if (!sig.verify(signatureBytes)) {
                throw SignatureVerificationException("Integrity check failed: Signature mismatch")
            }
            return true
        } catch (e: Exception) {
            throw SignatureVerificationException("Verification process failed: ${e.message}")
        }
    }

    private fun parsePublicKey(pem: String): PublicKey {
        val clean = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.decode(clean, Base64.DEFAULT)
        return KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(keyBytes))
    }
}

class SignatureVerificationException(msg: String) : Exception(msg)
