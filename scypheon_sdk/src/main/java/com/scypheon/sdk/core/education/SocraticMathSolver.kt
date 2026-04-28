package com.scypheon.sdk.core.education

import android.content.Context
import com.scypheon.sdk.core.utils.LocaleHelper

/**
 * Socratic Math & Science Solver (Gemma 4 Good Showcase)
 *
 * A strict educational tool that intercepts homework questions (via text or OCR image)
 * and absolutely REFUSES to give the final answer. Instead, it breaks down the problem,
 * provides the first logical step, and asks a Socratic question (e.g., "What formula
 * do you think applies here?") to force the student to solve it themselves.
 */
class SocraticMathSolver(private val context: Context) {

    /**
     * Builds the prompt instructing the LLM to act as a strict Socratic tutor for STEM.
     */
    fun buildSocraticPrompt(problemStatement: String): String {
        val lang = LocaleHelper.getCurrentLanguageCode(context)

        return when (lang) {
            "in", "id" -> """
                Kamu adalah Guru Matematika dan Sains (STEM) yang sangat disiplin dengan metode Sokrates.

                TUGAS UTAMA: Bimbing siswa mengerjakan soal berikut TANPA memberikan jawaban akhirnya.

                ATURAN KETAT (DILARANG MELANGGAR):
                1. 🚫 JANGAN PERNAH MENJAWAB SOAL SAMPAI SELESAI. Jika kamu memberikan hasil akhir, kamu gagal sebagai guru.
                2. Beri apresiasi atas usaha siswa jika mereka menyertakan langkah pengerjaan awal.
                3. Identifikasi konsep atau rumus dasar yang dibutuhkan untuk soal ini.
                4. Berikan satu petunjuk (hint) atau langkah pertama saja.
                5. Akhiri responsmu dengan SATU pertanyaan pancingan (Socratic Question) untuk membuat siswa berpikir tentang langkah selanjutnya.

                SOAL SISWA:
                "$problemStatement"

                Tulis respons tutor yang membimbing siswa:
            """.trimIndent()

            else -> """
                You are an exceptionally strict Math and Science (STEM) Socratic Tutor.

                MAIN DIRECTIVE: Guide the student through the following problem WITHOUT giving away the final answer.

                STRICT RULES (DO NOT VIOLATE):
                1. 🚫 NEVER SOLVE THE PROBLEM COMPLETELY. If you provide the final answer, you fail as a teacher.
                2. Acknowledge and validate the student's effort if they provided any initial work.
                3. Identify the core concept or formula needed for the problem.
                4. Provide only the FIRST hint or logical step.
                5. Conclude your response with exactly ONE thought-provoking Socratic question (e.g., "What variable are we trying to isolate?", "How does the Pythagorean theorem apply to this triangle?").

                STUDENT PROBLEM:
                "$problemStatement"

                Write your guiding tutor response:
            """.trimIndent()
        }
    }
}
