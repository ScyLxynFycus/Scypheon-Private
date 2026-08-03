package com.scypheon.sdk.core.humanitarian.medical

/**
 * 🛡️ SCYPHEON ENTERPRISE MEDICAL REGISTRY (v4.0)
 * --------------------------------------------------
 * MANDATE: FAIL-CLOSED SAFETY / PRECISION PHARMA
 * ENRICHMENT: maxMgPerKg, maxSingleDoseMg
 * VERIFICATION: 2026-05-14 09:17:39 (UTC)
 * --------------------------------------------------
 */
object MedicalSeeder {

    fun getFullProductionDataset(): Triple<List<PharmacopeiaEntry>, List<InteractionEntity>, List<FirstAidEntity>> {
        val drugs = mutableListOf<PharmacopeiaEntry>()
        val ts = 1778750259527L

        // REGISTRY NO: 001 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "ee8e3aa2-8550-41c7-9911-0723b3fb2f87",
            drugName = "Pain Reliever Extra Strength",
            genericName = "Acetaminophen",
            dosage = "Directions do not take more than directed adults and children 12 years and over take 2 gelcaps every 6 hours while symptoms last do not take more than 6 gelcaps in 24 hours, unless directed by a doctor do not take for more than 10 days unless directed by a doctor children under 12 years: ask a doctor",
            indications = "Uses temporarily relieves minor aches and pains due to: headache the common cold backache minor pain of arthritis toothache muscular aches premenstrual and menstrual cramps temporarily reduces fever",
            contraindications = "Standard precautions",
            maxMgPerKg = 15.0f,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = 1000.0,
            source = "OpenFDA/WHO 2.1",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N02BE01",
            route = "ORAL",
            storageConditions = "Other information store at 25°C (77°F); excursions permitted between 15°-30°C (59°-86°F) avoid high humidity see end flap for expiration date and lot number",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 002 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "4bd1345d-d116-426e-825f-bbf29381746a",
            drugName = "Ibuprofen Dye Free",
            genericName = "Ibuprofen",
            dosage = "Directions do not take more than directed the smallest effective dose should be used adults and children 12 years and over: take 1 tablet every 4 to 6 hours while symptoms persist if pain or fever does not respond to 1 tablet, 2 tablets may be used do not exceed 6 tablets in 24 hours, unless directed by a doctor children under 12 years: ask a doctor",
            indications = "Uses temporarily relieves minor aches and pains due to: headache toothache backache menstrual cramps the common cold muscular aches minor pain of arthritis temporarily reduces fever",
            contraindications = "Standard precautions",
            maxMgPerKg = 10.0f,
            maxDailyMg = 3200.0,
            maxSingleDoseMg = 800.0,
            source = "OpenFDA/WHO 2.1",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "M01AE01",
            route = "ORAL",
            storageConditions = "Other information store between 20°-25°C (68°-77°F) avoid excessive heat 40°C (104°F) see end flap for expiration date and lot number",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 003 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "008ee85b-5cac-45a6-a857-a828f8125175",
            drugName = "Low Dose Aspirin",
            genericName = "Aspirin",
            dosage = "Directions drink a full glass of water with each dose adults and children 12 years and over: take 4 to 8 tablets every 4 hours not to exceed 48 tablets in 24 hours unless directed by a doctor children under 12 years: consult a doctor",
            indications = "Uses for the temporary relief of minor aches and pains or as recommended by your doctor. Because of its delayed action, this product will not provide fast relief of headaches or other symptoms needing immediate relief. ask your doctor about other uses for safety coated 81 mg aspirin",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = 1000.0,
            source = "OpenFDA/WHO 2.1",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N02BA01",
            route = "ORAL",
            storageConditions = "Other information store between 15-30ºC (59-86ºF)",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 004 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "8c45ef1f-f708-485b-bc20-60aa87ce6289",
            drugName = "Naproxen",
            genericName = "Naproxen",
            dosage = "2 DOSAGE AND ADMINISTRATION Use the lowest effective dosage for shortest duration consistent with individual patient treatment goals. ( 2.1 ) Rheumatoid Arthritis, Osteoarthritis, and Ankylosing Spondylitis Naproxen tablets 250 mg (one-half tablet) 500 mg twice daily Naproxen sodium tablets 275 mg (one-half tablet) 550 mg twice daily The dose may be adjusted up or down depending on the clinical response of the patient. In patients who tolerate lower doses well, the dose may be increased to naproxen 1500 mg/ day for up to 6 months. Polyarticular Juvenile Idiopathic Arthritis Naproxen tablets may not allow for the flexible dose titration needed in pediatric patients with polyarticular juvenile idiopathic arthritis. A liquid formulation may be more appropriate. Recommended total daily dose of",
            indications = "1 INDICATIONS AND USAGE Naproxen tablets and naproxen sodium tablets are indicated for: the relief of the signs and symptoms of: • rheumatoid arthritis • osteoarthritis • ankylosing spondylitis • Polyarticular Juvenile Idiopathic Arthritis Naproxen tablets and naproxen sodium tablets are also indicated for: the relief of signs and symptoms of: • tendonitis • bursitis • acute gout the management of: • pain • primary dysmenorrhea Naproxen tablets and naproxen sodium tablets are non-steroidal anti-inflammatory drugs indicated for: the relief of the signs and symptoms of: • rheumatoid arthritis • osteoarthritis • ankylosing spondylitis • polyarticular juvenile idiopathic arthritis Naproxen tablets and naproxen sodium tablets are also indicated for: the relief of signs and symptoms of: • tendon",
            contraindications = "4 CONTRAINDICATIONS Naproxen tablets and naproxen sodium tablets are contraindicated in the following patients: • Known hypersensitivity (e.g., anaphylactic reactions and serious skin reactions) to naproxen or any components of the drug product [ see Warnings and Precautions ( 5.7 , 5.9 ) ] • History of asthma, urticaria, or other allergic-type reactions after taking aspirin or other NSAIDs. Severe, sometimes fatal, anaphylactic reactions to NSAIDs have been reported in such patients [ see Warnings and Precautions ( 5.7 , 5.8 ) ] • In the setting of coronary artery bypass graft (CABG) surgery [ see Warnings and Precautions ( 5.1 ) ] • Known hypersensitivity to naproxen or any components of the drug product ( 4 ) • History of asthma, urticaria, or other allergic-type reactions after taking",
            maxMgPerKg = 15.0f,
            maxDailyMg = 1500.0,
            maxSingleDoseMg = 500.0,
            source = "OpenFDA/WHO 2.1",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "M01AE02",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 005 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "322eb217-089d-e92d-e063-6394a90a6566",
            drugName = "Childrens Zyrtec",
            genericName = "Cetirizine",
            dosage = "Directions may be taken with or without water chew or crush tablets completely before swallowing children 2 to under 6 years of age Chew and swallow 1 tablet (2.5 mg) once daily; If needed, dose can be increased to a maximum of 2 tablets (5 mg) once daily or 1 tablet (2.5 mg) every 12 hours. Do not give more than 2 tablets (5 mg) in 24 hours. adults and children 6 years and over Chew and swallow 2 tablets (5 mg) or 4 tablets (10 mg) once daily depending upon severity of symptoms; do not take more than 4 tablets (10 mg) in 24 hours. adults 65 years and over Chew and swallow 2 tablets (5 mg) once daily; do not take more than 2 tablets (5 mg) in 24 hours. children under 2 years of age ask a doctor consumers with liver or kidney disease ask a doctor",
            indications = "Uses temporarily relieves these symptoms due to hay fever or other upper respiratory allergies: runny nose sneezing itchy, watery eyes itching of the nose or throat",
            contraindications = "Standard precautions",
            maxMgPerKg = 0.25f,
            maxDailyMg = 10.0,
            maxSingleDoseMg = 10.0,
            source = "OpenFDA/WHO 2.1",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "R06AE07",
            route = "ORAL",
            storageConditions = "Other information store between 20° to 25°C (68° to 77°F) do not use if blister unit is torn or broken",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 006 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "4fd50f17-7237-4659-bad6-a9e6189fa37c",
            drugName = "allergy relief",
            genericName = "Loratadine",
            dosage = "Directions adults and children 6 years and over 1 tablet daily; not more than 1 tablet in 24 hours children under 6 years of age ask a doctor consumers with liver or kidney disease ask a doctor",
            indications = "Uses temporarily relieves these symptoms due to hay fever or other upper respiratory allergies: • runny nose • itchy, watery eyes • sneezing • itching of the nose or throat",
            contraindications = "Standard precautions",
            maxMgPerKg = 0.2f,
            maxDailyMg = 10.0,
            maxSingleDoseMg = 10.0,
            source = "OpenFDA/WHO 2.1",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "R06AX13",
            route = "ORAL",
            storageConditions = "Other information • do not use if printed foil under cap is broken or missing • store between 20 ° to 25 ° C (68 ° to 77 ° F)",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 007 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "448524ca-2fda-44ab-b1f8-edf8db006713",
            drugName = "Childrens Allergy Relief",
            genericName = "Diphenhydramine",
            dosage = "Directions ■ if needed, take every 4-6 hours ■ do not take more than 6 doses in 24-hours children under 4 years of age Do not use children 4 to under 6 years of age Do not use unless directed by a doctor children 6 to under 12 years of age 1 to 2 teaspoonfuls (5 ml to 10 ml)",
            indications = "Uses temporarily relieves these symptoms due to hay fever or other respiratory allergies: ■ sneezing ■ itching of the nose or throat ■ runny nose ■ itchy watery eyes ■temporarily relieves these symptoms due to the common cold: ■ sneezing ■ runny nose",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 2.1",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 008 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "411e99da-2482-40c7-e063-6394a90aaf80",
            drugName = "Rompe Pechito DM for Kids",
            genericName = "Guaifenesin",
            dosage = "Directions Do not give more than 6 doses in any 24 hours period  shake well before use  measure only with dosing cup provided  keep dosing cup with product  mL = milliliter Age Dose Children 6 to under 12 years of age 5 mL every 4 hours Children 4 to under 6 years of age 2.5 mL every 4 hours Children under 4 years of age Do not use",
            indications = "Uses Uses: temporarily relieves cough due to minor throat and bronchial irritation as may occur with a cold helps loosen phlegm (mucus) and thin bronchial secretions to drain bronchial tubes.",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 2.1",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 009 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "bbbf9415-a533-1629-8f52-3f286fdc2d46",
            drugName = "Omeprazole",
            genericName = "Omeprazole",
            dosage = "Directions for adults 18 years of age and older this product is to be used once a day (every 24 hours), every day for 14 days it may take 1 to 4 days for full effect; some people get complete relief of symptoms within 24 hours 14-Day Course of Treatment swallow 1 tablet with a glass of water before eating in the morning take every day for 14 days do not take more than 1 tablet a day do not use for more than 14 days unless directed by your doctor swallow whole. Do not chew or crush tablets Repeated 14-Day Courses (if needed) you may repeat a 14-day course every 4 months do not take for more than 14 days or more often than every 4 months unless directed by a doctor children under 18 years of age: ask a doctor. Heartburn in children may sometimes be caused by a serious condition.",
            indications = "Use(s) treats frequent heartburn (occurs 2 or more days a week) not intended for immediate relief of heartburn; this drug may take 1 to 4 days for full effect",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 2.1",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 010 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "41c182b7-7b97-e23a-e063-6394a90ab53f",
            drugName = "Famotidine",
            genericName = "Famotidine",
            dosage = "2 DOSAGE AND ADMINISTRATION Indication Recommended Dosage ( 2.1 ) Adult and Pediatric Patients 40 kg and greater Active DU 40 mg once daily; or 20 mg twice daily Active Gastric Ulcer 40 mg once daily GERD 20 mg twice daily Erosive Esophagitis 20 mg twice daily; or 40 mg twice daily Adults Pathological Hypersecretory Conditions 20 mg every 6 hours; adjust to patient needs; maximum 160 mg every 6 hours Risk Reduction of DU Recurrence 20 mg once daily • See full prescribing information for complete dosing information, including dosing in renal impairment, and recommended treatment duration. ( 2.1 , 2.2 ) Administration ( 2.3 ): • Take once daily before bedtime or twice daily in the morning and before bedtime with or without food. 2.1 Recommended Dosage Table 1 shows the recommended dosage of",
            indications = "1 INDICATIONS AND USAGE Famotidine tablets are indicated in adult and pediatric patients 40 kg and greater for the treatment of: • active duodenal ulcer (DU). • active gastric ulcer (GU). • symptomatic nonerosive gastroesophageal reflux disease (GERD). • erosive esophagitis due to GERD, diagnosed by biopsy. Famotidine tablets are indicated in adults for the: • treatment of pathological hypersecretory conditions (e.g., Zollinger-Ellison syndrome, multiple endocrine neoplasias). • reduction of the risk of duodenal ulcer recurrence. Famotidine tablets are a histamine-2 (H 2 ) receptor antagonist indicated ( 1 ): In adult and pediatric patients 40 kg and greater for the treatment of: • active duodenal ulcer (DU). • active gastric ulcer. • symptomatic nonerosive gastroesophageal reflux disease",
            contraindications = "4 CONTRAINDICATIONS Famotidine tablets are contraindicated in patients with a history of serious hypersensitivity reactions (e.g., anaphylaxis) to famotidine or other histamine-2 (H 2 ) receptor antagonists. History of serious hypersensitivity reactions (e.g., anaphylaxis) to famotidine or other H 2 receptor antagonists. ( 4 )",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 2.1",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 011 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "2d263950-4148-405d-b582-13857ac88044",
            drugName = "MORPHINE SULFATE",
            genericName = "Morphine",
            dosage = "2 DOSAGE AND ADMINISTRATION Morphine sulfate tablets should be prescribed only by healthcare professionals who are knowledgeable about the use of opioids and how to mitigate the associated risks. ( 2.1 ) Use the lowest effective dosage for the shortest duration of time consistent with individual patient treatment goals. Reserve titration to higher doses of morphine sulfate tablets for patients in whom lower doses are insufficiently effective and in whom the expected benefits of using a higher dose opioid clearly outweigh the substantial risks. ( 2.1 , 5 ) Many acute pain conditions (e.g., the pain that occurs with a number of surgical procedures or acute musculoskeletal injuries) require no more than a few days of an opioid analgesic. Clinical guidelines on opioid prescribing for some acut",
            indications = "1 INDICATIONS AND USAGE Morphine sulfate tablets are indicated for the management of: adult and pediatric patients weighing at least 50 kg and above with acute pain severe enough to require an opioid analgesic and for which alternative treatments are inadequate. adults with chronic pain severe enough to require an opioid analgesic and for which alternative treatments are inadequate. Limitations of Use: Because of the risks of addiction, abuse, and misuse with opioids, which can occur at any dosage or duration, [see Warnings and Precautions ( 5.1 )], reserve morphine sulfate tablets for use in patients for whom alternative treatment options (e.g., non-opioid analgesics or opioid combination products): Have not been tolerated or are not expected to be tolerated, Have not provided adequate an",
            contraindications = "4 CONTRAINDICATIONS Morphine sulfate tablets are contraindicated in patients with: Significant respiratory depression [see Warnings and Precautions ( 5.2 )]. Acute or severe bronchial asthma in an unmonitored setting or in the absence of resuscitative equipment [see Warnings and Precautions ( 5.7 )]. Concurrent use of monoamine oxidase inhibitors (MAOIs) or use of MAOIs within the last 14 days [see Warnings and Precautions (5.8) and Drug Interactions ( 7 )]. Known or suspected gastrointestinal obstruction, including paralytic ileus [see Warnings and Precautions ( 5.12 )]. Hypersensitivity to morphine (e.g., anaphylaxis) [see Adverse Reactions ( 6 )]. Significant respiratory depression. ( 4 ) Acute or severe bronchial asthma in an unmonitored setting or in absence of resuscitative equipment",
            maxMgPerKg = 0.2f,
            maxDailyMg = 60.0,
            maxSingleDoseMg = 10.0,
            source = "OpenFDA/WHO 2.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N02AA01",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 012 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "a47a3a31-e293-49d5-b6bc-950303525a64",
            drugName = "Fentanyl Citrate",
            genericName = "Fentanyl",
            dosage = "2 DOSAGE AND ADMINISTRATION • Fentanyl Citrate Injection should be administered only by persons specifically trained in the use of intravenous anesthetics and management of the respiratory effects of potent opioids. • Ensure that an opioid antagonist, resuscitative and intubation equipment, and oxygen are readily available ( 2.1 ). • Individualize dosing based on the factors such as age, body weight, physical status, underlying pathological condition, use of other drugs, type of anesthesia to be used, and the surgical procedure involved. ( 2.1 ) • Initiate treatment in adults with 50 mcg to 100 mcg. ( 2.2 ) • Initiate treatment in children 2 to 12 years of age, with a reduced dose as low as 2 mcg/kg to 3 mcg/kg. ( 2.2 ) 2.1 Important Dosage and Administration Instructions Fentanyl Citrate",
            indications = "1 INDICATIONS AND USAGE Fentanyl Citrate Injection is indicated for: • analgesic action of short duration during the anesthetic periods, premedication, induction and maintenance, and in the immediate postoperative period (recovery room) as the need arises. • use as a narcotic analgesic supplement in general or regional anesthesia. • administration with a neuroleptic as an anesthetic premedication, for the induction of anesthesia and as an adjunct in the maintenance of general and regional anesthesia. • use as an anesthetic agent with oxygen in selected high-risk patients, such as those undergoing open heart surgery or certain complicated neurological or orthopedic procedures. Fentanyl Citrate Injection is indicated for: • analgesic action of short duration during the anesthetic periods, pr",
            contraindications = "4 CONTRAINDICATIONS Fentanyl Citrate Injection is contraindicated in patients with: • Hypersensitivity to fentanyl (e.g., anaphylaxis) [See Adverse Reactions ( 6 )] • Hypersensitivity to fentanyl ( 4 )",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 2.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "INTRAMUSCULAR",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 013 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "a994e116-d201-47f9-baaf-dd76a84eba7e",
            drugName = "TRAMADOL HYDROCHLORIDE",
            genericName = "Tramadol",
            dosage = "2 DOSAGE AND ADMINISTRATION Tramadol hydrochloride extended-release tablets should be prescribed only by healthcare professionals who are knowledgeable about the use of extended-release/long-acting opioids and how to mitigate the associated risks. (2.1) Use the lowest effective dosage for the shortest duration of time consistent with individual patient treatment goals. Reserve titration to higher doses of tramadol hydrochloride extended-release tablets for patients in whom lower doses are insufficiently effective and in whom the expected benefits of using a higher dose opioid clearly outweigh the substantial risks. (2.1, 5) Initiate the dosing regimen for each patient individually, taking into account the patient’s underlying cause and severity of pain, prior analgesic treatment and respon",
            indications = "1 INDICATIONS AND USAGE Tramadol hydrochloride extended-release tablets are indicated for the management of severe and persistent pain that requires an opioid analgesic and that cannot be adequately treated with alternative options, including immediate-release opioids. Limitations of Use • Because of the risks of addiction, abuse, misuse, overdose, and death, which can occur at any dosages or duration, and persist over the course of therapy, [see Warnings and Precautions (5.1)], reserve opioid analgesics, including tramadol hydrochloride extended-release tablets, for use in patients for whom alternative treatment options are ineffective, not tolerated or would be otherwise inadequate to provide sufficient management of pain. • Tramadol hydrochloride extended-release tablets are not indicat",
            contraindications = "4 CONTRAINDICATIONS Tramadol hydrochloride extended-release tablets are contraindicated for: all children younger than 12 years of age [see Warnings and Precautions (5.4)] post-operative management in children younger than 18 years of age following tonsillectomy and/or adenoidectomy [see Warnings and Precautions (5.4)] . Tramadol hydrochloride extended-release tablets are also contraindicated in patients with: Significant respiratory depression [see Warnings and Precautions (5.3)] Acute or severe bronchial asthma in an unmonitored setting or in the absence of resuscitative equipment [see Warnings and Precautions (5.12)] Known or suspected gastrointestinal obstruction, including paralytic ileus [see Warnings and Precautions (5.15)] Hypersensitivity to tramadol (e.g., anaphylaxis) [see Warni",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 2.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 014 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "b04f74f8-d960-4b7a-988a-372d9a1e1406",
            drugName = "Codeine sulfate",
            genericName = "Codeine",
            dosage = "2 DOSAGE AND ADMINISTRATION • Codeine Sulfate Tablets should be prescribed only by healthcare professionals who are knowledgeable about the use of opioids and how to mitigate the associated risks. ( 2.1 ) • Use the lowest effective dosage for the shortest duration of time consistent with individual patient treatment goals. Reserve titration to higher doses of Codeine Sulfate Tablets for patients in whom lower doses are insufficiently effective and in whom the expected benefits of using a higher dose opioid clearly outweigh the substantial risks. ( 2.1 , 5 ) • Many acute pain conditions (e.g., the pain that occurs with a number of surgical procedures or acute musculoskeletal injuries) require no more than a few days of an opioid analgesic. Clinical guidelines on opioid prescribing for some",
            indications = "1 INDICATIONS AND USAGE Codeine Sulfate Tablets are indicated for the management of mild to moderate pain, where treatment with an opioid is appropriate and for which alternative treatments are inadequate. Limitations of Use: • Because of the risks of addiction, abuse, misuse, overdose, and death, which can occur at any dosage or duration and persist over the course of therapy [see Warnings and Precautions ( 5.1 )] , reserve opioid analgesics, including Codeine Sulfate Tablets, for use in patients for whom alternative treatment options are ineffective, not tolerated, or would be otherwise inadequate to provide sufficient management of pain. Codeine Sulfate Tablets are an opioid agonist, indicated for the management of mild to moderate pain, where treatment with an opioid is appropriate and",
            contraindications = "4 CONTRAINDICATIONS Codeine Sulfate Tablets are contraindicated for: • All children younger than 12 years of age [see Warnings and Precautions ( 5.6 )] . • Post-operative management in children younger than 18 years of age following tonsillectomy and/or adenoidectomy [see Warnings and Precautions ( 5.6 )] . Codeine Sulfate Tablets are also contraindicated in patients with: • Significant respiratory depression [see Warnings and Precautions ( 5.2 )]. • Acute or severe bronchial asthma in an unmonitored setting or in the absence of resuscitative equipment [see Warnings and Precautions ( 5.9 )]. • Concurrent use of monoamine oxidase inhibitors (MAOIs) or use of MAOIs within the last 14 days [see Warnings and Precautions ( 5.10 ), Drug Interactions ( 7 )]. • Known or suspected gastrointestinal",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 2.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 015 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "a2a30785-599c-4436-a3cc-b4db011b7a87",
            drugName = "AMOXICILLIN AND CLAVULANATE POTASSIUM",
            genericName = "Amoxicillin",
            dosage = "2 DOSAGE AND ADMINISTRATION Adults and Pediatric Patients greater than 40 kg: 500 or 875 mg every 12 hours or 250 or 500 mg every 8 hours, based on the amoxicillin component. ( 2.2 , 2.3 ) Pediatric patients aged 12 weeks (3 months) and older: 25 to 45 mg/kg/day every 12 hours or 20 to 40 mg/kg/day every 8 hours, up to the adult dose. ( 2.3 ) Neonates and infants less than 12 weeks of age: 30 mg/kg/day divided every 12 hours, based on the amoxicillin component. Use of the 125 mg/5 mL oral suspension is recommended. ( 2.3 ) 2.1 Important Administration Instructions Amoxicillin and Clavulanate Potassium may be taken without regard to meals; however, absorption of clavulanate potassium is enhanced when Amoxicillin and Clavulanate Potassium is administered at the start of a meal. To minimize t",
            indications = "1 INDICATIONS AND USAGE Amoxicillin and Clavulanate Potassium is indicated for the treatment of infections in adults and pediatric patients, due to susceptible isolates of the designated bacteria in the conditions listed below: Lower Respiratory Tract Infections - caused by beta‑lactamase–producing isolates of Haemophilus influenzae and Moraxella catarrhalis . Acute Bacterial Otitis Media - caused by beta‑lactamase–producing isolates of H. influenzae and M. catarrhalis . Sinusitis - caused by beta‑lactamase–producing isolates of H. influenzae and M. catarrhalis . Skin and Skin Structure Infections - caused by beta‑lactamase–producing isolates of Staphylococcus aureus , Escherichia coli , and Klebsiella species. Urinary Tract Infections - caused by beta‑lactamase–producing isolates of E. co",
            contraindications = "4 CONTRAINDICATIONS History of a serious hypersensitivity reaction (e.g., anaphylaxis or Stevens-Johnson syndrome) to Amoxicillin and Clavulanate Potassium or to other beta‑lactams (e.g., penicillins or cephalosporins). ( 4.1 ) History of cholestatic jaundice/hepatic dysfunction associated with Amoxicillin and Clavulanate Potassium. ( 4.2 ) 4.1 Serious Hypersensitivity Reactions Amoxicillin and Clavulanate Potassium is contraindicated in patients with a history of serious hypersensitivity reactions (e.g., anaphylaxis or Stevens-Johnson syndrome) to amoxicillin, clavulanate or to other beta‑lactam antibacterial drugs (e.g., penicillins and cephalosporins). 4.2 Cholestatic Jaundice/Hepatic Dysfunction Amoxicillin and Clavulanate Potassium is contraindicated in patients with a previous histor",
            maxMgPerKg = 30.0f,
            maxDailyMg = 3000.0,
            maxSingleDoseMg = 1000.0,
            source = "OpenFDA/WHO 6.2",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "J01CA04",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "CATEGORY B"
        ))

        // REGISTRY NO: 016 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "a797de06-c8be-4417-9bac-137dc4931c8b",
            drugName = "ciprofloxacin",
            genericName = "Ciprofloxacin",
            dosage = "DOSAGE AND ADMINISTRATION Corneal Ulcers: The recommended dosage regimen for the treatment of corneal ulcers is two drops into the affected eye every 15 minutes for the first six hours and then two drops into the affected eye every 30 minutes for the remainder of the first day. On the second day, instill two drops in the affected eye hourly. On the third through the fourteenth day, place two drops in the affected eye every four hours. Treatment may be continued after 14 days if corneal re-epithelialisation has not occurred. Bacterial Conjunctivitis: The recommended dosage regimen for the treatment of bacterial conjunctivitis is one or two drops instilled into the conjunctival sac(s) every two hours while awake for two days and one or two drops every four hours while awake for the next five",
            indications = "INDICATIONS AND USAGE Ciprofloxacin Ophthalmic Solution is indicated for the treatment of infections caused by susceptible strains of the designated microorganisms in the conditions listed below: Corneal Ulcers: Pseudomonas aeruginosa Serratia marcescens * Staphylococcus aureus Staphylococcus epidermidis Streptococcus pneumoniae Streptococcus (Viridans Group) * Conjunctivitis: Haemophilus influenzae Staphylococcus aureus Staphylococcus epidermidis Streptococcus pneumoniae *Efficacy for this organism was studied in fewer than 10 infections.",
            contraindications = "CONTRAINDICATIONS A history of hypersensitivity to ciprofloxacin or any other component of the medication is a contraindication to its use. A history of hypersensitivity to other quinolones may also contraindicate the use of ciprofloxacin.",
            maxMgPerKg = 15.0f,
            maxDailyMg = 1500.0,
            maxSingleDoseMg = 750.0,
            source = "OpenFDA/WHO 6.2",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "J01MA02",
            route = "OPHTHALMIC",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 017 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "6efa1622-de6a-4eee-b5bb-6a8149bbe1c1",
            drugName = "Azithromycin",
            genericName = "Azithromycin",
            dosage = "2 DOSAGE AND ADMINISTRATION • Adult Patients ( ) Infection Recommended Dose/Duration of Therapy Community-acquired pneumonia (mild severity) Pharyngitis/tonsillitis (second-line therapy) Skin/skin structure (uncomplicated) 500 mg as a single dose on Day 1, followed by 250 mg once daily on Days 2 through 5. Acute bacterial exacerbations of chronic bronchitis (mild to moderate) 500 mg as a single dose on Day 1, followed by 250 mg once daily on Days 2 through 5 or 500 mg once daily for 3 days. Acute bacterial sinusitis 500 mg once daily for 3 days. Genital ulcer disease (chancroid) Non-gonococcal urethritis and cervicitis One single 1 gram dose. Gonococcal urethritis and cervicitis One single 2 gram dose. • Pediatric Patients ( ) Infection Recommended Dose/Duration of Therapy Acute otitis med",
            indications = "1 INDICATIONS AND USAGE Azithromycin is a macrolide antibacterial drug indicated for the treatment of patients with mild to moderate infections caused by susceptible strains of the designated microorganisms in the specific conditions listed below. Recommended dosages and durations of therapy in adult and pediatric patient populations vary in these indications. [see Dosage and Administration (2) ] Azithromycin is a macrolide antibacterial drug indicated for mild to moderate infections caused by designated, susceptible bacteria: • Acute bacterial exacerbations of chronic bronchitis in adults () • Acute bacterial sinusitis in adults () • Uncomplicated skin and skin structure infections in adults () • Urethritis and cervicitis in adults () • Genital ulcer disease in men () • Acute otitis media",
            contraindications = "4 CONTRAINDICATIONS • Patients with known hypersensitivity to azithromycin, erythromycin, any macrolide or ketolide drug. () • Patients with a history of cholestatic jaundice/hepatic dysfunction associated with prior use of azithromycin. () 4.1 Hypersensitivity Azithromycin tablets are contraindicated in patients with known hypersensitivity to azithromycin, erythromycin, any macrolide or ketolide drug. 4.2 Hepatic Dysfunction Azithromycin tablets are contraindicated in patients with a history of cholestatic jaundice/hepatic dysfunction associated with prior use of azithromycin.",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 6.2",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 018 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "cc7743b1-4e7b-446a-9ec1-cc234a8bbccf",
            drugName = "Ceftriaxone Sodium",
            genericName = "Ceftriaxone",
            dosage = "DOSAGE AND ADMINISTRATION Ceftriaxone may be administered intravenously or intramuscularly. Do not use diluents containing calcium, such as Ringer’s solution or Hartmann’s solution, to reconstitute ceftriaxone vials or to further dilute a reconstituted vial for IV administration because a precipitate can form. Precipitation of ceftriaxone-calcium can also occur when ceftriaxone is mixed with calcium-containing solutions in the same IV administration line. Ceftriaxone must not be administered simultaneously with calcium-containing IV solutions, including continuous calcium-containing infusions such as parenteral nutrition via a Y-site. However, in patients other than neonates, ceftriaxone and calcium-containing solutions may be administered sequentially of one another if the infusion lines",
            indications = "INDICATIONS AND USAGE Before instituting treatment with ceftriaxone, appropriate specimens should be obtained for isolation of the causative organism and for determination of its susceptibility to the drug. Therapy may be instituted prior to obtaining results of susceptibility testing. To reduce the development of drug-resistant bacteria and maintain the effectiveness of ceftriaxone for injection, USP and other antibacterial drugs, ceftriaxone for injection, USP should be used only to treat or prevent infections that are proven or strongly suspected to be caused by susceptible bacteria. When culture and susceptibility information are available, they should be considered in selecting or modifying antibacterial therapy. In the absence of such data, local epidemiology and susceptibility patte",
            contraindications = "CONTRAINDICATIONS Hypersensitivity Ceftriaxone for injection is contraindicated in patients with known hypersensitivity to ceftriaxone, any of its excipients or to any other cephalosporin. Patients with previous hypersensitivity reactions to penicillin and other beta lactam antibacterial agents may be at greater risk of hypersensitivity to ceftriaxone (see Warnings – Hypersensitivity Reactions ). Neonates Premature neonates : Ceftriaxone for injection is contraindicated in premature neonates up to a post-menstrual age of 41 weeks (gestational age + chronological age). Hyperbilirubinemic neonates : Hyperbilirubinemic neonates should not be treated with ceftriaxone for injection. Ceftriaxone can displace bilirubin from its binding to serum albumin, leading to a risk of bilirubin encephalopat",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 6.2",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "INTRAMUSCULAR",
            storageConditions = "Storage Prior to Reconstitution Store at 20° to 25°C (68° to 77°F) [see USP Controlled Room Temperature]. Protect from light.",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 019 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "50c3fdb2-97a0-1143-e063-6294a90a90df",
            drugName = "doxycycline",
            genericName = "Doxycycline",
            dosage = "DOSAGE AND ADMINISTRATION The usual dosage and frequency of administration of doxycycline differs from that of the other tetracyclines. Exceeding the recommended dosage may result in an increased incidence of side effects. Adults: The usual dose of oral doxycycline is 200 mg on the first day of treatment (administered 100 mg every 12 hours) followed by a maintenance dose of 100 mg/day. In the management of more severe infections (particularly chronic infections of the urinary tract), 100 mg every 12 hours is recommended. Pediatric Patients: For all pediatric patients weighing less than 45 kg with severe or life-threatening infections (e.g., anthrax, Rocky Mountain spotted fever), the recommended dosage is 2.2 mg/kg of body weight administered every 12 hours. Children weighing 45 kg or more",
            indications = "INDICATIONS AND USAGE To reduce the development of drug-resistant bacteria and maintain effectiveness of doxycycline and other antibacterial drugs, doxycycline should be used only to treat or prevent infections that are proven or strongly suspected to be caused by susceptible bacteria. When culture and susceptibility information are available, they should be considered in selecting or modifying antibacterial therapy. In the absence of such data, local epidemiology and susceptibility patterns may contribute to the empiric selection of therapy. Treatment: Doxycycline is indicated for the treatment of the following infections: Rocky Mountain spotted fever, typhus fever and the typhus group, Q fever, rickettsialpox, and tick fevers caused by Rickettsiae. Respiratory tract infections caused by",
            contraindications = "CONTRAINDICATIONS This drug is contraindicated in persons who have shown hypersensitivity to any of the tetracyclines.",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 6.2",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 020 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "29651ae0-6297-4821-9b15-9d7753e1a3b8",
            drugName = "Metronidazole",
            genericName = "Metronidazole",
            dosage = "DOSAGE AND ADMINISTRATION Trichomoniasis: In the Female: One-day treatment − two grams of metronidazole tablets, given either as a single dose or in two divided doses of one gram each, given in the same day. Seven-day course of treatment − 250 mg three times daily for seven consecutive days. There is some indication from controlled comparative studies that cure rates as determined by vaginal smears and signs and symptoms, may be higher after a seven-day course of treatment than after a one-day treatment regimen. The dosage regimen should be individualized. Single-dose treatment can assure compliance, especially if administered under supervision, in those patients who cannot be relied on to continue the seven-day regimen. A seven-day course of treatment may minimize reinfection by protectin",
            indications = "INDICATIONS AND USAGE Symptomatic Trichomoniasis. Metronidazole tablets are indicated for the treatment of T. vaginalis infection in females and males when the presence of the trichomonad has been confirmed by appropriate laboratory procedures (wet smears and/or cultures). Asymptomatic Trichomoniasis. Metronidazole tablets are indicated in the treatment of asymptomatic T. vaginalis infection in females when the organism is associated with endocervicitis, cervicitis, or cervical erosion. Since there is evidence that presence of the trichomonad can interfere with accurate assessment of abnormal cytological smears, additional smears should be performed after eradication of the parasite. Treatment of Asymptomatic Sexual Partners. T. vaginalis infection is a venereal disease. Therefore, asympto",
            contraindications = "CONTRAINDICATIONS Hypersensitivity Metronidazole tablet are contraindicated in patients with a prior history of hypersensitivity to metronidazole or other nitroimidazole derivatives. In patients with trichomoniasis, metronidazole tablet are contraindicated during the first trimester of pregnancy (see PRECAUTIONS ). Psychotic Reaction with Disulfiram Use of oral metronidazole is associated with psychotic reactions in alcoholic patients who were using disulfiram concurrently. Do not administer metronidazole to patients who have taken disulfiram within the last two weeks (see PRECAUTIONS, Drug Interactions ). Interaction with Alcohol Use of oral metronidazole is associated with a disulfiram-like reaction to alcohol, including abdominal cramps, nausea, vomiting, headaches, and flushing. Discon",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 6.2",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 021 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "cc27c9a5-dd2e-4a79-af00-28f51408362a",
            drugName = "Amlodipine Besylate",
            genericName = "Amlodipine",
            dosage = "2 DOSAGE AND ADMINISTRATION •Adult recommended starting dose: 5 mg once daily with maximum dose 10 mg once daily. ( 2.1 ) о Small, fragile, or elderly patients, or patients with hepatic insufficiency may be started on 2.5 mg once daily. ( 2.1 ) •Pediatric starting dose: 2.5 mg to 5 mg once daily. ( 2.2 ) Important Limitation : Doses in excess of 5 mg daily have not been studied in pediatric patients. ( 2.2 ) 2.1 Adults The usual initial antihypertensive oral dose of amlodipine besylate tablet is 5 mg once daily and the maximum dose is 10 mg once daily. Small, fragile, or elderly patients, or patients with hepatic insufficiency may be started on 2.5 mg once daily and this dose may be used when adding amlodipine besylate tablet to other antihypertensive therapy. Adjust dosage according to bl",
            indications = "1 INDICATIONS AND USAGE Amlodipine besylate tablets are calcium channel blocker and may be used alone or in combination with other antihypertensive and antianginal agents for the treatment of: •Hypertension ( 1.1 ) о Amlodipine besylate tablets are indicated for the treatment of hypertension, to lower blood pressure. Lowering blood pressure reduces the risk of fatal and nonfatal cardiovascular events, primarily strokes and myocardial infarctions. •Coronary Artery Disease ( 1.2 ) о Chronic Stable Angina о Vasospastic Angina (Prinzmetal's or Variant Angina) о Angiographically Documented Coronary Artery Disease in patients without heart failure or an ejection fraction < 40% 1.1 Hypertension Amlodipine besylate tablets are indicated for the treatment of hypertension, to lower blood pressure. L",
            contraindications = "4 CONTRAINDICATIONS Known sensitivity to amlodipine ( 4 ) Amlodipine besylate tablets are contraindicated in patients with known sensitivity to amlodipine.",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 11.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 022 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "016461a7-40a7-4ebc-9342-66ea16cdb6fc",
            drugName = "Losartan Potassium",
            genericName = "Losartan",
            dosage = "2 DOSAGE AND ADMINISTRATION Hypertension • Usual adult dose: 50 mg once daily. (2.1) • Usual pediatric starting dose: 0.7 mg per kg once daily (up to 50 mg). (2.1) Hypertensive Patients with Left Ventricular Hypertrophy • Usual starting dose: 50 mg once daily. (2.2) • Add hydrochlorothiazide 12.5 mg and/or increase losartan potassium to 100 mg followed by an increase to hydrochlorothiazide 25 mg if further blood pressure response is needed. (2.2 , 14.2) Nephropathy in Type 2 Diabetic Patients • Usual dose: 50 mg once daily. (2.3) • Increase dose to 100 mg once daily if further blood pressure response is needed. (2.3) 2.1 Hypertension Adult Hypertension The usual starting dose of losartan potassium tablets is 50 mg once daily. The dosage can be increased to a maximum dose of 100 mg once dai",
            indications = "1 INDICATIONS AND USAGE Losartan potassium tablets are an angiotensin II receptor blocker (ARB) indicated for: • Treatment of hypertension, to lower blood pressure in adults and children greater than 6 years old. Lowering blood pressure reduces the risk of fatal and nonfatal cardiovascular events, primarily strokes and myocardial infarctions. (1.1) • Reduction of the risk of stroke in patients with hypertension and left ventricular hypertrophy. There is evidence that this benefit does not apply to Black patients. (1.2) • Treatment of diabetic nephropathy with an elevated serum creatinine and proteinuria in patients with type 2 diabetes and a history of hypertension. (1.3) 1.1 Hypertension Losartan potassium tablets are indicated for the treatment of hypertension in adults and pediatric pat",
            contraindications = "4 CONTRAINDICATIONS Losartan potassium is contraindicated: • In patients who are hypersensitive to any component of this product. • For coadministration with aliskiren in patients with diabetes. • Hypersensitivity to any component. (4) • Coadministration with aliskiren in patients with diabetes. (4)",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 11.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 023 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "05b0f170-ac97-4a83-b03a-659a4b493ee2",
            drugName = "Atorvastatin calcium",
            genericName = "Atorvastatin",
            dosage = "2 DOSAGE AND ADMINISTRATION Take orally once daily with or without food ( 2.1 ). Assess LDL-C when clinically appropriate, as early as 4 weeks after initiating atorvastatin calcium tablets, and adjust dosage if necessary ( 2.1 ). Adults ( 2.2 ): Recommended starting dosage is 10 or 20 mg once daily; dosage range is 10 mg to 80 mg once daily. Patients requiring LDL-C reduction >45% may start at 40 mg once daily. Pediatric Patients Aged 10 Years of Age and Older with HeFH: Recommended starting dosage is 10 mg once daily; dosage range is 10 to 20 mg once daily ( 2.3 ). Pediatric Patients Aged 10 Years of Age and Older with HoFH: Recommended starting dosage is 10 to 20 mg once daily; dosage range is 10 to 80 mg once daily ( 2.4 ). See full prescribing information for atorvastatin calcium table",
            indications = "1 INDICATIONS AND USAGE Atorvastatin calcium tablets are indicated: To reduce the risk of: Myocardial infarction (MI), stroke, revascularization procedures, and angina in adults with multiple risk factors for coronary heart disease (CHD) but without clinically evident CHD MI and stroke in adults with type 2 diabetes mellitus with multiple risk factors for CHD but without clinically evident CHD Non-fatal MI, fatal and non-fatal stroke, revascularization procedures, hospitalization for congestive heart failure, and angina in adults with clinically evident CHD As an adjunct to diet to reduce low-density lipoprotein cholesterol (LDL-C) in: Adults with primary hyperlipidemia. Adults and pediatric patients aged 10 years and older with heterozygous familial hypercholesterolemia (HeFH). As an adju",
            contraindications = "4 CONTRAINDICATIONS Acute liver failure or decompensated cirrhosis [see Warnings and Precautions (5.3) ] Hypersensitivity to atorvastatin or any excipients in atorvastatin calcium. Hypersensitivity reactions, including anaphylaxis, angioneurotic edema, erythema multiforme, Stevens-Johnson syndrome, and toxic epidermal necrolysis, have been reported [see Adverse Reactions (6.2) ]. Acute liver failure or decompensated cirrhosis ( 4 ). Hypersensitivity to atorvastatin or any excipient in atorvastatin calcium ( 4 ).",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 11.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 024 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "b8c33971-54b3-4971-bf7f-1bec66999a29",
            drugName = "Warfarin Sodium",
            genericName = "Warfarin",
            dosage = "2 DOSAGE AND ADMINISTRATION Individualize dosing regimen for each patient, and adjust based on INR response. ( 2.1 , 2.2 ) Knowledge of genotype can inform initial dose selection. ( 2.3 ) Monitoring: Obtain daily INR determinations upon initiation until stable in the therapeutic range. Obtain subsequent INR determinations every 1 to 4 weeks. ( 2.4 ) Review conversion instructions from other anticoagulants. ( 2.8 ) 2.1 Individualized Dosing The dosage and administration of warfarin sodium tablets must be individualized for each patient according to the patient’s International Normalized Ratio (INR) response to the drug. Adjust the dose based on the patient’s INR and the condition being treated. Consult the latest evidence-based clinical practice guidelines regarding the duration and intensi",
            indications = "1 INDICATIONS AND USAGE Warfarin sodium tablets are indicated for: Prophylaxis and treatment of venous thrombosis and its extension, pulmonary embolism (PE). Prophylaxis and treatment of thromboembolic complications associated with atrial fibrillation (AF) and/or cardiac valve replacement. Reduction in the risk of death, recurrent myocardial infarction (MI), and thromboembolic events such as stroke or systemic embolization after myocardial infarction. Limitations of Use Warfarin sodium tablets have no direct effect on an established thrombus, nor does it reverse ischemic tissue damage. Once a thrombus has occurred, however, the goals of anticoagulant treatment are to prevent further extension of the formed clot and to prevent secondary thromboembolic complications that may result in seriou",
            contraindications = "4 CONTRAINDICATIONS Warfarin sodium is contraindicated in: Pregnancy Warfarin sodium is contraindicated in women who are pregnant except in pregnant women with mechanical heart valves, who are at high risk of thromboembolism [see Warnings and Precautions ( 5.7 ) and Use in Specific Populations ( 8.1 )] . Warfarin sodium can cause fetal harm when administered to a pregnant woman. Warfarin sodium exposure during pregnancy causes a recognized pattern of major congenital malformations (warfarin embryopathy and fetotoxicity), fatal fetal hemorrhage, and an increased risk of spontaneous abortion and fetal mortality. If warfarin sodium is used during pregnancy or if the patient becomes pregnant while taking this drug, the patient should be apprised of the potential hazard to a fetus [see Use in S",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 11.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 025 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "20347bf0-6858-4d38-b1f6-8ec210d2bb1a",
            drugName = "Digoxin",
            genericName = "Digoxin",
            dosage = "2 DOSAGE & ADMINISTRATION Digoxin dose is based on patient-specific factors (age, lean body weight, renal function, etc.). See full prescribing information. Monitor for toxicity and therapeutic effect. 2.1 Important Dosing and Administration Information In selecting a digoxin dosing regimen, it is important to consider factors that affect digoxin blood levels (e.g., body weight, age, renal function, concomitant drugs) since toxic levels of digoxin are only slightly higher than therapeutic levels. Dosing can be either initiated with a loading dose followed by maintenance dosing if rapid titration is desired or initiated with maintenance dosing without a loading dose. Consider interruption or reduction in digoxin dose prior to electrical cardioversion [see Warnings and Precautions (5.4) ] .",
            indications = "1 INDICATIONS & USAGE Digoxin is a cardiac glycoside indicated for: Treatment of mild to moderate heart failure in adults. ( 1.1 ) Increasing myocardial contractility in pediatric patients with heart failure. ( 1.2 ) Control of resting ventricular rate in patients with chronic atrial fibrillation in adults. ( 1.3 ) 1.1 Heart Failure in Adults Digoxin is indicated for the treatment of mild to moderate heart failure in adults. Digoxin increases left ventricular ejection fraction and improves heart failure symptoms as evidenced by improved exercise capacity and decreased heart failure-related hospitalizations and emergency care, while having no effect on mortality. Where possible, digoxin should be used in combination with a diuretic and an angiotensin-converting enzyme (ACE) inhibitor. 1.2 H",
            contraindications = "4 CONTRAINDICATIONS Digoxin is contraindicated in patients with: Ventricular fibrillation [see Warnings and Precautions (5.1)] Known hypersensitivity to digoxin (reactions seen include unexplained rash, swelling of the mouth, lips or throat or a difficulty in breathing). A hypersensitivity reaction to other digitalis preparations usually constitutes a contraindication to digoxin. Ventricular fibrillation. ( 4 ) Known hypersensitivity to digoxin or other forms of digitalis. ( 4 )",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 11.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 026 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "00b256d4-1691-cc3b-e063-6294a90a47ec",
            drugName = "Lisinopril and Hydrochlorothiazide",
            genericName = "Lisinopril",
            dosage = "DOSAGE AND ADMINISTRATION Lisinopril monotherapy is an effective treatment of hypertension in once-daily doses of 10 mg to 80 mg, while hydrochlorothiazide monotherapy is effective in doses of 12.5 mg to 50 mg per day. In clinical trials of lisinopril/hydrochlorothiazide combination therapy using lisinopril doses of 10 mg to 80 mg and hydrochlorothiazide doses of 6.25 mg to 50 mg, the antihypertensive response rates generally increased with increasing dose of either component. The side effects (see WARNINGS ) of lisinopril are generally rare and apparently independent of dose; those of hydrochlorothiazide are a mixture of dose-dependent phenomena (primarily hypokalemia) and dose-independent phenomena (e.g., pancreatitis), the former much more common than the latter. Therapy with any combin",
            indications = "INDICATIONS AND USAGE Lisinopril and hydrochlorothiazide tablets are indicated for the treatment of hypertension, to lower blood pressure. Lowering blood pressure lowers the risk of fatal and non-fatal cardiovascular events, primarily strokes and myocardial infarctions. These benefits have been seen in controlled trials of antihypertensive drugs from a wide variety of pharmacologic classes including lisinopril and hydrochlorothiazide. Control of high blood pressure should be part of comprehensive cardiovascular risk management, including, as appropriate, lipid control, diabetes management, antithrombotic therapy, smoking cessation, exercise, and limited sodium intake. Many patients will require more than 1 drug to achieve blood pressure goals. For specific advice on goals and management, s",
            contraindications = "CONTRAINDICATIONS Lisinopril and hydrochlorothiazide tablets are contraindicated in patients who are hypersensitive to any component of this product and in patients with a history of angioedema related to previous treatment with an angiotensin converting enzyme inhibitor and in patients with hereditary or idiopathic angioedema. Because of the hydrochlorothiazide component, this product is contraindicated in patients with anuria or hypersensitivity to other sulfonamide-derived drugs. Lisinopril and hydrochlorothiazide tablets are contraindicated in combination with a neprilysin inhibitor (e.g., sacubitril). Do not administer lisinopril and hydrochlorothiazide tablets within 36 hours of switching to or from sacubitril/valsartan, a neprilysin inhibitor (see WARNINGS ). Do not coadminister ali",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 11.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 027 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "80acab10-ff9e-4353-8bf0-a9ea6138e047",
            drugName = "anti diarrheal",
            genericName = "Loperamide",
            dosage = "Directions • drink plenty of clear fluids to help prevent dehydration caused by diarrhea • find right dose on chart. If possible, use weight to dose; otherwise use age. • shake well before using • use only enclosed dosing cup specifically designed for use with this product. Do not use any other dosing device. • mL = milliliter adults and children 12 years and over 30 mL after the first loose stool; 15 mL after each subsequent loose stool; but no more than 60 mL in 24 hours children 9-11 years (60-95 lbs) 15 mL after the first loose stool; 7.5 mL after each subsequent loose stool; but no more than 45 mL in 24 hours children 6-8 years (48-59 lbs) 15 mL after the first loose stool; 7.5 mL after each subsequent loose stool; but no more than 30 mL in 24 hours children 2-5 years (34 to 47 lbs) a",
            indications = "Use controls symptoms of diarrhea, including Travelers’ Diarrhea",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 17.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Other information • each 30 mL contains: sodium 15 mg • store between 20-25 ° C (68-77 ° F)",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 028 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "3160c469-0445-4227-8ab2-93032e0443d9",
            drugName = "Bismuth",
            genericName = "Bismuth subsalicylate",
            dosage = "Directions chew or dissolve in mouth Adults and children over 12 years : 2 tablets (1 dose) every ½ hour or 4 tablets (2 doses) every hour as needed for diarrhea 2 tablets (1 dose) every 1/2 hour as needed for overindulgence (upset stomach, heartburn, indigestion, nausea) do not exceed 8 doses (16 tablets) in 24 hours use until diarrhea stops but no more than 2 days Children under 12 years of age: ask a doctor drink plenty of clear fluids to prevent dehydration caused by diarrhea",
            indications = "Uses relieves travelers’ diarrhea diarrhea upset stomach due to overindulgence in food and drink including: heartburn indigestion nausea gas belching fullness",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 17.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 029 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "440a6d3c-d0d2-7a09-e063-6294a90a1de8",
            drugName = "GERI-MOX ANTACID ANTIGAS",
            genericName = "Aluminum Hydroxide",
            dosage = "Directions shake well before use adults and children 12 years of age and older: take 2 to 4 teaspoonfuls between meals, at bedtime, or as directed by a doctor do not take more than 16 teaspoonfuls in any 24 hour period or use the maximum dosage for more than 2 weeks children under 12 years: ask a doctor",
            indications = "Uses relieves heartburn sour stomach acid indigestion the symptoms referred to as gas",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 17.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Other information each 5 mL teaspoonful contains: magnesium 85 mg, sodium 3 mg store at room temperature protect from freezing keep tightly closed",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 030 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "09a7141e-5879-df3c-e063-6394a90a9962",
            drugName = "MILK OF MAGNESIA",
            genericName = "Magnesium Hydroxide",
            dosage = "Directions shake well before use do not exceed the maximum recommended daily dose in a 24 hour period dose may be taken once a day preferably at bedtime, in divided doses, or as directed by a doctor drink a full glass (8 oz) of liquid with each dose for accurate dosing, only use the dosing cup provided mL = milliliter age dose adults and children 12 years and over 30 mL to 60 mL children 6 to 11 years 15 mL to 30 mL children under 6 years ask a doctor",
            indications = "Uses relieves occasional constipation (irregularity) generally produces bowel movement in 1/2 to 6 hours",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 17.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Other information each 15 mL contains: magnesium 500 mg, sodium 6 mg store at room temperature tightly closed avoid freezing",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 031 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "3b2a5ecf-ba3d-d11c-e063-6394a90a81b7",
            drugName = "Epinephrine",
            genericName = "Epinephrine",
            dosage = "2 DOSAGE AND ADMINISTRATION • Hypotension associated with septic shock ( 2.2 ) : o Dilute epinephrine in dextrose solution prior to infusion. o Infuse epinephrine into a large vein. o Titrate 0.05 mcg/kg/min to 2 mcg/kg/min to achieve desired blood pressure. o Wean gradually. 2.1 General Considerations Inspect visually for particulate matter and discoloration prior to administration, whenever solution and container permit. Do not use if the solution is colored or cloudy, or if it contains particulate matter. Discard any unused portion. 2.2 Hypotension associated with Septic Shock Dilute epinephrine in 5% Dextrose Injection, USP or 5% Dextrose and Sodium Chloride solution. These dextrose containing fluids provide protection against significant loss of potency by oxidation. Administration in",
            indications = "1 INDICATIONS AND USAGE Epinephrine is a non-selective alpha and beta adrenergic agonist indicated to increase mean arterial blood pressure in adult patients with hypotension associated with septic shock. ( 1.1 ) 1.1 Hypotension associated with Septic Shock Epinephrine Injection USP, 1 mg/10 mL (0.1 mg/mL) is indicated to increase mean arterial blood pressure in adult patients with hypotension associated with septic shock.",
            contraindications = "4 CONTRAINDICATIONS None. None.",
            maxMgPerKg = 0.01f,
            maxDailyMg = 2.0,
            maxSingleDoseMg = 0.5,
            source = "OpenFDA/WHO 4.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "C01CA24",
            route = "INTRAVENOUS",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 032 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "21e7776e-65d0-45fb-a1b2-13f49055ad64",
            drugName = "PENTAZOCINE HYDROCHLORIDE AND NALOXONE HYDROCHLORIDE",
            genericName = "Naloxone",
            dosage = "DOSAGE AND ADMINISTRATION Important Dosage and Administration Instructions Pentazocine and Naloxone Tablets should be prescribed only by healthcare professionals who are knowledgeable about the use of opioids and how to mitigate the associated risks. Use the lowest effective dosage for the shortest duration of time consistent with individual patient treatment goals [see Warnings and Precautions ] . Reserve titration to higher doses of Pentazocine and Naloxone Tablets for patients in whom lower doses are insufficiently effective and in whom the expected benefits of using a higher dose opioid clearly outweigh the substantial risks. Many acute pain conditions (e.g., the pain that occurs with a number of surgical procedures or acute musculoskeletal injuries) require no more than a few days of",
            indications = "INDICATIONS AND USAGE Pentazocine and Naloxone Tablets are indicated for the management of pain severe enough to require an opioid analgesic and for which alternative treatments are inadequate. Limitations of Use Because of the risks of addiction, abuse, misuse, overdose, and death, which can occur at any dosage or duration, and persist over the course of therapy, reserve opioid analgesics, including Pentazocine and Naloxone Tablets for use in patients for whom alternative treatment options are ineffective, not tolerated, or would be otherwise inadequate to provide sufficient management of pain.",
            contraindications = "CONTRAINDICATIONS Pentazocine and Naloxone Tablets are contraindicated in patients with: Significant respiratory depression [see WARNINGS ] Acute or severe bronchial asthma in an unmonitored setting or in the absence of resuscitative equipment [see WARNINGS ]. Patients with known or suspected gastrointestinal obstruction, including paralytic ileus [see WARNINGS ] Patients with hypersensitivity to either pentazocine, naloxone, or any of the formulation excipients (e.g., anaphylaxis) [see WARNINGS ].",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 4.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 033 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "100be173-227f-a802-e063-6294a90ae92e",
            drugName = "ATROPINE SULFATE",
            genericName = "Atropine",
            dosage = "2 DOSAGE & ADMINISTRATION 2.1 General Administration Parenteral drug products should be inspected visually for particulate matter and discoloration prior to administration, whenever solution and container permit. Do not administer unless solution is clear and seal is intact. Each vial is intended for single dose only. Discard unused portion. For Intravenous administration. Titrate based on heart rate, PR interval, blood pressure and symptoms. 2.2 Adult Dosage 2.3 Pediatric Dosage Dosing in pediatric populations has not been well studied. Usual initial dose is 0.01 to 0.03 mg/kg. 2.4 Dosing in Patients with Coronary Artery Disease Limit the total dose of atropine sulfate to 0.03 to 0.04 mg/kg [see WARNINGS AND PRECAUTIONS (5.1 )]. DOSAGE",
            indications = "1 INDICATIONS & USAGE Atropine Sulfate Injection, USP, is indicated for temporary blockade of severe or life threatening muscarinic effects, e.g., as an antisialagogue, an antivagal agent, an antidote for organophosphorus or muscarinic mushroom poisoning, and to treat bradyasystolic cardiac arrest.",
            contraindications = "4 CONTRAINDICATIONS None.",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 4.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "INTRAVENOUS",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 034 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "b274cc51-dd50-430a-bc9e-ad8c1b6be455",
            drugName = "ANXIETY II HP",
            genericName = "Activated Charcoal",
            dosage = "DIECTIONS: Adults & children above 12 years: 10 drops orally 3 times daily, or as directed by a health care professional.",
            indications = "​USES: ​Temporarly relieves simple nervous tension.**",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 4.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 035 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "e1d65360-2785-4308-8a64-5c2c9e812868",
            drugName = "Glucagon",
            genericName = "Glucagon",
            dosage = "2 DOSAGE AND ADMINISTRATION Dosage in adult and pediatric patients to treat severe hypoglycemia ( 2.2 ) • Adults and Pediatric Patients Weighing 20 kg or More: ▪ The recommended dosage is 1 mg (1 mL) injected subcutaneously or intramuscularly into the upper arm, thigh, or buttocks, or intravenously. ▪ If there has been no response after 15 minutes, an additional 1 mg dose (1 mL) may be administered while waiting for emergency assistance. • Pediatric Patients Weighing Less Than 20 kg: ▪ The recommended dosage is 0.5 mg (0.5 mL) or dose equivalent to 20 mcg/kg to 30 mcg/kg injected subcutaneously or intramuscularly into the upper arm, thigh, or buttocks, or intravenously. ▪ If there has been no response after 15 minutes, an additional 0.5 mg dose (0.5 mL) may be administered while waiting fo",
            indications = "1 INDICATIONS AND USAGE Glucagon for Injection is an antihypoglycemic agent and a gastrointestinal motility inhibitor indicated: • for the treatment of severe hypoglycemia in pediatric and adult patients with diabetes. ( 1.1 ) • as a diagnostic aid for use during radiologic examinations to temporarily inhibit movement of the gastrointestinal tract in adult patients. ( 1.2 ) 1.1 Severe Hypoglycemia Glucagon for Injection is indicated for the treatment of severe hypoglycemia in pediatric and adult patients with diabetes mellitus. 1.2 Diagnostic Aid Glucagon for Injection is indicated as a diagnostic aid for use during radiologic examinations to temporarily inhibit movement of the gastrointestinal tract in adult patients.",
            contraindications = "4 CONTRAINDICATIONS Glucagon for Injection is contraindicated in patients with: • Pheochromocytoma because of the risk of substantial increase in blood pressure [see Warning and Precautions (5.1) ] • Insulinoma because of the risk of hypoglycemia [see Warning and Precautions (5.2) ] • Known hypersensitivity to glucagon or any of the excipients in Glucagon for Injection. Allergic reactions have been reported with glucagon and include anaphylactic shock with breathing difficulties and hypotension [see Warning and Precautions (5.3) ] • Glucagonoma when used as a diagnostic aid because of the risk of hypoglycemia [see Warnings and Precautions (5.8) ] • Pheochromocytoma ( 4 ) • Insulinoma ( 4 ) • Known hypersensitivity to glucagon or to any of the excipients ( 4 ) • Glucagonoma when used as a d",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 4.0",
            lastUpdated = ts,
            isHighRisk = true,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 036 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "b9d5a8f5-39f1-425d-93fa-8adf95867fdb",
            drugName = "Fluoxetine",
            genericName = "Fluoxetine",
            dosage = "2 DOSAGE AND ADMINISTRATION Indication Adult Pediatric MDD ( 2.1 ) 20 mg/day in am (initial dose) 10 to 20 mg/day (initial dose) OCD ( 2.2 ) 20 mg/day in am (initial dose) 10 mg/day (initial dose) Bulimia Nervosa ( 2.3 ) 60 mg/day in am Panic Disorder ( 2.4 ) 10 mg/day (initial dose) Depressive Episodes Associated with Bipolar I Disorder ( 2.5 ) Oral in combination with olanzapine: 5 mg of oral olanzapine and 20 mg of fluoxetine once daily (initial dose) Oral in combination with olanzapine: 2.5 mg of oral olanzapine and 20 mg of fluoxetine once daily (initial dose) A lower or less frequent dosage should be used in patients with hepatic impairment, the elderly, and for patients with concurrent disease or on multiple concomitant medications ( 2.7 ) Fluoxetine capsules and olanzapine in combi",
            indications = "1 INDICATIONS AND USAGE Fluoxetine is indicated for the treatment of: Acute and maintenance treatment of Major Depressive Disorder [see Clinical Studies (14.1) ] . Acute and maintenance treatment of obsessions and compulsions in patients with Obsessive Compulsive Disorder (OCD) [see Clinical Studies (14.2) ] . Acute and maintenance treatment of binge-eating and vomiting behaviors in patients with moderate to severe Bulimia Nervosa [see Clinical Studies (14.3) ] . Acute treatment of Panic Disorder, with or without agoraphobia [see Clinical Studies (14.4) ] . Fluoxetine and Olanzapine in Combination is indicated for the treatment of: Acute treatment of depressive episodes associated with Bipolar I Disorder. Fluoxetine monotherapy is not indicated for the treatment of depressive episodes asso",
            contraindications = "4 CONTRAINDICATIONS When using fluoxetine capsules and olanzapine in combination, also refer to the Contraindications section of the package insert for Symbyax. Serotonin Syndrome and MAOIs: Do not use MAOIs intended to treat psychiatric disorders with fluoxetine or within 5 weeks of stopping treatment with fluoxetine. Do not use fluoxetine within 14 days of stopping an MAOI intended to treat psychiatric disorders. In addition, do not start fluoxetine in a patient who is being treated with linezolid or intravenous methylene blue ( 4.1 ) Pimozide: Do not use. Risk of QT prolongation and drug interaction ( 4.2 , 5.11 , 7.7 , 7.8 ) Thioridazine: Do not use. Risk of QT interval prolongation and elevated thioridazine plasma levels. Do not use thioridazine within 5 weeks of discontinuing fluoxet",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 24.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "CATEGORY C"
        ))

        // REGISTRY NO: 037 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "7731d02d-7220-4f43-b230-9d38875f4192",
            drugName = "Sertraline Hydrochloride",
            genericName = "Sertraline",
            dosage = "DOSAGE AND ADMINISTRATION Initial Treatment Dosage for Adults Major Depressive Disorder –Sertraline hydrochloride treatment should be administered at a dose of 50 mg once daily. While a relationship between dose and effect has not been established for major depressive disorder, OCD, panic disorder, PTSD or social anxiety disorder, patients were dosed in a range of 50-200 mg/day in the clinical trials demonstrating the effectiveness of Sertraline hydrochloride for the treatment of this indication. Consequently, a dose of 50 mg, administered once daily, is recommended as the initial therapeutic dose. Patients not responding to a 50 mg dose may benefit from dose increases up to a maximum of 200 mg/day. Given the 24 hour elimination half-life of sertraline hydrochloride, dose changes should no",
            indications = "INDICATIONS AND USAGE Major Depressive Disorder – Sertraline hydrochloride is indicated for the treatment of major depressive disorder in adults. The efficacy of Sertraline hydrochloride in the treatment of a major depressive episode was established in six to eight week controlled trials of adult outpatients whose diagnoses corresponded most closely to the DSM-III category of major depressive disorder (see Clinical Trials under CLINICAL PHARMACOLOGY ). A major depressive episode implies a prominent and relatively persistent depressed or dysphoric mood that usually interferes with daily functioning (nearly every day for at least 2 weeks); it should include at least 4 of the following 8 symptoms: change in appetite, change in sleep, psychomotor agitation or retardation, loss of interest in u",
            contraindications = "CONTRAINDICATIONS All Dosage Forms of Sertraline: The use of MAOIs intended to treat psychiatric disorders with Sertraline hydrochloride or within 14 days of stopping treatment with Sertraline hydrochloride is contraindicated because of an because of an increased risk if serotonin syndrome. The use of Sertraline hydrochloride within 14 days of stopping an MAOI intended to treat psychiatric disorders is also contraindicated (see WARNINGS and DOSAGE AND ADMINISTRATION ). Starting Sertraline hydrochloride in a patient who is being treated with MAOIs such as linezolid or intravenous methyelene blue is also contraindicated because of an increased risk of serotonin syndrome (see WARNINGS and DOSAGE AND ADMINISTRATION). Concomitant use in patients taking pimozide is contraindicated (see PRECAUTIO",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 24.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 038 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "7b545974-2d39-4106-8975-76c124a6c5ee",
            drugName = "Haloperidol",
            genericName = "Haloperidol",
            dosage = "DOSAGE AND ADMINISTRATION There is considerable variation from patient to patient in the amount of medication required for treatment. As with all antipsychotic drugs, dosage should be individualized according to the needs and response of each patient. Dosage adjustments, either upward or downward, should be carried out as rapidly as practicable to achieve optimum therapeutic control. To determine the initial dosage, consideration should be given to the patient's age, severity of illness, previous response to other antipsychotic drugs, and any concomitant medication or disease state. Children, debilitated or geriatric patients, as well as those with a history of adverse reactions to antipsychotic drugs, may require less haloperidol. The optimal response in such patients is usually obtained",
            indications = "INDICATIONS AND USAGE Haloperidol tablets are indicated for use in the management of manifestations of psychotic disorders. Haloperidol tablets are indicated for the control of tics and vocal utterances of Tourette's Disorder in children and adults. Haloperidol tablets are effective for the treatment of severe behavior problems in children of combative, explosive hyperexcitability (which cannot be accounted for by immediate provocation). Haloperidol tablets are also effective in the short-term treatment of hyperactive children who show excessive motor activity with accompanying conduct disorders consisting of some or all of the following symptoms: impulsivity, difficulty sustaining attention, aggressivity, mood lability, and poor frustration tolerance. Haloperidol tablets should be reserve",
            contraindications = "CONTRAINDICATIONS Haloperidol tablets are contraindicated in severe toxic central nervous system depression or comatose states from any cause and in individuals who are hypersensitive to this drug or have Parkinson's disease.",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 24.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 039 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "47f4f928-6316-1f11-e063-6394a90ab1bb",
            drugName = "RISPERIDONE",
            genericName = "Risperidone",
            dosage = "2 DOSAGE AND ADMINISTRATION Table 1. Recommended Daily Dosage by Indication Initial Dose Titration (Increments) Target Dose Effective Dose Range Schizophrenia: adults ( 2.1 ) 2 mg 1 to 2 mg 4 to 8 mg 4 to 16 mg Schizophrenia: adolescents ( 2.2 ) 0.5 mg 0.5 to 1 mg 3 mg 1 to 6 mg Bipolar mania: adults ( 2.2 ) 2 to 3 mg 1 mg 1 to 6 mg 1 to 6 mg Bipolar mania: children and adolescents ( 2.2 ) 0.5 mg 0.5 to 1 mg 1 to 2.5 mg 1 to 6 mg Irritability in autistic disorder ( 2.3 ) 0.25 mg Can increase to 0.5 mg by Day 4: (body weight less than 20 kg) 0.5 mg Can increase to 1 mg by Day 4: (body weight greater than or equal to 20 kg) After Day 4, at intervals of > 2 weeks: 0.25 mg (body weight less than 20 kg) 0.5 mg (body weight greater than or equal to 20 kg) 0.5 mg: (body weight less than 20 kg) 1",
            indications = "1 INDICATIONS AND USAGE Risperidone tablets are an atypical antipsychotic indicated for: Treatment of schizophrenia ( 1.1 ) As monotherapy or adjunctive therapy with lithium or valproate, for the treatment of acute manic or mixed episodes associated with Bipolar I Disorder ( 1.2 ) Treatment of irritability associated with autistic disorder ( 1.3 ) 1.1 Schizophrenia Risperidone tablets are indicated for the treatment of schizophrenia. Efficacy was established in 4 short-term trials in adults, 2 short-term trials in adolescents (ages 13 to 17 years), and one long-term maintenance trial in adults [see Clinical Studies (14.1) ] . 1.2 Bipolar Mania Monotherapy Risperidone tablets are indicated for the treatment of acute manic or mixed episodes associated with Bipolar I Disorder. Efficacy was es",
            contraindications = "4 CONTRAINDICATIONS Risperidone tablets are contraindicated in patients with a known hypersensitivity to either risperidone or paliperidone, or to any of the excipients in the risperidone tablets formulation. Hypersensitivity reactions, including anaphylactic reactions and angioedema, have been reported in patients treated with risperidone and in patients treated with paliperidone. Paliperidone is a metabolite of risperidone. Known hypersensitivity to risperidone, paliperidone, or to any excipients in risperidone tablets. ( 4 )",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 24.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 040 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "842dfa9a-8c93-4c77-8f6b-95dd175df191",
            drugName = "Diazepam",
            genericName = "Diazepam",
            dosage = "DOSAGE AND ADMINISTRATION Dosage should be individualized for maximum beneficial effect. While the usual daily dosages given below will meet the needs of most patients, there will be some who may require higher doses. In such cases dosage should be increased cautiously to avoid adverse effects. ADULTS: USUAL DAILY DOSE: Management of Anxiety Disorders and Relief of Symptoms of Anxiety. Depending upon severity of symptoms—2 mg to 10 mg, 2 to 4 times daily Symptomatic Relief in Acute Alcohol Withdrawal. 10 mg, 3 or 4 times during the first 24 hours, reducing to 5 mg, 3 or 4 times daily as needed. Adjunctively for Relief of Skeletal Muscle Spasm. 2 mg to 10 mg, 3 or 4 times daily Adjunctively in Convulsive Disorders. 2 mg to 10 mg, 2 to 4 times daily Geriatric Patients, or in the presence of",
            indications = "INDICATIONS AND USAGE Diazepam tablets are indicated for the management of anxiety disorders or for the short-term relief of the symptoms of anxiety. Anxiety or tension associated with the stress of everyday life usually does not require treatment with an anxiolytic. In acute alcohol withdrawal, diazepam tablets may be useful in the symptomatic relief of acute agitation, tremor, impending or acute delirium tremens and hallucinosis. Diazepam tablets are a useful adjunct for the relief of skeletal muscle spasm due to reflex spasm to local pathology (such as inflammation of the muscles or joints, or secondary to trauma), spasticity caused by upper motor neuron disorders (such as cerebral palsy and paraplegia), athetosis, and stiff-man syndrome. Oral diazepam tablets may be used adjunctively i",
            contraindications = "CONTRAINDICATIONS Diazepam tablets are contraindicated in patients with a known hypersensitivity to diazepam and, because of lack of sufficient clinical experience, in pediatric patients under 6 months of age. Diazepam tablets are also contraindicated in patients with myasthenia gravis, severe respiratory insufficiency, severe hepatic insufficiency, and sleep apnea syndrome. They may be used in patients with open-angle glaucoma who are receiving appropriate therapy, but is contraindicated in acute narrow-angle glaucoma.",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 24.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        drugs.add(PharmacopeiaEntry("ERR-40", "Salbutamol", "Salbutamol", "FAIL", "FAIL", "FAIL", source = "DATA_MISSING", lastUpdated = ts))

        // REGISTRY NO: 042 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "49fdba31-142a-2157-e063-6294a90a01f3",
            drugName = "Fluticasone Propionate",
            genericName = "Fluticasone",
            dosage = "Directions read the Quick Start Guide for how to: prime the bottle use the spray clean the spray nozzle shake gently before each use use this product only once a day do not use more than directed ADULTS AND CHILDREN 12 YEARS OF AGE AND OLDER Week 1- use 2 sprays in each nostril once daily Week 2 through 6 months- use 1 or 2 sprays in each nostril once daily, as needed to treat your symptoms After 6 months of daily use – ask your doctor if you can keep using CHILDREN 4 TO 11 YEARS OF AGE the growth rate of some children may be slower while using this product. Children should use for the shortest amount of time necessary to achieve symptom relief. Talk to your child’s doctor if your child needs to use the spray for longer than two months a year. an adult should supervise use use 1 spray in e",
            indications = "Uses Temporarily relieves these symptoms of hay fever or other upper respiratory allergies: nasal congestion itchy, watery eyes itchy nose runny nose sneezing",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 23.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "NASAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 043 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "c8dbb0db-5c00-4205-a765-d664117c4398",
            drugName = "Ipratropium Bromide",
            genericName = "Ipratropium",
            dosage = "Consult label",
            indications = "Verified",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 23.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "NASAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 044 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "c678afd8-0ddd-40a3-936e-594c126a275d",
            drugName = "montelukast sodium",
            genericName = "Montelukast",
            dosage = "2 DOSAGE AND ADMINISTRATION Administration (by indications): • Asthma ( 2.1 ): Once daily in the evening for patients 2 years and older. • Acute prevention of EIB ( 2.2 ): One tablet at least 2 hours before exercise for patients 6 years of age and older. • Seasonal allergic rhinitis ( 2.3 ): Once daily for patients 2 years and older. • Perennial allergic rhinitis ( 2.3 ): Once daily for patients 2 years and older. Dosage (by age) ( 2 ): • 15 years and older: one 10-mg tablet. • 6 to 14 years: one 5-mg chewable tablet. • 2 to 5 years: one 4-mg chewable tablet. Patients with both asthma and allergic rhinitis should take only one dose daily in the evening ( 2.4 ). 2.1 Asthma Montelukast sodium should be taken once daily in the evening. The following doses are recommended: For adults and adole",
            indications = "1 INDICATIONS AND USAGE Montelukast sodium chewable tablets are a leukotriene receptor antagonist indicated for: • Prophylaxis and chronic treatment of asthma in patients 2 years of age and older ( 1.1 ). • Acute prevention of exercise-induced bronchoconstriction (EIB) in patients 6 years of age and older ( 1.2 ). • Relief of symptoms of allergic rhinitis (AR): seasonal allergic rhinitis (SAR) in patients 2 years of age and older, and perennial allergic rhinitis (PAR) in patients 2 years of age and older. Reserve use for patients who have an inadequate response or intolerance to alternative therapies ( 1.3 ). 1.1 Asthma Montelukast sodium chewable tablets are indicated for the prophylaxis and chronic treatment of asthma in adults and pediatric patients 2 years of age and older. 1.2 Exercis",
            contraindications = "4 CONTRAINDICATIONS • Hypersensitivity to any component of this product. • Hypersensitivity to any component of this product ( 4 ).",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 23.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        drugs.add(PharmacopeiaEntry("ERR-44", "Beclometasone", "Beclometasone", "FAIL", "FAIL", "FAIL", source = "DATA_MISSING", lastUpdated = ts))

        // REGISTRY NO: 046 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "46766222-63b4-478f-aa8b-a23b20476317",
            drugName = "Theophylline (Anhydrous)",
            genericName = "Theophylline",
            dosage = "DOSAGE AND ADMINISTRATION Theophylline (Anhydrous) Extended-Release Tablets 400 or 600 mg can be taken once a day in the morning or evening. It is recommended that Theophylline (Anhydrous) Extended-Release Tablets be taken with meals. Patients should be advised that if they choose to take Theophylline (Anhydrous) Extended-Release Tablets with food it should be taken consistently with food and if they take it in a fasted condition it should routinely be taken fasted. It is important that the product whenever dosed be dosed consistently with or without food. Theophylline (Anhydrous) Extended-Release Tablets are not to be chewed or crushed because it may lead to a rapid release of theophylline with the potential for toxicity. The scored tablet may be split. Infrequently, patients receiving Th",
            indications = "INDICATIONS AND USAGE Theophylline is indicated for the treatment of the symptoms and reversible airflow obstruction associated with chronic asthma and other chronic lung diseases, e.g., emphysema and chronic bronchitis.",
            contraindications = "CONTRAINDICATIONS Theophylline (Anhydrous) Extended-Release Tablets are contraindicated in patients with a history of hypersensitivity to theophylline or other components in the product.",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 23.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Store at 20° to 25°C (68° to 77°F) [See USP Controlled Room Temperature]. Dispense in tight, light-resistant container.",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 047 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "79996212-633b-464e-9967-834039fb13a4",
            drugName = "Stiolto Respimat",
            genericName = "Tiotropium",
            dosage = "2 DOSAGE AND ADMINISTRATION For oral inhalation only. Two inhalations of STIOLTO RESPIMAT once-daily at the same time of day. ( 2 ) 2.1 Recommended Dosage The recommended dosage of STIOLTO RESPIMAT is two inhalations once-daily at the same time of the day. Do not use STIOLTO RESPIMAT more than two inhalations every 24 hours. 2.2 Administration Information For oral inhalation only. Prior to first use, the STIOLTO RESPIMAT cartridge is inserted into the STIOLTO RESPIMAT inhaler and the unit is primed. When using the unit for the first time, patients are to actuate the inhaler toward the ground until an aerosol cloud is visible and then repeat the process three more times. The unit is then considered primed and ready for use. If not used for more than 3 days, patients are to actuate the inhal",
            indications = "1 INDICATIONS AND USAGE STIOLTO RESPIMAT is a combination of tiotropium bromide, an anticholinergic and olodaterol, a long-acting beta 2 -adrenergic agonist (LABA) indicated for the long-term, once-daily maintenance treatment of patients with chronic obstructive pulmonary disease (COPD). ( 1.1 ) Important limitations: STIOLTO RESPIMAT is NOT indicated to treat acute deterioration of COPD. ( 1.1 ) STIOLTO RESPIMAT is NOT indicated to treat asthma. ( 1.1 ) 1.1 Maintenance Treatment of COPD STIOLTO RESPIMAT is a combination of tiotropium bromide and olodaterol indicated for long-term, once-daily maintenance treatment of patients with chronic obstructive pulmonary disease (COPD), including chronic bronchitis and/or emphysema. Important Limitations of Use STIOLTO RESPIMAT is not indicated to tr",
            contraindications = "4 CONTRAINDICATIONS Use of a LABA, including STIOLTO RESPIMAT, without an inhaled corticosteroid is contraindicated in patients with asthma [see Warnings and Precautions (5.1) ] . STIOLTO RESPIMAT is not indicated for the treatment of asthma. STIOLTO RESPIMAT is contraindicated in patients with a hypersensitivity to tiotropium, ipratropium, olodaterol, or any component of this product [see Warnings and Precautions (5.4) ] . In clinical trials and postmarketing experience with tiotropium, immediate hypersensitivity reactions, including angioedema (including swelling of the lips, tongue, or throat), itching, or rash have been reported. Hypersensitivity reactions were also reported in clinical trials with STIOLTO RESPIMAT. Use of a LABA, including STIOLTO RESPIMAT, without an inhaled corticos",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 23.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "RESPIRATORY (INHALATION)",
            storageConditions = "Storage Store at 20°C to 25°C (68°F to 77°F); excursions permitted to 15°C to 30°C (59°F to 86°F) [see USP Controlled Room Temperature]. Avoid freezing.",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 048 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "ce26c1bb-c50d-7731-6877-0b8a5fda98df",
            drugName = "Budesonide",
            genericName = "Budesonide",
            dosage = "Directions Read insert (inside package) on how to: get a new bottle ready (primed) before first use prime bottle again if not used for two days use the spray clean the spray nozzle ADULTS AND CHILDREN 12 YEARS OF AGE AND OLDER adults and children 12 years of age and older once daily, spray 2 times into each nostril while sniffing gently once your allergy symptoms improve, reduce to 1 spray in each nostril per day CHILDREN 6 TO UNDER 12 YEARS OF AGE the growth rate of some children may be slower while using this product. Talk to your child’s doctor if your child needs to use the spray for longer than two months a year children 6 to under 12 years of age an adult should supervise use once daily, spray 1 time into each nostril while sniffing gently if allergy symptoms do not improve, increase",
            indications = "Use s Temporarily relieves these symptoms of hay fever or other upper respiratory allergies: • nasal congestion • runny nose • itchy nose • sneezing",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 23.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "NASAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 049 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "cb9a2970-e0e8-4faf-9ed9-06bec3649207",
            drugName = "Acyclovir",
            genericName = "Acyclovir",
            dosage = "DOSAGE AND ADMINISTRATION Acute Treatment of Herpes Zoster : 800 mg every 4 hours orally, 5 times daily for 7 to 10 days. Genital Herpes : Treatment of Initial Genital Herpes: 200 mg every 4 hours, 5 times daily for 10 days. Chronic Suppressive Therapy for Recurrent Disease: 400 mg 2 times daily for up to 12 months, followed by re-evaluation. Alternative regimens have included doses ranging from 200 mg 3 times daily to 200 mg 5 times daily. The frequency and severity of episodes of untreated genital herpes may change over time. After 1 year of therapy, the frequency and severity of the patient's genital herpes infection should be re-evaluated to assess the need for continuation of therapy with acyclovir. Intermittent Therapy: 200 mg every 4 hours, 5 times daily for 5 days. Therapy should b",
            indications = "INDICATIONS AND USAGE Herpes Zoster Infections : Acyclovir is indicated for the acute treatment of herpes zoster (shingles). Genital Herpes : Acyclovir is indicated for the treatment of initial episodes and the management of recurrent episodes of genital herpes. Chickenpox : Acyclovir is indicated for the treatment of chickenpox (varicella).",
            contraindications = "CONTRAINDICATIONS Acyclovir is contraindicated for patients who develop hypersensitivity to acyclovir or valacyclovir.",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 6.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "CATEGORY B"
        ))

        // REGISTRY NO: 050 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "187a99bd-1c9b-8eaf-e063-6394a90a2a74",
            drugName = "Oseltamivir Phosphate",
            genericName = "Oseltamivir",
            dosage = "2 DOSAGE AND ADMINISTRATION Treatment of influenza Adults and adolescents (13 years and older): 75 mg twice daily for 5 days ( 2.2 ) Pediatric patients 1 to 12 years of age: Based on weight twice daily for 5 days ( 2.2 ) Pediatric patients 2 weeks to less than 1 year of age: 3mg/kg twice daily for 5 days ( 2.2 ) Renally impaired adult patients (creatinine clearance >30 to 60 mL/min): Reduce to 30 mg twice daily for 5 days ( 2.4 ) Renally impaired adult patients (creatinine clearance >10 to 30 mL/min): Reduce to 30 mg once daily for 5 days ( 2.4 ) ESRD patients on hemodialysis: Reduce to 30 mg immediately and then 30 mg after every hemodialysis cycle. Treatment duration not to exceed 5 days ( 2.4 ) ESRD patients on CAPD: Reduce to a single 30 mg dose immediately ( 2.4 ) Prophylaxis of influ",
            indications = "1 INDICATIONS AND USAGE Oseltamivir phosphate for oral suspension is an influenza neuraminidase inhibitor (NAI) indicated for: Treatment of acute, uncomplicated influenza A and B in patients 2 weeks of age and older who have been symptomatic for no more than 48 hours. ( 1.1 ) Prophylaxis of influenza A and B in patients 1 year and older. ( 1.2 ) Limitations of Use : Not a substitute for annual influenza vaccination. ( 1.3 ) Consider available information on influenza drug susceptibility patterns and treatment effects when deciding whether to use. ( 1.3 ) Not recommended for patients with end-stage renal disease not undergoing dialysis. ( 1.3 ) 1.1 Treatment of Influenza Oseltamivir phosphate for oral suspension is indicated for the treatment of acute, uncomplicated illness due to influenza",
            contraindications = "4 CONTRAINDICATIONS Oseltamivir phosphate for oral suspension is contraindicated in patients with known serious hypersensitivity to oseltamivir or any component of the product. Severe allergic reactions have included anaphylaxis and serious skin reactions including toxic epidermal necrolysis, Stevens-Johnson Syndrome, and erythema multiforme [see Warnings and Precautions (5.1) ] . Patients with known serious hypersensitivity to oseltamivir or any of the components of oseltamivir phosphate for oral suspension ( 4 )",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 6.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Storage Store dry powder at 20º to 25ºC (68º to 77ºF); excursions permitted to 15º to 30ºC (59º to 86ºF) [See USP Controlled Room Temperature]. Preserved in well closed container. Store constituted oral suspension under refrigeration for up to 17 days at 2º to 8ºC (36º to 46ºF). Do not freeze. Alternatively, store constituted oral suspension for up to 10 days at 20º to 25ºC (68º to 77ºF); excursions permitted to 15º to 30ºC (59º to 86ºF) [See USP Controlled Room Temperature]. Keep the bottle in the outer carton in order to protect from light.",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 051 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "2da2a777-2835-429d-b73a-31b8e0bde571",
            drugName = "Albendazole",
            genericName = "Albendazole",
            dosage = "2 DOSAGE AND ADMINISTRATION Patients weighing 60 kg or greater, 400 mg twice daily; less than 60 kg, 15 mg/kg/day in divided doses twice daily (maximum total daily dose 800 mg). Albendazole tablets should be taken with food. ( 2 ) Hydatid disease: 28-day cycle followed by 14-day albendazole-free interval for a total of 3 cycles. ( 2 ) Neurocysticercosis: 8 to 30 days. ( 2 ) See additional important information in the Full Prescribing Information. ( 2 ) 2.1 Dosage Dosing of albendazole will vary depending upon the indication. Albendazole tablets may be crushed or chewed and swallowed with a drink of water. Albendazole tablets should be taken with food [see Clinical Pharmacology (12.3) ] . Table 1: Albendazole Dosage Indication Patient Weight Dose Duration Hydatid Disease 60 kg or greater 40",
            indications = "1 INDICATIONS AND USAGE Albendazole is an anthelmintic drug indicated for: Treatment of parenchymal neurocysticercosis due to active lesions caused by larval forms of the pork tapeworm, Taenia solium . ( 1.1 ) Treatment of cystic hydatid disease of the liver, lung, and peritoneum, caused by the larval form of the dog tapeworm, Echinococcus granulosus . ( 1.2 ) 1.1 Neurocysticercosis Albendazole is indicated for the treatment of parenchymal neurocysticercosis due to active lesions caused by larval forms of the pork tapeworm, Taenia solium . 1.2 Hydatid Disease Albendazole is indicated for the treatment of cystic hydatid disease of the liver, lung, and peritoneum, caused by the larval form of the dog tapeworm, Echinococcus granulosus .",
            contraindications = "4 CONTRAINDICATIONS Albendazole is contraindicated in patients with known hypersensitivity to the benzimidazole class of compounds or any components of albendazole. Patients with known hypersensitivity to the benzimidazole class of compounds or any components of albendazole.",
            maxMgPerKg = 15.0f,
            maxDailyMg = 800.0,
            maxSingleDoseMg = 400.0,
            source = "OpenFDA/WHO 6.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "P02CA03",
            route = "ORAL",
            storageConditions = "16.2 Storage and Handling Store at 20° to 25°C (68° to 77°F) [See USP Controlled Room Temperature].",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 052 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "06f2293c-2cf4-70d4-e063-6394a90a3a56",
            drugName = "Ivermectin",
            genericName = "Ivermectin",
            dosage = "DOSAGE AND ADMINISTRATION Strongyloidiasis The recommended dosage of ivermectin Tablets for the treatment of strongyloidiasis is a single oral dose designed to provide approximately 200 mcg of ivermectin per kg of body weight. See Table 1 for dosage guidelines. Patients should take tablets on an empty stomach with water (See CLINICAL PHARMACOLOGY, Pharmacokinetics ). In general, additional doses are not necessary. However, follow-up stool examinations should be performed to verify eradication of infection (See CLINICAL PHARMACOLOGY, Clinical Studies ). Table 1: Dosage Guidelines for Ivermectin Tablets for Strongyloidiasis Body Weight (kg) Single Oral Dose Number of 3-mg Tablets 15-24 1 tablet 25-35 2 tablets 36-50 3 tablets 51-65 4 tablets 66-79 5 tablets ≥ 80 200 mcg/kg Onchocerciasis The",
            indications = "INDICATIONS AND USAGE Ivermectin is indicated for the treatment of the following infections: Strongyloidiasis of the intestinal tract Ivermectin is indicated for the treatment of intestinal (i.e., nondisseminated) strongyloidiasis due to the nematode parasite Strongyloides stercoralis . This indication is based on clinical studies of both comparative and open-label designs, in which 64-100% of infected patients were cured following a single 200-mcg/kg dose of ivermectin (See CLINICAL PHARMACOLOGY, Clinical Studies ). Onchocerciasis Ivermectin is indicated for the treatment of onchocerciasis due to the nematode parasite Onchocerca volvulus . This indication is based on randomized, double-blind, placebo-controlled and comparative studies conducted in 1427 patients in onchocerciasis-endemic a",
            contraindications = "CONTRAINDICATIONS Ivermectin Tablets are contraindicated in patients who are hypersensitive to any component of this product.",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 6.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Storage Store at temperatures below 30°C (86°F).",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 053 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "16be47d2-4bd1-4410-a633-476cdbbbceec",
            drugName = "PRAZIQUANTEL",
            genericName = "Praziquantel",
            dosage = "2 DOSAGE AND ADMINISTRATION Schistosomiasis : 20 mg/kg body weight 3 times a day separated by 4 to 6 hours for 1 day only. ( 2.1 ) Clonorchiasis and Opisthorchiasis : 25 mg/kg 3 times a day separated by 4 to 6 hours for 1 day only. ( 2.1 ) Take with water during meals. Do not chew or keep segments in the mouth. ( 2.2 ) For pediatric patients under 6 years of age, the tablets may be crushed or disintegrated and mixed with semi-solid food or liquid. ( 2.2 ) For additional administration instructions see the full prescribing information. 2.1 Recommended Dosage Schistosomiasis The recommended dosage for the treatment of schistosomiasis is 20 mg/kg bodyweight administered orally three times a day separated by 4 to 6 hours, for 1 day only. Clonorchiasis and Opisthorchiasis The recommended dosage",
            indications = "1 INDICATIONS AND USAGE Praziquantel tablets are indicated in patients aged 1 year and older for the treatment of the following infections: Schistosomiasis due to all species of schistosoma (for example, Schistosoma mekongi, Schistosoma japonicum, Schistosoma mansoni and Schistosoma hematobium ), and Clonorchiasis and Opisthorchiasis due to the liver flukes, Clonorchis sinensis/Opisthorchis viverrini (approval of this indication was based on studies in which the two species were not differentiated) Praziquantel tablets are an anthelmintic indicated in patients aged one year and older for the treatment of the following infections: Schistosomiasis due to all species of schistosoma (for example , Schistosoma mekongi , Schistosoma japonicum , Schistosoma mansoni and Schistosoma hematobium ), a",
            contraindications = "4 CONTRAINDICATIONS Praziquantel is contraindicated in: Patients who previously have shown hypersensitivity to praziquantel or any of the excipients in praziquantel tablets. Patients with ocular cysticercosis; since parasite destruction within the eye that occurs because of hypersensitivity reaction to the dead parasite after treatment may cause irreversible lesions, ocular cysticercosis must not be treated with praziquantel. Patients taking strong Cytochrome P450 3A enzyme (CYP 3A) inducers, such as rifampin [see Warnings and Precautions ( 5.6) and Drug Interactions ( 7.1 , 7.2 )] . Known hypersensitivity to praziquantel or any of its ingredients. ( 4.1 ) Concomitant administration with strong Cytochrome P450 3A enzyme (CYP 3A) inducers such as rifampin. ( 4 , 5.6 , 7.1 )",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 6.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 054 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "5757cf97-389e-4199-8b53-d32a18e55800",
            drugName = "VERMOX",
            genericName = "Mebendazole",
            dosage = "2 DOSAGE AND ADMINISTRATION The recommended dosage in patients one year of age and older is one VERMOX™ CHEWABLE 500 mg tablet taken as a single dose. Chew VERMOX™ CHEWABLE 500 mg tablet completely before swallowing. Do not swallow the tablet whole. For patients who have difficulty chewing the tablet, approximately 2 mL to 3 mL of drinking water can be added to a suitably sized spoon and the VERMOX™ CHEWABLE 500 mg tablet placed into the water. Within 2 minutes, the tablet absorbs the water and turns into a soft mass with semi-solid consistency, which can then be swallowed. VERMOX™ CHEWABLE 500 mg tablet can be taken without regard to food intake [see Clinical Pharmacology (12.3) ]. The recommended dosage in patients one year of age and older is one single tablet of VERMOX™ CHEWABLE 500 mg",
            indications = "1 INDICATIONS AND USAGE VERMOX™ CHEWABLE is indicated for the treatment of patients one year of age and older with gastrointestinal infections caused by Ascaris lumbricoides (roundworm) and Trichuris trichiura (whipworm). VERMOX™ CHEWABLE is an anthelmintic indicated for the treatment of patients one year of age and older with gastrointestinal infections caused by: Ascaris lumbricoides (roundworm) and Trichuris trichiura (whipworm) ( 1 ).",
            contraindications = "4 CONTRAINDICATIONS VERMOX™ CHEWABLE is contraindicated in persons with a known hypersensitivity to the drug or its excipients. Patients with a known hypersensitivity to the drug or its excipients ( 4 )",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 6.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Store below 30°C. Keep container tightly closed. Unused tablets should be discarded 1 month after the bottle is first opened. When the bottle is first opened this Discard After date should be written on the bottle label in the place provided.",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 055 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "6d4407d6-9669-4bf6-91cf-05ec2d70c96a",
            drugName = "Chloroquine Phosphate",
            genericName = "Chloroquine",
            dosage = "DOSAGE AND ADMINISTRATION The dosage of chloroquine phosphate is often expressed in terms of equivalent chloroquine base. Each 500 mg tablet of Chloroquine phosphate contains the equivalent of 300 mg chloroquine base. In infants and children the dosage is preferably calculated by body weight. Prophylaxis against chloroquine-sensitive Plasmodium species Adult Dose: The dosage for prophylaxis is 500 mg (= 300 mg base) administered once per week on exactly the same day of each week. Pediatric Dose : The dosage for prophylaxis is 5 mg calculated as base, per kg of body weight, administered once per week on exactly the same day of each week. The pediatric dose should never exceed the adult dose regardless of weight. If circumstances permit, suppressive therapy should begin two weeks prior to ex",
            indications = "INDICATIONS AND USAGE Chloroquine phosphate tablets are indicated for the: Treatment of uncomplicated malaria due to susceptible strains of P. falciparum, P.malariae, P. ovale, and P.vivax . Prophylaxis of malaria in geographic areas where resistance to chloroquine is not present. Treatment of extraintestinal amebiasis. Chloroquine phosphate tablets do not prevent relapses in patients with vivax or ovale malaria because it is not effective against exoerythrocytic forms of the parasites. Limitations of Use in Malaria: Do not use chloroquine phosphate tablets for the treatment of complicated malaria (high-grade parasitemia and/or complications e.g., cerebral malaria or acute renal failure). Do not use chloroquine phosphate tablets for malaria prophylaxis in areas where chloroquine resistance",
            contraindications = "CONTRAINDICATIONS Use of chloroquine phosphate tablets for indications other than acute malaria is contraindicated in the presence of retinal or visual field changes of any etiology. Use of chloroquine phosphate tablets is contraindicated in patients with known hypersensitivity to 4-aminoquinoline compounds.",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 6.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 056 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "a43439f4-6959-43c6-95a0-44fcdb62acea",
            drugName = "anti itch",
            genericName = "Hydrocortisone",
            dosage = "Directions • for itching of skin irritation, inflammation, and rashes: • adults and children 2 years of age and older: apply to affected area not more than 3 to 4 times daily • children under 2 years of age: do not use, ask a doctor • for external anal and genital itching, adults: • when practical, clean the affected area with mild soap and warm water and rinse thoroughly • gently dry by patting or blotting with toilet tissue or a soft cloth before applying • apply to affected area not more than 3 to 4 times daily • children under 12 years of age: ask a doctor",
            indications = "Uses • temporarily relieves itching associated with minor skin irritations, inflammation, and rashes due to: • eczema • psoriasis • poison ivy, oak, sumac • insect bites • detergents • jewelry • cosmetics • soaps • seborrheic dermatitis • temporarily relieves external anal and genital itching • other uses of this product should only be under the advice and supervision of a doctor",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 400.0,
            maxSingleDoseMg = 100.0,
            source = "OpenFDA/WHO 13.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "D07AA02",
            route = "TOPICAL",
            storageConditions = "Other information • store at 20-25°C (68-77°F)",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 057 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "3fb723a8-70e1-d700-e063-6294a90a53c5",
            drugName = "Betamethasone Dipropionate",
            genericName = "Betamethasone",
            dosage = "2 DOSAGE AND ADMINISTRATION Apply a thin film of betamethasone dipropionate cream (augmented) to the affected skin areas once or twice daily. Therapy should be discontinued when control is achieved. Betamethasone dipropionate cream (augmented) is a high-potency corticosteroid. Treatment with betamethasone dipropionate cream (augmented) should not exceed 50 g per week because of the potential for the drug to suppress the hypothalamic-pituitary-adrenal (HPA) axis [see Warnings and Precautions (5.1) ]. Betamethasone dipropionate cream (augmented) should not be used with occlusive dressings unless directed by a physician. Avoid contact with eyes. Wash hands after each application. Avoid use on the face, groin, or axillae, or if skin atrophy is present at the treatment site. Betamethasone dipro",
            indications = "1 INDICATIONS AND USAGE Betamethasone dipropionate cream (augmented) is a corticosteroid indicated for the relief of the inflammatory and pruritic manifestations of corticosteroid-responsive dermatoses in patients 13 years of age or older. Betamethasone dipropionate cream (augmented), 0.05% is a corticosteroid indicated for the relief of the inflammatory and pruritic manifestations of corticosteroid-responsive dermatoses in patients 13 years of age and older. ( 1 )",
            contraindications = "4 CONTRAINDICATIONS Betamethasone dipropionate cream (augmented), is contraindicated in patients who are hypersensitive to betamethasone dipropionate, to other corticosteroids, or to any ingredient in this preparation. Hypersensitivity to any component of this medicine. ( 4 )",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 13.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "TOPICAL",
            storageConditions = "Store at 20° to 25°C (68° to 77°F) [see USP Controlled Room Temperature]. Protect from freezing.",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 058 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "0052992a-819c-4c55-bd88-201a09a3573a",
            drugName = "Clotrimazole",
            genericName = "Clotrimazole",
            dosage = "Directions • Wash the affected area and dry thoroughly. ● Apply a thin layer of this product over affected area twice daily (morning and night), or as directed by a doctor. ● Supervise children in the use of this product. ● For athlete’s foot, pay special attention to the spaces between the toes; wear well-fitting ventilated shoes, and change shoes and socks at least once daily. ● For athlete’s foot and ringworm, use daily for 4 weeks. For jock itch, use daily for 2 weeks. ● If conditions persists longer, consult a doctor. ● This product is not effective on the scalp or nails.",
            indications = "Uses Cures athlete’s foot (tinea pedis), jock itch (tinea cruris), ringworm (tinea corporis). Relieves the itching, irritation, redness, scaling and discomfort which can accompany these conditions.",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 13.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "TOPICAL",
            storageConditions = "Other Information • store at controlled room temperature 15°-30°C (59°- 86°F) • Close cap tightly after use.",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 059 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "9c983738-f481-4e39-b812-69b45c7e9401",
            drugName = "MUPIROCIN",
            genericName = "Mupirocin",
            dosage = "2 DOSAGE AND ADMINISTRATION For Topical Use Only. Apply a small amount of mupirocin cream, with a cotton swab or gauze pad, to the affected area 3 times daily for 10 days. Cover the treated area with gauze dressing if desired. Re-evaluate patients not showing a clinical response within 3 to 5 days. Mupirocin cream is not for intranasal, ophthalmic, or other mucosal use [see Warnings and Precautions ( 5.2 , 5.6 )]. Do not apply mupirocin cream concurrently with any other lotions, creams or ointments [see Clinical Pharmacology ( 12.3 )]. For Topical Use Only. ( 2 ) Apply a small amount of mupirocin cream, with a cotton swab or gauze pad, to the affected area 3 times daily for 10 days. ( 2 ) Re-evaluate patients not showing a clinical response within 3 to 5 days. ( 2 ) Not for intranasal, oph",
            indications = "1 INDICATIONS AND USAGE Mupirocin cream is indicated for the treatment of secondarily infected traumatic skin lesions (up to 10 cm in length or 100 cm 2 in area) due to susceptible isolates of Staphylococcus aureus (S. aureus) and Streptococcus pyogenes (S. pyogenes) . Mupirocin cream is an RNA synthetase inhibitor antibacterial indicated for the treatment of secondarily infected traumatic skin lesions (up to 10 cm in length or 100 cm 2 in area) due to susceptible isolates of Staphylococcus aureus and Streptococcus pyogenes . ( 1 )",
            contraindications = "4 CONTRAINDICATIONS Mupirocin cream is contraindicated in patients with known hypersensitivity to mupirocin or any of the excipients of mupirocin cream. Known hypersensitivity to mupirocin or any of the excipients of mupirocin cream. ( 4 )",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 13.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "TOPICAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 060 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "16e69f99-ef67-4d31-8f52-e792c0d73ca8",
            drugName = "good sense lice killing creme rinse",
            genericName = "Permethrin",
            dosage = "Directions Inspect • all household members should be checked by another person for lice and/or nits (eggs) • use a magnifying glass in bright light to help you see the lice and nits (eggs) • use a tool, such as a comb or two unsharpened pencils to lift and part the hair • look for tiny nits near the scalp, beginning at the back of the neck and behind the ears • small sections of hair (1-2 inches wide) should be examined at a time • unlike dandruff, nits stick to the hair. Dandruff should move when lightly touched. • if either lice or nits (eggs) are found, treat with the creme rinse Treat • wash hair with a shampoo without conditioner. Do not use a shampoo that contains a conditioner or a conditioner alone since this may decrease the activity of the creme rinse. Rinse with water. • towel d",
            indications = "Use treats head lice",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 13.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "TOPICAL",
            storageConditions = "Other information • read all the directions and warnings in the Consumer Information Insert before use. Keep the carton. It contains important information. • store at 20 to 25°C (68 to 77°F)",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 061 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "8aec6ed1-af2d-415f-bb5f-e56da3045deb",
            drugName = "Ketoconazole",
            genericName = "Ketoconazole",
            dosage = "DOSAGE & ADMINISTRATION Apply the shampoo to the damp skin of the affected area and a wide margin surrounding this area. Lather, leave in place for 5 minutes, and then rinse off with water. One application of the shampoo should be sufficient.",
            indications = "INDICATIONS & USAGE Ketoconazole shampoo, 2% is indicated for the treatment of tinea (pityriasis) versicolor caused by or presumed to be caused by Pityrosporum orbiculare (also known as Malassezia furfur or M. orbiculare ) . Note: Tinea (pityriasis) versicolor may give rise to hyperpigmented or hypopigmented patches on the trunk which may extend to the neck, arms and upper thighs. Treatment of the infection may not immediately result in normalization of pigment to the affected sites. Normalization of pigment following successful therapy is variable and may take months, depending on individual skin type and incidental sun exposure. Although tinea versicolor is not contagious, it may recur because the organism that causes the disease is part of the normal skin flora.",
            contraindications = "CONTRAINDICATIONS Ketoconazole shampoo, 2% is contraindicated in persons who have known hypersensitivity to the active ingredient or excipients of this formulation.",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 13.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "TOPICAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "CATEGORY C"
        ))

        // REGISTRY NO: 062 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "f92d2c6c-a52a-4251-bdfc-4d6f8a668dc7",
            drugName = "Terbinafine",
            genericName = "Terbinafine",
            dosage = "2 DOSAGE AND ADMINISTRATION • Prior to administering, evaluate patients for evidence of chronic or active liver disease. • Fingernail onychomycosis: One tablet, once daily for 6 weeks. • Toenail onychomycosis: One tablet, once daily for 12 weeks. 2.1 Assessment Prior to Initiation Before administering terbinafine tablets, evaluate patients for evidence of chronic or active liver disease [see Contraindications (4) and ]. 2.2 Dosage Fingernail onychomycosis: One 250 mg tablet once daily for 6 weeks. Toenail onychomycosis: One 250 mg tablet once daily for 12 weeks. The optimal clinical effect is seen some months after mycological cure and cessation of treatment. This is related to the period required for outgrowth of healthy nail.",
            indications = "1 INDICATIONS AND USAGE Terbinafine tablets are indicated for the treatment of onychomycosis of the toenail or fingernail due to dermatophytes (tinea unguium). Prior to initiating treatment, appropriate nail specimens for laboratory testing [potassium hydroxide (KOH) preparation, fungal culture, or nail biopsy] should be obtained to confirm the diagnosis of onychomycosis. Terbinafine tablets are an allylamine antifungal indicated for the treatment of onychomycosis of the toenail or fingernail due to dermatophytes (tinea unguium).",
            contraindications = "4 CONTRAINDICATIONS Terbinafine tablets are contraindicated in patients with: • Chronic or active liver disease [see ] • History of allergic reaction to oral terbinafine because of the risk of anaphylaxis [see ] • Chronic or active liver disease. (4) • History of allergic reaction to oral terbinafine because of the risk of anaphylaxis. (4)",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 13.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 063 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "41b76910-3e17-3254-e063-6294a90adb54",
            drugName = "Tri-Vite Drops with Fluoride",
            genericName = "Vitamin A",
            dosage = "DOSAGE AND ADMINISTRATION 1.0 mL daily or as directed by physician. May be dropped directly into the mouth with dropper; or mixed with cereal, fruit juice or other food. Tri-Vite Drops with Fluoride 0.25 mg is available in 50 mL bottles with accompanying calibrated dropper.",
            indications = "INDICATIONS AND USAGE Supplementation of the diet with vitamins A, C and D. Tri-Vite Drops with Fluoride 0.25 mg also provides fluoride for caries prophylaxis. The American Academy of Pediatrics recommends that children up to age 16, in areas where drinking water contains less than optimal levels of fluoride, receive daily fluoride supplementation. The American Academy of Pediatrics recommend that infants and young children 6 months to 3 years of age, in areas where the drinking water contains less than 0.3 ppm of fluoride, and children 3-6 years of age, in areas where the drinking water contains 0.3 through 0.6 ppm of fluoride, receive 0.25 mg of supplemental fluoride daily which is provided in a dose of 1 mL of Tri-Vite Drops with Fluoride 0.25 mg (See Dosage and Administration ). Tri-Vi",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 200000.0,
            maxSingleDoseMg = 200000.0,
            source = "OpenFDA/WHO 27.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "A11CA01",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 064 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "3de7bf90-9678-bd5a-e063-6394a90aeea7",
            drugName = "DISCOUNT DRUG MART",
            genericName = "Vitamin C",
            dosage = "Directions ■ adults and children 5 years and over: dissolve 1 drop slowly in the mouth. Repeat every 2 hours as needed. ■ children under 5 years: ask a doctor.",
            indications = "Use temporary relieves: ■ cough due to cold ■ occasional minor irritation or sore throat",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 27.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Other information ■ 10 calories per drop ■ contains: SOY.",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 065 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "41b76910-3e17-3254-e063-6294a90adb54",
            drugName = "Tri-Vite Drops with Fluoride",
            genericName = "Vitamin D",
            dosage = "DOSAGE AND ADMINISTRATION 1.0 mL daily or as directed by physician. May be dropped directly into the mouth with dropper; or mixed with cereal, fruit juice or other food. Tri-Vite Drops with Fluoride 0.25 mg is available in 50 mL bottles with accompanying calibrated dropper.",
            indications = "INDICATIONS AND USAGE Supplementation of the diet with vitamins A, C and D. Tri-Vite Drops with Fluoride 0.25 mg also provides fluoride for caries prophylaxis. The American Academy of Pediatrics recommends that children up to age 16, in areas where drinking water contains less than optimal levels of fluoride, receive daily fluoride supplementation. The American Academy of Pediatrics recommend that infants and young children 6 months to 3 years of age, in areas where the drinking water contains less than 0.3 ppm of fluoride, and children 3-6 years of age, in areas where the drinking water contains 0.3 through 0.6 ppm of fluoride, receive 0.25 mg of supplemental fluoride daily which is provided in a dose of 1 mL of Tri-Vite Drops with Fluoride 0.25 mg (See Dosage and Administration ). Tri-Vi",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 27.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 066 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "7aa8c3c6-7a91-4217-9e48-e1dfcf938060",
            drugName = "Integra F",
            genericName = "Folic Acid",
            dosage = "DOSAGE AND ADMINISTRATION: Adults (persons over 12 years of age), One (1) capsule daily, between meals, or as prescribed by a physician. Do not exceed recommended dosage. Do not administer to children under the age of 12.",
            indications = "INDICATIONS: Integra FTM is indicated for the treatment of iron deficiency anemia, and folate deficiency anemia. Integra FTM is indicated in pregnancy for the prevention and treatment of iron deficiency and to supply a maintenance dosage of folic acid.",
            contraindications = "CONTRAINDICATIONS: Integra FTM is contraindicated in patients with known hypersensitivity to any of its ingredients; also, all iron compounds are contraindicated in patients with hemosiderosis, hemochromatosis, or hemolytic anemias. Pernicious anemia is a contraindication, as folic acid may obscure its signs and symptoms.",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 27.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 067 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "3a749d62-0e21-0c15-e063-6394a90ac1a8",
            drugName = "FERRUM SULPHURICUM",
            genericName = "Ferrous Sulfate",
            dosage = "Adults and children: At the onset of symptoms, dissolve 5 pellets under the tongue 3 times a day until symptoms are relieved or as directed by a doctor.",
            indications = "Uses: See symptoms on front panel. Relieves hot flashes with headaches *",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 27.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 068 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "60f41580-e08d-faad-e053-2a91aa0a9076",
            drugName = "ANTACID",
            genericName = "Calcium Carbonate",
            dosage = "Directions take one to four tablets daily. do not take more than 4 tablets in 24 hours do not use the maximum dosage for more than 2 weeks",
            indications = "Uses relieves acid indigestion heartburn sour stomach upset stomach associated with these symptoms",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 27.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 069 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "3f74c669-3182-952f-e063-6294a90aad92",
            drugName = "Potassium Chloride",
            genericName = "Potassium Chloride",
            dosage = "2 DOSAGE AND ADMINISTRATION Monitor serum potassium and adjust dosages accordingly (2.1) If serum potassium is less than 2.5 mEq/L, use intravenous potassium instead of oral supplementation (2.1) Take with meals and with a glass of water or other liquid. Swallow tablets whole without crushing, chewing or sucking. (2.1) Treatment of hypokalemia : Doses range from 40 to 100 mEq/day in divided doses. Limit doses to 40 mEq per dose. (2.2) Prevention of hypokalemia : Typical dose is 20 mEq per day. (2.2) 2.1 Administration and Monitoring If serum potassium concentration is less than 2.5 mEq/L, use intravenous potassium instead of oral supplementation. Monitoring Monitor serum potassium and adjust dosages accordingly. Monitor serum potassium periodically during maintenance therapy to ensure pota",
            indications = "1 INDICATIONS AND USAGE Potassium Chloride Extended-Release Tablets are indicated for the treatment and prophylaxis of hypokalemia with or without metabolic alkalosis, in patients for whom dietary management with potassium-rich foods or diuretic dose reduction is insufficient. Potassium Chloride Extended-Release Tablets are a potassium salt, indicated for the treatment and prophylaxis of hypokalemia with or without metabolic alkalosis in patients for whom dietary management with potassium-rich foods or diuretic dose reduction is insufficient. (1)",
            contraindications = "4 CONTRAINDICATIONS Potassium chloride is contraindicated in patients on triamterene and amiloride. Concomitant use with triamterene and amiloride (4)",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 27.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "ORAL",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))

        // REGISTRY NO: 070 | FAIL-CLOSED ENABLED
        drugs.add(PharmacopeiaEntry(
            id = "47491040-0b87-ee67-e063-6294a90a47d4",
            drugName = "CVS Astringent Eye",
            genericName = "Zinc Sulfate",
            dosage = "Directions Instill 1 or 2 drops in the affected eye(s) up to four times daily.",
            indications = "Uses for temporary relief of discomfort and redness of the eye due to minor eye irritations",
            contraindications = "Standard precautions",
            maxMgPerKg = null,
            maxDailyMg = 4000.0,
            maxSingleDoseMg = null,
            source = "OpenFDA/WHO 27.0",
            lastUpdated = ts,
            isHighRisk = false,
            atcCode = "N/A",
            route = "OPHTHALMIC",
            storageConditions = "Standard Room Temp",
            pregnancyCategory = "N/A"
        ))


        val interactions = listOf(
            InteractionEntity("WHO-103", "WHO-102", "MAJOR", "MAJOR", "Potentiated GI bleed.", "Potentiated GI bleed.")
        )
        val protocols = listOf(
            FirstAidEntity(1, "Burns", "Burns", 2, "Cool care.", "Cool care.", "[]", null, null, "burn", "WHO", "2026-05-14")
        )
        return Triple(drugs, interactions, protocols)
    }
}
