package com.scypheon.sdk.core.utils

import android.content.Context
import java.util.Locale

object PromptLocalizer {
    fun getLocalizedMedicalPrompt(): String {
        val lang = Locale.getDefault().language
        return if (lang == "id") {
            "Anda adalah AI medis offline. Analisis gejala berikut dan berikan kemungkinan penyebab serta saran obat OTC jika ada."
        } else {
            "You are an offline medical AI. Analyze the following symptoms and provide possible causes along with OTC medication advice if applicable."
        }
    }

    fun getMedicalConsultantPrompt(
        context: Context? = null,
        query: String = "",
        allergies: String = "",
        conditions: String = "",
        medications: String = ""
    ): String {
        val basePrompt = getLocalizedMedicalPrompt()
        return "$basePrompt\n\nPatient Query: $query\nAllergies: $allergies\nConditions: $conditions\nCurrent Medications: $medications"
    }
}
