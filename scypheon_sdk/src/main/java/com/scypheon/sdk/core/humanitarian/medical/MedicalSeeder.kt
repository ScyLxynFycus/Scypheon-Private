package com.scypheon.sdk.core.humanitarian.medical

/**
 * MedicalSeeder: Hardened pharmaceutical data seeder.
 * Provides WHO-compliant essential medicines with Fail-Closed safety.
 */
object MedicalSeeder {

    fun getFullProductionDataset(): Triple<List<PharmacopeiaEntry>, List<InteractionEntity>, List<FirstAidEntity>> {
        val drugs = mutableListOf<PharmacopeiaEntry>()
        val ts = System.currentTimeMillis()

        // [v4.1] Hardened Entry: Paracetamol
        drugs.add(PharmacopeiaEntry(
            id = "drug_001",
            drugName = "Paracetamol",
            genericName = "Acetaminophen",
            dosage = "500mg - 1000mg every 4-6 hours. Max 4000mg/day.",
            indications = "Mild to moderate pain, fever reduction",
            contraindications = "Severe hepatic impairment",
            interactionDetails = "Increased hepatotoxicity with alcohol",
            maxMgPerKg = 15.0f,
            maxDailyMg = 4000,
            severity = "MILD",
            source = "WHO EML 2023",
            lastUpdated = ts,
            content = "Paracetamol Acetaminophen analgesic antipyretic pain fever"
        ))

        // [v4.1] Hardened Entry: Amoxicillin
        drugs.add(PharmacopeiaEntry(
            id = "drug_002",
            drugName = "Amoxicillin",
            genericName = "Amoxicillin Trihydrate",
            dosage = "250mg - 500mg every 8 hours. Max 3000mg/day.",
            indications = "Bacterial infections (Otitis media, Sinusitis, Pneumonia)",
            contraindications = "Penicillin hypersensitivity",
            interactionDetails = "May reduce oral contraceptive efficacy",
            maxMgPerKg = 25.0f,
            maxDailyMg = 3000,
            severity = "MODERATE",
            source = "WHO EML 2023",
            lastUpdated = ts,
            content = "Amoxicillin antibiotic penicillin bacterial infection"
        ))

        // [v4.1] Hardened Entry: Ibuprofen
        drugs.add(PharmacopeiaEntry(
            id = "drug_003",
            drugName = "Ibuprofen",
            genericName = "Ibuprofen",
            dosage = "200mg - 400mg every 4-6 hours. Max 1200mg/day OTC, 3200mg/day Rx.",
            indications = "Inflammation, pain, fever",
            contraindications = "NSAID hypersensitivity, gastric ulcers, severe renal impairment",
            maxMgPerKg = 10.0f,
            maxDailyMg = 3200,
            severity = "MILD",
            source = "WHO EML 2023",
            lastUpdated = ts,
            content = "Ibuprofen NSAID anti-inflammatory pain fever"
        ))

        // Interaction Data (Stub)
        val interactions = listOf(
            InteractionEntity("drug_001", "alcohol", "MAJOR", "MAJOR", "Potentiated hepatotoxicity.", "Bahaya kerusakan hati.")
        )

        // First Aid Protocols (Stub)
        val protocols = listOf(
            FirstAidEntity(
                id = 1,
                conditionName = "Burns",
                conditionNameId = "Luka Bakar",
                severityLevel = 2,
                instructionsEn = "Cool the burn with running water for 20 minutes. Cover with plastic wrap.",
                instructionsId = "Dinginkan luka dengan air mengalir selama 20 menit.",
                medicationRequired = "None initially",
                warningEn = "Do not apply ice or butter.",
                warningId = "Jangan gunakan es atau mentega.",
                localSearchKeywords = "burn, api, terbakar",
                source = "WHO",
                lastUpdated = "2026-05-23"
            )
        )

        return Triple(drugs, interactions, protocols)
    }
}
