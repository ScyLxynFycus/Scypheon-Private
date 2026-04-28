package com.scypheon.sdk.core.humanitarian

import android.content.Context
import com.google.gson.Gson
import timber.log.Timber

/**
 * DrugDatabase: Hybrid Drug Information System
 *
 * ARCHITECTURE:
 * - Tier 0: INSTANT_DRUGS HashMap (50 obat P3K, hardcoded, < 1ms)
 * - Tier 1: SQLite/JSON Local (500 obat umum, < 10ms)
 * - Tier 2: API Fallback (obat langka, online)
 *
 * Safety: Semua data obat harus divalidasi dengan sumber resmi (BPOM/FDA).
 * Ini hanya alat bantu baca, BUKAN pengganti saran dokter/apoteker.
 */
@Suppress("unused") // Enterprise: Full API surface for medical features
class DrugDatabase(private val context: Context) {

    companion object {
        private const val TAG = "DrugDatabase"
        private const val DATABASE_FILE = "drugs_database.json"
    }

    // ══════════════════════════════════════════════════════════════════════
    // DATA CLASSES (Match JSON structure)
    // ══════════════════════════════════════════════════════════════════════

    data class DrugDatabaseJson(
        val version: String,
        val lastUpdated: String,
        val disclaimer: String,
        val drugs: List<DrugInfo>,
        val interactions: List<InteractionInfo>,
        val allergyGroups: List<AllergyGroup>,
        val categories: Map<String, CategoryInfo>
    )

    data class DrugInfo(
        val id: String,
        val brandNames: List<String>,
        val genericName: String,
        val category: String,
        val categoryDesc: String,
        val usage: String,
        val dosageAdult: String,
        val dosageChild: String,
        val warnings: List<String>,
        val contraindications: List<String>,
        val pregnancySafe: Boolean,
        val barCodes: List<String>
    )

    data class InteractionInfo(
        val drug1: String,
        val drug2: String,
        val severity: String,
        val description: String
    )

    data class AllergyGroup(
        val group: String,
        val members: List<String>,
        val crossReact: List<String>
    )

    data class CategoryInfo(
        val name: String,
        val color: String,
        val description: String,
        val symbol: String,
        val warning: String? = null
    )

    // ══════════════════════════════════════════════════════════════════════
    // Tier 0: INSTANT DRUGS (Hardcoded for sub-1ms lookup)
    // ══════════════════════════════════════════════════════════════════════

    private val instantDrugs = mapOf(
        // International Pain/Fever (Analgesics & Antipyretics)
        "paracetamol" to InstantDrug("Acetaminophen", "Pain relief and fever reduction", "OTC"),
        "tylenol" to InstantDrug("Acetaminophen", "Pain relief and fever reduction", "OTC"),
        "panadol" to InstantDrug("Acetaminophen", "Pain relief and fever reduction", "OTC"),
        "ibuprofen" to InstantDrug("Ibuprofen", "Anti-inflammatory, pain relief, and fever reduction", "OTC"),
        "advil" to InstantDrug("Ibuprofen", "Anti-inflammatory, pain relief, and fever reduction", "OTC"),
        "motrin" to InstantDrug("Ibuprofen", "Anti-inflammatory, pain relief, and fever reduction", "OTC"),
        "aspirin" to InstantDrug("Acetylsalicylic Acid", "Pain relief, fever reduction, and anti-inflammatory", "OTC"),
        "aleve" to InstantDrug("Naproxen", "Long-lasting anti-inflammatory and pain relief", "OTC"),

        // International Allergies (Antihistamines)
        "benadryl" to InstantDrug("Diphenhydramine", "Allergy relief (causes drowsiness)", "OTC"),
        "zyrtec" to InstantDrug("Cetirizine", "Allergy relief (non-drowsy)", "OTC"),
        "claritin" to InstantDrug("Loratadine", "Allergy relief (non-drowsy)", "OTC"),
        "allegra" to InstantDrug("Fexofenadine", "Allergy relief (non-drowsy)", "OTC"),

        // International Cough, Cold, & Flu
        "robitussin" to InstantDrug("Dextromethorphan + Guaifenesin", "Cough suppressant and expectorant", "OTC"),
        "mucinex" to InstantDrug("Guaifenesin", "Expectorant for chest congestion", "OTC"),
        "sudafed" to InstantDrug("Pseudoephedrine", "Nasal decongestant", "OTC/BTC"),
        "dayquil" to InstantDrug("Acetaminophen + Dextromethorphan + Phenylephrine", "Daytime cold and flu relief", "OTC"),
        "nyquil" to InstantDrug("Acetaminophen + Dextromethorphan + Doxylamine", "Nighttime cold and flu relief", "OTC"),

        // International Stomach & Digestion
        "pepto-bismol" to InstantDrug("Bismuth Subsalicylate", "Upset stomach, nausea, and diarrhea relief", "OTC"),
        "imodium" to InstantDrug("Loperamide", "Anti-diarrheal", "OTC"),
        "tums" to InstantDrug("Calcium Carbonate", "Antacid for heartburn and indigestion", "OTC"),

        // International First Aid & Supplements
        "betadine" to InstantDrug("Povidone-Iodine", "Topical antiseptic", "OTC"),
        "neosporin" to InstantDrug("Bacitracin + Neomycin + Polymyxin B", "Topical antibiotic ointment", "OTC"),
        "vitamin c" to InstantDrug("Ascorbic Acid", "Immune system support", "OTC")
    )

