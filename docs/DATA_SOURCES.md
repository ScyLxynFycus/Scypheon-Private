# SCYPHEON: Medical Data Source Documentation
*Version: 1.0 (Phase 1 Enrichment)*
*Last Updated: 2026-05-06*

## 1. Overview
The medical module in Scypheon (specifically the humanitarian pharmacopeia) is designed to provide offline-native grounding for clinical safety triage. This document outlines the provenance, limitations, and verification status of the dataset.

## 2. Data Provenance

### 2.1 Drug Entities (Foundational)
- **Source**: World Health Organization (WHO) Model List of Essential Medicines (EML).
- **Extraction Method**: Programmatic import from structured Wikipedia tables representing the 2023 WHO EML update.
- **Coverage**: 188 medicines spanning multiple therapeutic categories (Analgesics, Antibacterials, Antimalarials, Cardiovascular, etc.).
- **ID Schema**: `WHO-xxx` format. Note that jumps in sequence (e.g., WHO-200 to WHO-500) reflect therapeutic category groupings in the original source.

### 2.2 Clinical Metadata (Enrichment)
- **Source**: FDA DailyMed (Professional Drug Labels) and WHO Model Formulary guidelines.
- **Assembly Method**: Curated by the AI model based on high-confidence training data from these public sources.
- **Fields Covered**:
  - `dose_min_mg`, `dose_max_mg`, `max_daily_mg`: Numeric values for computational validation.
  - `side_effects`, `contraindications`: Summarized clinical risk factors.
  - `mechanism_of_action`: Pharmacological description.

### 2.3 Localization
- **Source**: Machine-generated Indonesian translations.
- **Goal**: Facilitate search via common local terms (e.g., "obat darah tinggi" for Amlodipine).

## 3. Known Limitations and Risks
- **Model-Curated Data**: While sourced from official guidelines, the assembly was performed by an AI agent. It has **NOT** undergone a line-by-line review by a human pharmacist or physician.
- **Offline Context**: Data is optimized for emergency triage, not for definitive long-term treatment planning.
- **Translation Accuracy**: Local Indonesian keywords may contain medical inaccuracies or informal terms that require local verification.

## 4. Post-Hackathon Verification Plan
To reach "Production-Ready" status, the following steps are mandatory:
1. **Medical Board Review**: A panel of licensed physicians/pharmacists must verify the `dose_max_mg` and `contraindications` for all 188 drugs.
2. **Deterministic Source Locking**: Replace model-curated data with a direct SQL export from an official database (e.g., RxNorm) where possible.
3. **Local Dialect Audit**: Review Indonesian keywords with local healthcare workers in disaster-prone regions to ensure linguistic relevance.

## 5. Disclaimer
**THIS DATA IS FOR DEMONSTRATION AND EMERGENCY GROUNDING PURPOSES ONLY.** SCYPHEON is not a substitute for professional medical advice, diagnosis, or treatment. Always seek the advice of your physician or other qualified health provider with any questions you may have regarding a medical condition.

---
*Signed,*
*The Scypheon Development Team*
