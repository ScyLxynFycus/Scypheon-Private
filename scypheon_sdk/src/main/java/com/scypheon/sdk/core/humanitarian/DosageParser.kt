package com.scypheon.sdk.core.humanitarian

import com.scypheon.sdk.core.annotations.SafetyCritical

@SafetyCritical
object DosageParser {
    data class ParsedDosage(
        val amount: Double?, 
        val unit: String?, 
        val frequency: Int?, 
        val isValid: Boolean,
        val timing: String? = null
    )

    // Order by length descending to match "mcg" before "g"
    private val UNITS = listOf("mcg", "mg", "ml", "iu", "tab", "cap", "tablet", "capsule", "kapsul", "gram", "g")
    
    private val UNIT_MAP = mapOf(
        "mcg" to "mcg", "mg" to "mg", "ml" to "ml", "iu" to "iu",
        "tab" to "tab", "tablet" to "tab", "kapsul" to "cap", "cap" to "cap", "capsule" to "cap",
        "gram" to "g", "g" to "g"
    )
    
    private val FREQ_MAP = mapOf(
        "sekali" to 1, "dua kali" to 2, "tiga kali" to 3,
        "once" to 1, "twice" to 2, "thrice" to 3,
        "daily" to 1, "harian" to 1
    )

    private val TIMING_MAP = mapOf(
        "after meals" to "After meals",
        "setelah makan" to "After meals",
        "before meals" to "Before meals",
        "sebelum makan" to "Before meals",
        "before sleep" to "Before sleep",
        "sebelum tidur" to "Before sleep",
        "morning" to "Morning",
        "pagi" to "Morning",
        "night" to "Night",
        "malam" to "Night"
    )

    fun parse(raw: String): ParsedDosage {
        val cleaned = raw.lowercase().trim()
        if (cleaned.isBlank()) return ParsedDosage(null, null, null, false)

        var amount: Double? = null
        var frequency: Int? = null
        var unit: String? = null

        // 1. Extract Unit first (longest match)
        unit = UNITS.find { cleaned.contains(it) }?.let { UNIT_MAP[it] }

        // 2. Handle "X x Y" pattern (frequency x amount)
        val xyPattern = Regex("""(\d+)\s*[x×*]\s*(\d+(?:\.\d+)?)""")
        val xyMatch = xyPattern.find(cleaned)
        
        if (xyMatch != null) {
            frequency = xyMatch.groupValues[1].toIntOrNull()
            // If unit is tab/cap, then Y is amount. Otherwise Y might be strength.
            val yValue = xyMatch.groupValues[2].toDoubleOrNull()
            amount = yValue
        }

        // 3. Extract Strength (e.g., "500mg")
        // If we found a unit, find the number preceding it.
        if (unit != null && (unit == "mg" || unit == "ml" || unit == "mcg" || unit == "g")) {
            val strengthPattern = Regex("""(\d+(?:\.\d+)?)\s*$unit""")
            val strengthMatch = strengthPattern.find(cleaned)
            val strengthValue = strengthMatch?.groupValues?.get(1)?.toDoubleOrNull()
            
            if (strengthValue != null) {
                // Strength takes precedence for the 'amount' field if it's a mass/volume unit
                amount = strengthValue
            }
        }

        // 4. Look for explicit frequency keywords if not found by pattern
        if (frequency == null) {
            frequency = FREQ_MAP.entries.find { (k, _) -> cleaned.contains(k) }?.value
        }
        
        // 5. Look for "X times" or "X kali"
        if (frequency == null) {
            frequency = Regex("""(\d+)\s*(?:kali|times)""").find(cleaned)?.groupValues?.get(1)?.toIntOrNull()
        }
        
        // Final fallback for amount if still null
        if (amount == null) {
            amount = Regex("""(\d+(?:\.\d+)?)""").find(cleaned)?.groupValues?.get(1)?.toDoubleOrNull()
        }

        // Extract timing
        val timing = TIMING_MAP.entries.find { (k, _) -> cleaned.contains(k) }?.value

        // Clinical safety: block if amount missing OR (unit and frequency both missing)
        val isValid = amount != null && (unit != null || frequency != null)
        
        return ParsedDosage(amount, unit, frequency, isValid, timing)
    }
}
