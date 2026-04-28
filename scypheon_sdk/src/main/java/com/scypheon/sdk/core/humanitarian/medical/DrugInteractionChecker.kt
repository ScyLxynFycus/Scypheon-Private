package com.scypheon.sdk.core.humanitarian.medical

import timber.log.Timber

/**
 * Enterprise Sub-System: Drug Interaction Checker.
 * Provides offline pharmacological cross-checking to prevent dangerous medical combinations.
 * In a real production app, this would be backed by a full SQLite database derived from FDA APIs.
 * For the hackathon, it uses a localized hardcoded Map of known dangerous pairs.
 */
object DrugInteractionChecker {

    // Simulates a relational pharmacological database (Drug A -> List of dangerously interacting Drug B's)
    private val KNOWN_INTERACTIONS = mapOf(
        "aspirin" to listOf("ibuprofen", "warfarin", "naproxen"),
        "ibuprofen" to listOf("aspirin", "naproxen", "corticosteroids"),
        "warfarin" to listOf("aspirin", "omeprazole", "amiodarone"),
        "simvastatin" to listOf("amiodarone", "erythromycin", "clarithromycin"),
        "sildenafil" to listOf("nitroglycerin", "isosorbide")
    )

    /**
     * Checks if a newly scanned drug interacts dangerously with the patient's currently prescribed drugs.
     */
    fun checkInteraction(scannedDrug: String, currentPrescriptions: List<String>): String? {
        val cleanScanned = scannedDrug.lowercase().trim()

        // Find if the scanned drug exists in our database
        val interactants = KNOWN_INTERACTIONS[cleanScanned] ?: return null

        // Check if any of the patient's current prescriptions match the known dangerous interactants
        for (prescription in currentPrescriptions) {
            val cleanPrescription = prescription.lowercase().trim()
            if (interactants.contains(cleanPrescription)) {
                val warning = "SEVERE INTERACTION RISK: $scannedDrug should not be taken with $prescription."
                Timber.w("💊 Medical Alert: $warning")
                return warning
            }
        }

        return null // No known interactions found
    }
}
