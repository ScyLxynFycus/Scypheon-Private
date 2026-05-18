package com.scypheon.sdk.core.humanitarian.medical

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise Sub-System: Drug Interaction Checker.
 * Provides offline pharmacological cross-checking to prevent dangerous medical combinations.
 * Backed by the consolidated WHO-based PharmacopeiaDao.
 */
@Singleton
class DrugInteractionChecker @Inject constructor(
    private val dao: PharmacopeiaDao
) {

    /**
     * Checks if a newly scanned drug interacts dangerously with the patient's currently prescribed drugs.
     */
    suspend fun checkInteraction(scannedDrug: String, currentPrescriptions: List<String>): String? {
        val idScanned = dao.resolveIds(scannedDrug).firstOrNull() ?: return null

        for (prescription in currentPrescriptions) {
            val idPrescription = dao.resolveIds(prescription).firstOrNull() ?: continue
            
            // Interaction details are now stored in the PharmacopeiaEntry or separate entity
            // Using the hardened dao.getInteraction which returns a detail string
            val interaction = dao.getInteraction(idScanned, idPrescription)
            if (interaction != null) {
                val warning = "SEVERE INTERACTION RISK: $scannedDrug should not be taken with $prescription. Detail: $interaction"
                Timber.w("💊 Medical Alert: $warning")
                return warning
            }
        }

        return null // No known interactions found
    }
}
