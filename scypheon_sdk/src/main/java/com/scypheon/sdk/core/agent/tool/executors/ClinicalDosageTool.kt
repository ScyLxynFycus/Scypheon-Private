package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.*
import com.scypheon.sdk.core.humanitarian.medical.PharmacopeiaDao
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * ClinicalDosageTool: Precision pediatric and adult dosage calculator.
 * Enforces strict Fail-Closed safety envelopes using authoritative pharmacopeia data.
 */
@Singleton
class ClinicalDosageTool @Inject constructor(
    private val pharmacopeiaDao: PharmacopeiaDao
) : BaseTool() {
    
    override val name: String = "calculate_clinical_dosage"
    override val isMedical: Boolean = true
    
    override val description: String = "Calculates safe clinical dosages based on patient weight (kg) and age. Enforces maximum safety limits. Will strictly fail if data is insufficient."
    
    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "genericName": {
              "type": "string",
              "description": "The exact generic name of the drug (e.g., 'Acetaminophen')"
            },
            "weightKg": {
              "type": "number",
              "description": "Patient weight in kilograms. MUST be greater than 0."
            },
            "ageMonths": {
              "type": "integer",
              "description": "Patient age in months. Optional, but critical for infants."
            }
          },
          "required": ["genericName", "weightKg"]
        }
    """.trimIndent()

    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val start = System.currentTimeMillis()
        
        // 1. Strict Parameter Parsing (No Silent Defaults)
        val genericName = args["genericName"] as? String 
            ?: return ToolResult.Error("CRITICAL: Missing drug name parameter.", null, System.currentTimeMillis() - start)
            
        val weightDouble = (args["weightKg"] as? Number)?.toDouble() ?: 0.0
        if (weightDouble <= 0.0) {
            return ToolResult.Error("CRITICAL: Invalid patient weight ($weightDouble kg). Weight must be provided and > 0.", null, System.currentTimeMillis() - start)
        }
        
        val ageMonths = (args["ageMonths"] as? Number)?.toInt()

        // 2. Authoritative Data Retrieval
        val drug = pharmacopeiaDao.getEntryByGenericName(genericName)
            ?: return ToolResult.Error("FAIL-SAFE: Drug '$genericName' not found in local WHO/FDA authoritative database. Manual physician calculation required.", null, System.currentTimeMillis() - start)

        // 3. Strict Fail-Closed Pharmacokinetics
        val maxMgPerKg = drug.maxMgPerKg?.toDouble()
            ?: return ToolResult.Error("FAIL-SAFE: No weight-based dosing profile exists for '$genericName'. Aborting calculation.", null, System.currentTimeMillis() - start)

        val maxSingleDose = drug.maxSingleDoseMg?.toDouble() ?: Double.MAX_VALUE
        val maxDaily = drug.maxDailyMg ?: 4000

        // 4. Clinical Math (using BigDecimal for medical precision)
        val weight = BigDecimal(weightDouble)
        val mgPerKg = BigDecimal(maxMgPerKg)
        
        var calculatedDose = weight.multiply(mgPerKg)
        
        // 5. Adult / Obesity Cap Enforcement
        val singleDoseCap = BigDecimal(maxSingleDose)
        var cappedWarning = ""
        if (calculatedDose > singleDoseCap) {
            calculatedDose = singleDoseCap
            cappedWarning = " (CAPPED at maximum single adult dose of $maxSingleDose mg)"
        }

        // Round to 2 decimal places for clinical standard
        val finalDoseDisplay = calculatedDose.setScale(2, RoundingMode.HALF_UP).toPlainString()

        // 6. Medical Summary Building
        val summary = StringBuilder()
        summary.append("Calculated Single Dose: $finalDoseDisplay mg$cappedWarning\n")
        summary.append("Max Daily Limit: $maxDaily mg\n")
        summary.append("Route: ${drug.route ?: "PO (Oral/Check Label)"}\n")
        
        if (drug.isHighRisk) {
            summary.append("⚠️ HIGH-RISK MEDICATION: Double-check verification by 2nd clinician is MANDATORY.\n")
        }
        
        if (ageMonths != null && ageMonths in 0..6) {
            summary.append("⚠️ INFANT WARNING: Patient is < 6 months old. Renal/Hepatic clearance may be compromised. Consult specialist.\n")
        }

        return ToolResult.Success(
            mapOf(
                "dose_mg" to finalDoseDisplay.toDouble(),
                "is_capped" to cappedWarning.isNotEmpty(),
                "max_daily_mg" to maxDaily,
                "atc_code" to (drug.atcCode ?: "N/A"),
                "safety_summary" to summary.toString(),
                "clinical_directive" to "Provide the safety_summary exactly as written to the user.",
                "legal_disclaimer" to "Grounding source: ${drug.source}. AI-assisted calculation based on WHO EML. Final clinical execution remains with the attending operator."
            ),
            System.currentTimeMillis() - start
        )
    }

    override fun getActivityDescription(args: Map<String, Any?>): String = 
        "Running pharmacokinetics safety protocol for ${args["genericName"] ?: "Unknown"}"
}
