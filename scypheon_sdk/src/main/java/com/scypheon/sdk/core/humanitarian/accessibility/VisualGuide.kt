package com.scypheon.sdk.core.humanitarian.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import com.scypheon.sdk.core.memory.DualMemoryManager
import timber.log.Timber
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Humanitarian Accessibility Bridge for the Visually Impaired.
 * Uses MediaPipe's Object Detection to scan the environment in front of the user
 * and whispers (TTS) what is going on, serving as a social and physical wingman.
 */
class VisualGuide(
    private val context: Context,
    private val memoryManager: DualMemoryManager? = null
) : TextToSpeech.OnInitListener {

    private var objectDetector: ObjectDetector? = null
    private var tts: TextToSpeech? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Prevent spamming the same objects every frame
    private var lastSpokenTime = 0L
    private val ANNOUNCEMENT_COOLDOWN_MS = 5000L

    init {
        tts = TextToSpeech(context, this)
    }

    fun initialize(modelAssetPath: String) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelAssetPath)
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.LIVE_STREAM)
                .setMaxResults(3)
                .setResultListener(this::onObjectDetectionResult)
                .setErrorListener { error -> Timber.e(error, "Object Detection error") }
                .build()

            objectDetector = ObjectDetector.createFromOptions(context, options)
            Timber.i("✅ VisualGuide initialized with offline MediaPipe ObjectDetector.")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to init ObjectDetector")
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
        objectDetector?.detectAsync(mpImage, timestampMs)
    }

    private fun onObjectDetectionResult(result: ObjectDetectorResult, image: com.google.mediapipe.framework.image.MPImage) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSpokenTime < ANNOUNCEMENT_COOLDOWN_MS) return

        if (result.detections().isNotEmpty()) {
            // Get the most prominent objects
            val objectNames = result.detections().mapNotNull { detection ->
                detection.categories().firstOrNull()?.categoryName()
            }.distinct()

            if (objectNames.isNotEmpty()) {
                val description = "I see " + objectNames.joinToString(", ") + " ahead of you."
                Timber.i("👁️ Visual Guide: $description")

                tts?.speak(description, TextToSpeech.QUEUE_FLUSH, null, "VisualGuide")

                // 🧠 EPISODIC MEMORY: Save this physical vision snapshot into the RAG database.
                // This allows the user to ask later: "Where did I last see my keys?" or "Was there a chair in the last room?"
                scope.launch {
                    memoryManager?.saveMessage(
                        sessionId = "episodic_memory",
                        text = "[VISUAL_MEMORY] At ${java.util.Date()}: I saw ${objectNames.joinToString(", ")}",
                        isUser = false
                    )
                }

                lastSpokenTime = currentTime
            }
        }
    }

    fun shutdown() {
        objectDetector?.close()
        tts?.stop()
        tts?.shutdown()
    }
}
