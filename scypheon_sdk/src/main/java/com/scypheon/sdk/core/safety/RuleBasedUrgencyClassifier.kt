package com.scypheon.sdk.core.safety

import com.scypheon.sdk.core.agent.ooda.UrgencyClassifier
import com.scypheon.sdk.core.agent.ooda.UrgencyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleBasedUrgencyClassifier @Inject constructor() : UrgencyClassifier {
    companion object {
        private val URGENT_PATTERNS = setOf(
            "emergency", "sos", "mayday", "darurat", "tolong", "kecelakaan",
            "bencana", "serangan", "kritis", "pendarahan", "critical", "chest pain", "sukar nafas"
        )
        private val URGENCY_REGEX = Regex(URGENT_PATTERNS.joinToString("|") { "\\b$it\\b" }, RegexOption.IGNORE_CASE)
    }

    override suspend fun classify(query: String): UrgencyResult = withContext(Dispatchers.Default) {
        val match = URGENCY_REGEX.find(query)
        if (match != null) {
            UrgencyResult(isUrgent = true, confidence = 0.95f, reason = "Keyword match: ${match.value}")
        } else {
            UrgencyResult(isUrgent = false, confidence = 0.1f, reason = "No urgent patterns detected")
        }
    }
}
