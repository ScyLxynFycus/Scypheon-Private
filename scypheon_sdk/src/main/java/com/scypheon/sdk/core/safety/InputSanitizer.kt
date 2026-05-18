package com.scypheon.sdk.core.safety

/**
 * Single source of truth for input sanitization contract.
 */
interface InputSanitizer {
    fun sanitize(query: String): SanitizedInput
}

data class SanitizedInput(
    val text: String,
    val originalLength: Int,
    val wasTruncated: Boolean
)
