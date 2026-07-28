package com.scypheon.sdk.core.agent.tool.executors

import com.scypheon.sdk.core.agent.tool.BaseTool
import com.scypheon.sdk.core.agent.tool.ToolResult
import com.scypheon.sdk.core.agent.tool.ExecutionContext
import com.scypheon.sdk.core.humanitarian.medical.PharmacopeiaDao
import com.scypheon.sdk.core.security.AuditChain
import com.scypheon.sdk.core.security.PqcAuditSigner
import com.scypheon.sdk.core.telemetry.BlackBoxVault
import com.scypheon.sdk.core.telemetry.PIIAnonymizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/**
 * ClinicalDosageTool: PQC-Signed Dosage Calculator
 * 
 * Calculates safe dosage based on patient weight, age, and pharmacopeia data.
 * Every calculation is:
 * 1. Cryptographically signed with ML-DSA (Dilithium) for non-repudiation
 * 2. Logged to AuditChain for forensic traceability
 * 3. Anonymized before telemetry storage (zero-knowledge)
 * 
 * Returns signature and public key fingerprint so UI can display
 * "Verifiable Medical AI" badge with verification link.
 */
class ClinicalDosageTool @Inject constructor(
    private val pharmacopeiaDao: PharmacopeiaDao,
    private val pqcAuditSigner: PqcAuditSigner,
    private val auditChain: AuditChain,
    private val blackBoxVault: BlackBoxVault,
    private val piiAnonymizer: PIIAnonymizer
) : BaseTool() {
    
    override val name: String = "calculate_clinical_dosage"
    override val isMedical: Boolean = true
    
    override val triggerDescription: String = "Precision pediatric and adult dosage calculator."
    
    override val description: String = """
        Calculates safe medication dosage based on patient weight, age, and pharmacopeia data.
        Returns PQC-signed result for cryptographic verification.
        
        Required arguments:
        - drug_name: Name of medication
        - weight_kg: Patient weight in kilograms
        - age_years: Patient age in years (or months for infants)
        
        Optional arguments:
        - age_months: For infants < 1 year
        
        Returns: Dosage calculation with PQC signature for verification.
    """.trimIndent()
    
    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "drug_name": {
              "type": "string",
              "description": "Name of the medication (e.g., 'Paracetamol', 'Amoxicillin')"
            },
            "weight_kg": {
              "type": "number",
              "description": "Patient weight in kilograms"
            },
            "age_years": {
              "type": "integer",
              "description": "Patient age in years"
            },
            "age_months": {
              "type": "integer",
              "description": "For infants: age in months (optional if age_years provided)"
            }
          },
          "required": ["drug_name", "weight_kg"]
        }
    """.trimIndent()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    @Serializable
    data class DosagePayload(
        val drug_name: String,
        val dose_mg: Double,
        val dose_per_kg: Double,
        val max_daily_mg: Double,
        val frequency: String,
        val safety_summary: String,
        val weight_kg: Double,
        val age_years: Int?,
        val age_months: Int?,
        val timestamp: Long
    )
    
    override suspend fun call(args: Map<String, Any?>, context: ExecutionContext): ToolResult = withContext(Dispatchers.IO) {
        val traceId = "DOSAGE-${System.currentTimeMillis()}"
        val start = System.currentTimeMillis()
        
        try {
            // 1. Extract and validate inputs
            val drugName = args["drug_name"] as? String
                ?: return@withContext ToolResult.Error("Missing required argument: drug_name")
            
            val weightKg = (args["weight_kg"] as? Number)?.toDouble()
                ?: return@withContext ToolResult.Error("Missing required argument: weight_kg")
            
            val ageYears = (args["age_years"] as? Number)?.toInt()
            val ageMonths = (args["age_months"] as? Number)?.toInt()
            
            if (weightKg <= 0) {
                return@withContext ToolResult.Error("Invalid weight: must be positive")
            }
            
            Timber.i("$TAG: Calculating dosage for $drugName [weight=${weightKg}kg, trace=$traceId]")
            
            // 2. Query pharmacopeia
            val drugInfo = pharmacopeiaDao.getByDrugName(drugName)
                ?: return@withContext ToolResult.Error("Drug not found in pharmacopeia: $drugName")
            
            // 3. Calculate dosage
            val doseMgPerKg = drugInfo.maxMgPerKg?.toDouble()
                ?: return@withContext ToolResult.Error("No dosage information available for $drugName")
            
            val calculatedDose = weightKg * doseMgPerKg
            val maxDailyDose = drugInfo.maxDailyMg?.toDouble() 
                ?: return@withContext ToolResult.Error("CRITICAL SAFETY FAULT: Pharmacopeia database is missing 'maxDailyMg' for $drugName. Aborting dosage calculation to prevent potential overdose.")
            
            // 4. Safety checks
            val safetySummary = performSafetyChecks(
                drugName = drugName,
                calculatedDose = calculatedDose,
                maxDailyDose = maxDailyDose,
                weightKg = weightKg,
                ageYears = ageYears,
                ageMonths = ageMonths,
                contraindications = drugInfo.contraindications
            )
            
            // 5. Build canonical payload for signing
            val payload = DosagePayload(
                drug_name = drugName,
                dose_mg = calculatedDose,
                dose_per_kg = doseMgPerKg,
                max_daily_mg = maxDailyDose,
                frequency = "Every 6-8 hours",
                safety_summary = safetySummary,
                weight_kg = weightKg,
                age_years = ageYears,
                age_months = ageMonths,
                timestamp = System.currentTimeMillis()
            )
            
            val canonicalJson = json.encodeToString(payload)
            
            // 6. PQC Sign the payload
            Timber.d("$TAG: Signing dosage calculation with ML-DSA")
            val signature = pqcAuditSigner.signEvent(canonicalJson.toByteArray())
            val signerFingerprint = pqcAuditSigner.getPublicKeyFingerprint()
            
            // 7. Log to AuditChain (cryptographic proof)
            // AuditChain internal logEvent format expects a payload string. It will compute hash and sign it again via native layer.
            // But we log our signed payload anyway for redundancy.
            auditChain.logEvent("CLINICAL_DOSAGE_CALC", canonicalJson)
            
            // 8. Log to BlackBoxVault (anonymized telemetry)
            val anonymizedContext = piiAnonymizer.anonymize(
                "Drug: $drugName, Weight: ${weightKg}kg, Age: ${ageYears ?: ageMonths} ${if (ageMonths != null) "months" else "years"}"
            )
            
            blackBoxVault.recordClinicalDecision(
                traceId = traceId,
                decisionType = "DOSAGE_CALCULATION",
                pqcSignature = signature,
                signerFingerprint = signerFingerprint,
                anonymizedContext = anonymizedContext
            )
            
            // 9. Build result with signature
            val resultData = mapOf(
                "drug_name" to drugName,
                "dose_mg" to calculatedDose,
                "dose_per_kg" to doseMgPerKg,
                "max_daily_mg" to maxDailyDose,
                "frequency" to "Every 6-8 hours",
                "safety_summary" to safetySummary,
                "pqc_signature" to signature,
                "signer_fingerprint" to signerFingerprint,
                "trace_id" to traceId,
                "verification_status" to "SIGNED"
            )
            
            Timber.i("$TAG: Dosage calculated successfully [dose=${calculatedDose}mg, trace=$traceId]")
            
            ToolResult.Success(
                data = resultData,
                latencyMs = System.currentTimeMillis() - start
            )
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Dosage calculation failed")
            
            blackBoxVault.logSafetyViolation(
                traceId = traceId,
                violationType = "DOSAGE_CALCULATION_ERROR",
                payload = "Error: ${e.message}",
                severity = "HIGH"
            )
            
            ToolResult.Error("Dosage calculation failed: ${e.message}", null, System.currentTimeMillis() - start)
        }
    }
    
    private fun performSafetyChecks(
        drugName: String,
        calculatedDose: Double,
        maxDailyDose: Double,
        weightKg: Double,
        ageYears: Int?,
        ageMonths: Int?,
        contraindications: String?
    ): String {
        val warnings = mutableListOf<String>()
        
        // Check 1: Dose exceeds maximum
        if (calculatedDose > maxDailyDose) {
            warnings.add("⚠️ CRITICAL: Calculated dose (${calculatedDose}mg) exceeds maximum daily dose (${maxDailyDose}mg)")
        }
        
        // Check 2: Pediatric dose validation
        val ageInMonths = ageMonths ?: (ageYears?.times(12))
        if (ageInMonths != null && ageInMonths < 24) {
            warnings.add("⚠️ Infant/pediatric patient: Verify dose with pediatric specialist")
        }
        
        // Check 3: Low weight warning
        if (weightKg < 10) {
            warnings.add("⚠️ Low body weight: Monitor for adverse effects")
        }
        
        // Check 4: Contraindications
        if (!contraindications.isNullOrBlank()) {
            warnings.add("ℹ️ Contraindications: $contraindications")
        }
        
        return if (warnings.isEmpty()) {
            "✅ Dose within safe range. No contraindications detected."
        } else {
            warnings.joinToString("\n")
        }
    }
    
    override fun getActivityDescription(args: Map<String, Any?>): String =
        "Running pharmacokinetics safety protocol for ${args["drug_name"] ?: "Unknown"}"

    companion object {
        private const val TAG = "ClinicalDosageTool"
    }
}
