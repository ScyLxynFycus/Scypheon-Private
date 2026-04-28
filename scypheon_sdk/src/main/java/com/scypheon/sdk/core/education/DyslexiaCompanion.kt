package com.scypheon.sdk.core.education

import android.content.Context
import com.scypheon.sdk.core.utils.LocaleHelper

/**
 * Dyslexia Reading Companion (Gemma 4 Good Showcase)
 *
 * An offline educational accessibility tool. Takes dense, complex text (e.g., from an OCR scan
 * of a textbook) and uses Gemma 4 to rewrite it into highly accessible, dyslexia-friendly
 * formats: short sentences, simple vocabulary, active voice, and bullet points.
 */
class DyslexiaCompanion(private val context: Context) {

    /**
     * Builds the system prompt instructing the LLM to format the text for dyslexic readers.
     */
    fun buildDyslexiaPrompt(rawText: String): String {
        val lang = LocaleHelper.getCurrentLanguageCode(context)

        return when (lang) {
            "in", "id" -> """
                Kamu adalah asisten membaca inklusif untuk siswa dengan disleksia.

                TUGAS: Ubah ulang teks akademik/buku yang rumit di bawah ini menjadi format yang sangat ramah untuk penderita disleksia.

                ATURAN KETAT:
                1. Gunakan kalimat yang sangat pendek (maksimal 10-15 kata per kalimat).
                2. Gunakan kata-kata yang umum dan sederhana. Hindari jargon rumit tanpa penjelasan.
                3. Pecah paragraf panjang menjadi poin-poin (bullet points).
                4. Berikan jeda/spasi (baris kosong) antar ide utama.
                5. Gunakan kalimat aktif (contoh: "Budi melempar bola", bukan "Bola dilempar oleh Budi").
                6. Tambahkan emoji yang relevan di awal setiap poin untuk memberi isyarat visual.

                TEKS ASLI:
                "$rawText"

                Tulis ulang teks di atas sesuai aturan disleksia:
            """.trimIndent()

            else -> """
                You are an inclusive reading assistant for students with dyslexia.

                TASK: Rewrite the complex academic/textbook text below into a highly dyslexia-friendly format.

                STRICT RULES:
                1. Use very short sentences (maximum 10-15 words per sentence).
                2. Use simple, everyday vocabulary. Avoid complex jargon unless you briefly define it.
                3. Break down long paragraphs into bullet points.
                4. Provide ample spacing (empty lines) between main ideas.
                5. Use active voice exclusively (e.g., "The dog chased the ball," not "The ball was chased by the dog").
                6. Add a relevant emoji at the start of each bullet point to provide visual cues.

                RAW TEXT:
                "$rawText"

                Rewrite the text above following the dyslexia rules:
            """.trimIndent()
        }
    }
}
