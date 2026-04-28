package com.scypheon.sdk.core.skills

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Specialized Skill for high-fidelity OCR and Document Extraction.
 * Uses MLKit Text Recognition (Offline).
 */
@Singleton
class DocumentSkill @Inject constructor(
    private val context: Context
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extracts all readable text from the provided image URI.
     */
    suspend fun extractText(uri: Uri): String {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            processImage(image)
        } catch (e: Exception) {
            Timber.e(e, "DocumentSkill: Failed to load image from URI")
            "Error: Failed to process document."
        }
    }

    /**
     * Extracts text from a Bitmap.
     */
    suspend fun extractText(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        return processImage(image)
    }

    private suspend fun processImage(image: InputImage): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                if (text.isNotBlank()) {
                    Timber.i("DocumentSkill: Successfully extracted ${text.length} characters.")
                    continuation.resume(text)
                } else {
                    continuation.resume("No readable text found in document.")
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "DocumentSkill: MLKit Text Recognition failed")
                continuation.resume("Error: Text recognition failed.")
            }
    }

    fun release() {
        recognizer.close()
    }
}
