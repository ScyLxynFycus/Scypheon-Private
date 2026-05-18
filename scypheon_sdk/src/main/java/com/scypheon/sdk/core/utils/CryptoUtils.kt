package com.scypheon.sdk.core.utils

import java.security.MessageDigest
import java.util.Base64

object CryptoUtils {

    /**
     * Generates a SHA-256 hash of the input string.
     */
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a short HMAC-like signature for BLE packets.
     * In a production enterprise app, this would use a real HMAC with a shared/rotated secret.
     * For the hackathon, we use a salted SHA-256 to demonstrate the integrity layer.
     */
    fun signPacket(payload: String, secret: String): String {
        val salted = "$payload|$secret"
        return sha256(salted).take(8) // We only use 8 chars for BLE overhead efficiency
    }

    /**
     * Verifies the packet signature.
     */
    fun verifyPacket(payload: String, signature: String, secret: String): Boolean {
        return signPacket(payload, secret) == signature
    }
}
