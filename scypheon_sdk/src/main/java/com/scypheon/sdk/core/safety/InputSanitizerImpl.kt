package com.scypheon.sdk.core.safety

import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InputSanitizerImpl @Inject constructor() : InputSanitizer {
    companion object {
        private val INVISIBLE_CHARS = Regex("[\\u200B-\\u200F\\u2028-\\u202F\\u2060-\\u206F\\uFEFF]")
        private const val MAX_CHARS = 2048
    }

    override fun sanitize(query: String): SanitizedInput {
        val normalized = Normalizer.normalize(query, Normalizer.Form.NFKC)
        val cleaned = normalized.replace(INVISIBLE_CHARS, "").trim()
        val truncated = if (cleaned.length > MAX_CHARS) cleaned.substring(0, MAX_CHARS) else cleaned
        
        return SanitizedInput(
            text = truncated,
            originalLength = query.length,
            wasTruncated = cleaned.length > MAX_CHARS
        )
    }
}
