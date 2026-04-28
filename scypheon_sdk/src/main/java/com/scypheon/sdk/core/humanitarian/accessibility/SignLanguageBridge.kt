package com.scypheon.sdk.core.humanitarian.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.scypheon.sdk.core.engine.LiteRtEliteEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

/**
 * Humanitarian Accessibility Bridge for the Deaf/Mute community.
 * Uses MediaPipe's Hand Gesture Recognition to read sign language offline.
 * The recognized raw gestures are then fed into Gemma 4 / 3n to smooth them into
 * empathetic, natural-sounding sentences before speaking them out loud via TTS.
 */
class SignLanguageBridge(
    private val context: Context,
    private val llmEngine: LiteRtEliteEngine
) : TextToSpeech.OnInitListener {

    private var gestureRecognizer: GestureRecognizer? = null
    private var tts: TextToSpeech? = null
    private var accumulatedGestures = mutableListOf<String>()

    // Timer to determine when a sentence is finished (no new gestures)
    private var lastGestureTime = 0L
    private val SENTENCE_COOLDOWN_MS = 2500L

    init {
        tts = TextToSpeech(context, this)
    }

    fun initialize(modelAssetPath: String) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelAssetPath)
                .build()

            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.LIVE_STREAM)
                .setResultListener(this::onGestureRecognitionResult)
                .setErrorListener { error -> Timber.e(error, "Gesture Recognition error") }
                .build()

            gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
            Timber.i("✅ SignLanguageBridge initialized with offline MediaPipe HandLandmarker.")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to init GestureRecognizer")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.getDefault())
        }
    }

    /**
     * Called by CameraX Analyzer on every frame.
     */
    fun processFrame(bitmap: Bitmap, timestampMs: Long) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        gestureRecognizer?.recognizeAsync(mpImage, timestampMs)

        // Check if the user stopped signing, trigger Gemma compilation
        if (accumulatedGestures.isNotEmpty() && System.currentTimeMillis() - lastGestureTime > SENTENCE_COOLDOWN_MS) {
            compileAndSpeakSentence()
        }
    }

    private fun onGestureRecognitionResult(result: GestureRecognizerResult, image: com.google.mediapipe.framework.image.MPImage) {
        if (result.gestures().isNotEmpty()) {
            val primaryGesture = result.gestures().first().first().categoryName()

            // Ignore generic 'None' or 'Unknown' gestures
            if (primaryGesture != "None" && primaryGesture != "Unknown") {
                // Prevent duplicate back-to-back spam
                if (accumulatedGestures.isEmpty() || accumulatedGestures.last() != primaryGesture) {
                    accumulatedGestures.add(primaryGesture)
                    lastGestureTime = System.currentTimeMillis()
                    Timber.i("🖐️ Sign Detected: $primaryGesture")
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun compileAndSpeakSentence() {
        val rawSentence = accumulatedGestures.joinToString(" ")
        accumulatedGestures.clear()

        val prompt = """
            You are an empathetic, confident social bridge helping a deaf person communicate with others.
            They just signed the following sequence of words/gestures: "$rawSentence"

            Convert these disjointed words into a natural, friendly, and confident spoken sentence.
            Do not use markdown. Speak as if you are them, smoothly and kindly.
            Example: if raw is "Hungry Eat Food", output "I am feeling hungry, let's get some food."
        """.trimIndent()

        Timber.i("Raw Signs: $rawSentence -> Generating Gemma 4 bridge...")

        scope.launch {
            try {
                val aiResponse = llmEngine.generateResponse(prompt).reduce { acc, value -> acc + value }
                // Speak out loud to the non-deaf person
                tts?.speak(aiResponse, TextToSpeech.QUEUE_FLUSH, null, "SignBridge")
            } catch (e: Exception) {
                Timber.e(e, "Error generating SignLanguageBridge response")
            }
        }
    }

    fun shutdown() {
        gestureRecognizer?.close()
        tts?.stop()
        tts?.shutdown()
    }
}
