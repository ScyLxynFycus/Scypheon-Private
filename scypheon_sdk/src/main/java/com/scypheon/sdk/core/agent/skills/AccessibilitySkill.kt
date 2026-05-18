package com.scypheon.sdk.core.agent.skills

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AccessibilitySkill:
 * Enhances inclusivity for users with reading difficulties or visual impairments.
 * 
 * Maps to Theme: Digital Equity & Inclusivity.
 */
@Singleton
class AccessibilitySkill @Inject constructor() {

    fun formatForDyslexia(text: String): String {
        return text.split(". ")
            .joinToString("\n• ") { it.trim() }
            .let { "• $it" }
    }

    fun buildDyslexiaFriendlyPrompt(text: String): String {
        return """
            [SKILL_ACCESSIBILITY_DYSLEXIA]
            Reformat the following text to be dyslexia-friendly.
            
            TEXT: $text
            
            RULES: Short sentences, simple words, bullet points, and bold keywords.
            [/SKILL_ACCESSIBILITY_DYSLEXIA]
        """.trimIndent()
    }
}