    data class InstantDrug(
        val genericName: String,
        val usage: String,
        val category: String
    )

    // ══════════════════════════════════════════════════════════════════════
    // Illness Recommendations (Offline Reasoning)
    // ══════════════════════════════════════════════════════════════════════

    private val offlineIllnessRecommendations = mapOf(
        "fever" to listOf("paracetamol", "tylenol", "panadol", "ibuprofen", "advil", "aspirin"),
        "pain" to listOf("paracetamol", "tylenol", "ibuprofen", "advil", "aleve", "aspirin"),
        "headache" to listOf("paracetamol", "tylenol", "ibuprofen", "advil", "aspirin"),
        "inflammation" to listOf("ibuprofen", "advil", "aleve", "aspirin"),
        "allergy" to listOf("benadryl", "zyrtec", "claritin", "allegra"),
        "allergies" to listOf("benadryl", "zyrtec", "claritin", "allegra"),
        "cough" to listOf("robitussin", "dayquil", "nyquil"),
        "congestion" to listOf("mucinex", "sudafed", "dayquil"),
        "cold" to listOf("dayquil", "nyquil", "sudafed", "robitussin"),
        "flu" to listOf("dayquil", "nyquil", "tylenol", "advil"),
        "diarrhea" to listOf("imodium", "pepto-bismol"),
        "nausea" to listOf("pepto-bismol"),
        "stomach ache" to listOf("pepto-bismol", "tums"),
        "heartburn" to listOf("tums", "pepto-bismol")
    )

