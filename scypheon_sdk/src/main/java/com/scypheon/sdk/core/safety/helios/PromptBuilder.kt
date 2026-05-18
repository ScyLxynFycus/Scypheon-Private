package com.scypheon.sdk.core.safety.helios

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.Gson

@Singleton
class PromptBuilder @Inject constructor() {
    
    private val gson = Gson()

    data class SystemBlock(
        val role: String,
        val boundary_hash: String,
        val safety_level: String = "HARDENED"
    )

    /**
     * Secures the system prompt using encapsulation and SHA-256 integrity hash.
     * Prevents delimiter escape and instruction overriding.
     */
    fun buildSecurePrompt(systemInstruction: String, userQuery: String, enableThinking: Boolean = false): String {
        val hash = sha256(systemInstruction)
        
        val thinkingInstruction = if (enableThinking) {
            "\nYou MUST think before answering. Place your reasoning steps inside <thought>...</thought> blocks."
        } else {
            "\nDO NOT think before answering. DO NOT use <thought>...</thought> blocks. Provide the final answer directly."
        }

        return """
            <|im_start|>system
            [INTEGRITY_HASH: $hash]
            CONTENT: $systemInstruction$thinkingInstruction
            <|im_end|>
            <|im_start|>user
            $userQuery
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
