package com.scypheon.sdk.core.utils

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    /**
     * Generates a SHA-256 hash of the input string.
     */
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a short HMAC-SHA256 signature for BLE packets.
     * Truncated to 8 characters for BLE payload efficiency while providing
     * cryptographically secure authentication (unlike simple salted hashing).
     */
    fun signPacket(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        mac.init(secretKeySpec)
        val hmacBytes = mac.doFinal(payload.toByteArray())
        return hmacBytes.joinToString("") { "%02x".format(it) }.take(8)
    }

    /**
     * Verifies the packet signature securely.
     */
    fun verifyPacket(payload: String, signature: String, secret: String): Boolean {
        // Use MessageDigest.isEqual for constant-time comparison to prevent timing attacks
        val expected = signPacket(payload, secret).toByteArray()
        val actual = signature.toByteArray()
        return MessageDigest.isEqual(expected, actual)
    }
}
