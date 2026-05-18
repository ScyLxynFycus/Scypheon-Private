package com.scypheon.sdk.core.medical

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleBasedMedicalCache @Inject constructor() {
    
    data class StaticProtocol(
        val condition: String,
        val steps: List<String>,
        val escalationThreshold: String,
        val isEmergency: Boolean
    )

    // Hardcoded safety protocols. Zero external dependencies. Zero allocation lookup.
    private val protocols = listOf(
        StaticProtocol(
            condition = "DIARRHEA_DEHYDRATION",
            steps = listOf(
                "Give Oral Rehydration Solution (ORS) - 200ml after each loose stool.",
                "Continue breastfeeding or normal feeding.",
                "Avoid sugary drinks or plain water only."
            ),
            escalationThreshold = "Sunken eyes, lethargy, or unable to drink.",
            isEmergency = false
        ),
        StaticProtocol(
            condition = "SEVERE_BLEEDING",
            steps = listOf(
                "Apply direct pressure with clean cloth.",
                "Elevate the wound above heart level if possible.",
                "Do not remove soaked bandages; add more on top."
            ),
            escalationThreshold = "Bleeding does not stop after 10 min of firm pressure.",
            isEmergency = true
        ),
        StaticProtocol(
            condition = "HEAT_EXHAUSTION",
            steps = listOf(
                "Move to a cool, shaded area.",
                "Loosen tight clothing.",
                "Give small sips of water or electrolyte drink.",
                "Apply cool, wet cloths to skin."
            ),
            escalationThreshold = "Loss of consciousness, confusion, or temp >40°C.",
            isEmergency = true
        )
    )

    fun resolve(query: String): Result<StaticProtocol> {
        val normalized = query.lowercase().trim()
        // Simple keyword matching for maximum reliability and speed
        val match = protocols.find { protocol ->
            val keywords = protocol.condition.split("_")
            keywords.any { normalized.contains(it.lowercase()) }
        }
        return if (match != null) {
            Result.success(match)
        } else {
            Result.failure(CacheMissException("No deterministic fallback found for: $query"))
        }
    }
}

class CacheMissException(msg: String) : Exception(msg)
