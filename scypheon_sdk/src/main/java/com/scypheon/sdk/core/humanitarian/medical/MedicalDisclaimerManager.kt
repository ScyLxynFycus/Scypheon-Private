package com.scypheon.sdk.core.humanitarian.medical

import com.scypheon.sdk.core.utils.LocaleHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise Multi-lingual Disclaimer Manager:
 * Ensures life-critical warnings are dynamic and localized to the user's language.
 */
@Singleton
class MedicalDisclaimerManager @Inject constructor() {

    fun getHardWarning(drugName: String, category: String?): String {
        val language = LocaleHelper.getCurrentLanguage()
        val cat = category ?: "General"

        return when (language) {
            "in", "id" -> "🛑 [PERINGATAN KERAS]: $drugName memiliki risiko tinggi ($cat). Gunakan hanya dengan pengawasan profesional medis!"
            "es" -> "🛑 [ADVERTENCIA CRITICAL]: $drugName tiene un alto riesgo ($cat). ¡Úselo solo bajo supervisión médica profesional!"
            "fr" -> "🛑 [AVERTISSEMENT CRITIQUE]: $drugName présente un risque élevé ($cat). À utiliser uniquement sous surveillance médicale professionnelle !"
            else -> "🛑 [HARD WARNING]: $drugName has a high risk ($cat). Use only under professional medical supervision!"
        }
    }

    fun getHallucinationWarning(): String {
        val language = LocaleHelper.getCurrentLanguage()
        return when (language) {
            "in", "id" -> "⚠️ [MODERASI]: Konten terdeteksi sebagai halusinasi AI dan telah diblokir untuk keamanan Anda."
            "es" -> "⚠️ [MODERACIÓN]: Contenido detectado como alucinación de IA y bloqueado por su seguridad."
            "fr" -> "⚠️ [MODÉRATION] : Contenu détecté comme une hallucination de l'IA et bloqué pour votre sécurité."
            else -> "⚠️ [MODERATED]: Content detected as AI hallucination and blocked for your safety."
        }
    }
}
