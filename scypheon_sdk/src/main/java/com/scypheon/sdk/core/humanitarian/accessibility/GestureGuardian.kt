package com.scypheon.sdk.core.humanitarian.accessibility

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.scypheon.sdk.core.memory.DualMemoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * GestureGuardian: A humanitarian AI feature for 'Digital Equity & Inclusivity'.
 * Translates hand gestures/body language into interactive intents using LiteRT.
 */
class GestureGuardian(
    private val context: Context,
    private val memoryManager: DualMemoryManager,
    private val onKeyEvent: (String, String) -> Unit
) : com.scypheon.sdk.core.humanitarian.ScypheonAgent {

    private var gestureRecognizer: GestureRecognizer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun warmUp() {
        if (gestureRecognizer != null) return
        Timber.i(" [SAR] Warming up GestureGuardian...")
        initialize("gesture_recognizer.task")
    }

    override fun release() {
        Timber.i(" [SAR] Releasing GestureGuardian resources...")
        gestureRecognizer?.close()
        gestureRecognizer = null
    }

    override fun isReady(): Boolean = gestureRecognizer != null

    /**
     * Initializes the Gesture Detection engine.
     * Path should point to a Gesture Recognizer TFLite model.
     */
    fun initialize(modelPath: String = "gesture_recognizer.task") {
        try {
            val baseOptions = com.google.mediapipe.tasks.core.BaseOptions.builder()
                .setModelAssetPath(modelPath)
                .build()

            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.LIVE_STREAM)
                .setResultListener { result, _ -> handleResult(result) }
                .build()

            gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
            Timber.i("✅ [KINESICS] Guardian Initialized.")
        } catch (e: Exception) {
            Timber.e(e, "❌ [KINESICS] Failed to initialize GestureRecognizer.")
        }
    }

    fun processFrame(bitmap: Bitmap, timestampMs: Long) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        gestureRecognizer?.recognizeAsync(mpImage, timestampMs)
    }

    private fun handleResult(result: GestureRecognizerResult) {
        val gestures = result.gestures()
        if (gestures.isNotEmpty()) {
            val topGesture = gestures.first().first().categoryName()
            if (topGesture != "None" && topGesture != "Unknown") {
                val score = gestures.first().first().score()
                if (score > 0.8f) {
                    val eventMsg = "Detected Gesture: $topGesture (Conf: ${"%.2f".format(score)})"
                    onKeyEvent("GESTURE_DETECTED", eventMsg)
                    
                    // 🛡️ Safety & Trust: Log to BlackBox for auditability
                    Timber.d("🖐️ [KINESICS] $eventMsg")
                }
            }
        }
    }
}