    fun getRecommendationsForIllness(illness: String): List<DrugInfo> {
        val query = illness.lowercase().trim()
        val recommendations = mutableListOf<DrugInfo>()

        // Search through mapped illnesses
        for ((key, drugList) in offlineIllnessRecommendations) {
            if (query.contains(key)) {
                for (drugName in drugList) {
                    val result = lookupDrug(drugName)
                    if (result.found && result.drugInfo != null) {
                        recommendations.add(result.drugInfo)
                    }
                }
            }
        }

        return recommendations.distinctBy { it.id }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Tier 1: JSON Database
    // ══════════════════════════════════════════════════════════════════════

    private var database: DrugDatabaseJson? = null
    private var drugsMap: Map<String, DrugInfo> = emptyMap()
    private var barCodeMap: Map<String, DrugInfo> = emptyMap()

    init {
        loadDatabase()
    }

    private fun loadDatabase() {
        try {
            val jsonString = context.assets.open(DATABASE_FILE).bufferedReader().use { it.readText() }
            database = Gson().fromJson(jsonString, DrugDatabaseJson::class.java)

            // Build lookup maps
            drugsMap = database?.drugs?.flatMap { drug ->
                // Map by ID, generic name, and all brand names
                val entries = mutableListOf<Pair<String, DrugInfo>>()
                entries.add(drug.id.lowercase() to drug)
                entries.add(drug.genericName.lowercase() to drug)
                drug.brandNames.forEach { brand ->
                    entries.add(brand.lowercase() to drug)
                }
                entries
            }?.toMap() ?: emptyMap()

            // Build barcode lookup from drugs_database.json
            barCodeMap = database?.drugs?.flatMap { drug ->
                drug.barCodes.map { barCode -> barCode to drug }
            }?.toMap()?.toMutableMap() ?: mutableMapOf()

            // Also load separate barcode_database.json for extended barcode support
            loadBarCodeDatabase()

            Timber.i("$TAG: Loaded ${database?.drugs?.size ?: 0} drugs, ${barCodeMap.size} barcodes")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to load drug database")
        }
    }

    private fun loadBarCodeDatabase() {
        try {
            val jsonString = context.assets.open("barcode_database.json").bufferedReader().use { it.readText() }
            val barCodeDb = Gson().fromJson(jsonString, BarCodeDatabase::class.java)

            // Merge barcode entries
            barCodeDb?.barcodes?.forEach { (barCode, info) ->
                // Find matching drug in drugsMap
                val drugInfo = drugsMap[info.drugId.lowercase()]
                if (drugInfo != null) {
                    // Create enhanced DrugInfo with specific brand/strength
                    val enhanced = drugInfo.copy(
                        brandNames = listOf(info.brand) + drugInfo.brandNames.filter { it != info.brand }
                    )
                    (barCodeMap as MutableMap)[barCode] = enhanced
                }
            }

            Timber.i("$TAG: Loaded ${barCodeDb?.barcodes?.size ?: 0} additional barcodes")
        } catch (e: Exception) {
            Timber.w("$TAG: No barcode_database.json found, using embedded barcodes only")
        }
    }

    // Barcode database structure
    data class BarCodeDatabase(
        val version: String,
        val barcodes: Map<String, BarCodeEntry>
    )

    data class BarCodeEntry(
        val drugId: String,
        val brand: String,
        val strength: String
    )

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Lookup drug by name (brand or generic).
     * Uses tiered lookup: Tier 0 (instant) -> Tier 1 (JSON)
     */
    fun lookupDrug(name: String): DrugLookupResult {
        val query = name.lowercase().trim()

        // Tier 0: Instant lookup
        instantDrugs[query]?.let { instant ->
            return DrugLookupResult(
                found = true,
                source = "INSTANT",
                drugInfo = DrugInfo(
                    id = query,
                    brandNames = listOf(name),
                    genericName = instant.genericName,
                    category = instant.category,
                    categoryDesc = getCategoryDesc(instant.category),
                    usage = instant.usage,
                    dosageAdult = "Lihat kemasan",
                    dosageChild = "Lihat kemasan",
                    warnings = emptyList(),
                    contraindications = emptyList(),
                    pregnancySafe = false,
                    barCodes = emptyList()
                )
            )
        }

        // Tier 1: JSON lookup
        drugsMap[query]?.let { drug ->
            return DrugLookupResult(
                found = true,
                source = "LOCAL",
                drugInfo = drug
            )
        }

        // Not found locally - return for Tier 2 cloud lookup
        return DrugLookupResult(found = false, source = "NONE", drugInfo = null)
    }

    /**
     * Lookup with Tier 2 cloud fallback via OpenFDA.
     * Use this when local lookup fails.
     */
    suspend fun lookupWithFallback(name: String): DrugLookupResult {
        // Try local first
        val local = lookupDrug(name)
        if (local.found) return local

        // Tier 2: Cloud fallback disabled for offline SDK
        return DrugLookupResult(found = false, source = "NONE", drugInfo = null)
    }

    private fun cacheDrug(drug: DrugInfo) {
        // Add to in-memory map for current session
        drugsMap = drugsMap + (drug.id to drug)
        Timber.d("$TAG: Cached drug from cloud: ${drug.genericName}")
    }

    /**
     * Lookup drug by barcode.
     */
    fun lookupByBarCode(barCode: String): DrugLookupResult {
        barCodeMap[barCode]?.let { drug ->
            return DrugLookupResult(
                found = true,
                source = "BARCODE",
                drugInfo = drug
            )
        }
        return DrugLookupResult(found = false, source = "NONE", drugInfo = null)
    }

    data class DrugLookupResult(
        val found: Boolean,
        val source: String,
        val drugInfo: DrugInfo?
    )

    /**
     * Check drug-drug interaction.
     */
    fun checkInteraction(drug1: String, drug2: String): MedicalAgent.DrugInteraction? {
        val d1 = drug1.lowercase()
        val d2 = drug2.lowercase()

        val interaction = database?.interactions?.firstOrNull { info ->
            (info.drug1.lowercase() in d1 || d1 in info.drug1.lowercase()) &&
            (info.drug2.lowercase() in d2 || d2 in info.drug2.lowercase())
        } ?: database?.interactions?.firstOrNull { info ->
            // Check reverse order
            (info.drug1.lowercase() in d2 || d2 in info.drug1.lowercase()) &&
            (info.drug2.lowercase() in d1 || d1 in info.drug2.lowercase())
        }

        return interaction?.let {
            MedicalAgent.DrugInteraction(
                drug1 = it.drug1,
                drug2 = it.drug2,
                severity = parseSeverity(it.severity),
                description = it.description
            )
        }
    }

    /**
     * Check if user is allergic to this drug based on their allergy profile.
     */
    fun checkAllergy(drugName: String, userAllergies: List<String>): AllergyCheckResult {
        val query = drugName.lowercase()

        for (allergy in userAllergies) {
            val allergyLower = allergy.lowercase()

            // Direct match
            if (query.contains(allergyLower) || allergyLower.contains(query)) {
                return AllergyCheckResult(
                    isAllergic = true,
                    allergen = allergy,
                    type = "DIRECT",
                    message = "⛔ BAHAYA! Anda ALERGI terhadap $allergy!"
                )
            }

            // Check allergy groups (e.g., penicillin group)
            database?.allergyGroups?.forEach { group ->
                if (group.group.lowercase() == allergyLower || group.members.any { it.lowercase() == allergyLower }) {
                    // User is allergic to this group, check if drug is in group
                    if (group.members.any { query.contains(it.lowercase()) }) {
                        return AllergyCheckResult(
                            isAllergic = true,
                            allergen = allergy,
                            type = "GROUP",
                            message = "⛔ BAHAYA! Obat ini termasuk golongan ${group.group}. Anda ALERGI terhadap $allergy!"
                        )
                    }

                    // Check cross-reactivity
                    group.crossReact.forEach { crossGroup ->
                        database?.allergyGroups?.find { it.group.lowercase() == crossGroup.lowercase() }?.let { crossGroupInfo ->
                            if (crossGroupInfo.members.any { query.contains(it.lowercase()) }) {
                                return AllergyCheckResult(
                                    isAllergic = true,
                                    allergen = allergy,
                                    type = "CROSS_REACT",
                                    message = "⚠️ PERINGATAN! Obat ini ($crossGroup) dapat bereaksi silang dengan alergi ${group.group} Anda!"
                                )
                            }
                        }
                    }
                }
            }
        }

        return AllergyCheckResult(isAllergic = false, allergen = null, type = "SAFE", message = null)
    }

    data class AllergyCheckResult(
        val isAllergic: Boolean,
        val allergen: String?,
        val type: String,
        val message: String?
    )

    /**
     * Get category warning for hard drugs (K, P, N).
     */
    fun getCategoryWarning(category: String): String? {
        return database?.categories?.get(category)?.warning
    }

    /**
     * Get disclaimer text.
     */
    fun getDisclaimer(): String {
        return database?.disclaimer ?: "Informasi ini hanya alat bantu baca, BUKAN pengganti saran dokter/apoteker."
    }

    /**
     * Search drugs by partial name.
     */
    fun searchDrugs(query: String, limit: Int = 10): List<DrugInfo> {
        val queryLower = query.lowercase()

        return database?.drugs?.filter { drug ->
            drug.id.lowercase().contains(queryLower) ||
            drug.genericName.lowercase().contains(queryLower) ||
            drug.brandNames.any { it.lowercase().contains(queryLower) }
        }?.take(limit) ?: emptyList()
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private fun getCategoryDesc(category: String): String {
        return when (category) {
            "B" -> "Bebas"
            "BT" -> "Bebas Terbatas"
            "K" -> "Keras (Resep Dokter)"
            "P" -> "Psikotropika"
            "N" -> "Narkotika"
            else -> "Unknown"
        }
    }

    private fun parseSeverity(severity: String): MedicalAgent.Severity {
        return when (severity.uppercase()) {
            "CRITICAL" -> MedicalAgent.Severity.CRITICAL
            "HIGH" -> MedicalAgent.Severity.HIGH
            "MODERATE" -> MedicalAgent.Severity.MODERATE
            else -> MedicalAgent.Severity.LOW
        }
    }
}
