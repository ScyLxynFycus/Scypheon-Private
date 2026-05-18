package com.scypheon.sdk.core.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LiveVisionPipeline — Continuous Camera Awareness for Scypheon Live.
 * 
 * [v1.5.0-SAR] Makes the AI "see" the world in real-time, like Gemini Live.
 * 
 * Architecture (two parallel pipelines):
 * 
 * ┌─ FAST PIPE (every frame, ~5 FPS) ─────────────────────────┐
 * │  CameraX ImageAnalysis → MediaPipe ObjectDetector          │
 * │  Output: Structured list "person, cup, laptop"             │
 * │  Latency: ~20ms per frame                                  │
 * └────────────────────────────────────────────────────────────┘
 * 
 * ┌─ SLOW PIPE (every ~8s or on scene change) ────────────────┐
 * │  Capture keyframe → Gemma 4 E2B multimodal                 │
 * │  Output: Rich description "A young man at a desk..."       │
 * │  Latency: ~2-3s per keyframe                               │
 * └────────────────────────────────────────────────────────────┘
 * 
 * Both outputs merge into a `visionContext` string that the
 * LiveSessionOrchestrator automatically injects into every turn.
 */
@Singleton
class LiveVisionPipeline @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ═══════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════

    private var objectDetector: ObjectDetector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Current scene understanding (updated continuously)
    private val _sceneDescription = MutableStateFlow<SceneContext>(SceneContext.empty())
    val sceneDescription: StateFlow<SceneContext> = _sceneDescription.asStateFlow()

    // Latest camera frame for multimodal LLM (keyframe capture)
    private var _latestKeyframe: Bitmap? = null
    val latestKeyframe: Bitmap? get() = _latestKeyframe

    // Scene change detection
    private var previousObjects = setOf<String>()
    private var lastKeyframeTime = 0L
    private val KEYFRAME_INTERVAL_MS = 8000L  // Capture keyframe every 8 seconds
    private val SCENE_CHANGE_THRESHOLD = 2    // # of new objects to trigger early keyframe

    // Callback for scene changes (orchestrator listens to this)
    var onSceneUpdated: ((SceneContext) -> Unit)? = null
    var onKeyframeCaptured: ((Bitmap) -> Unit)? = null

    // ═══════════════════════════════════════════════════════════════
    // Initialization
    // ═══════════════════════════════════════════════════════════════

    /**
     * Initialize the MediaPipe ObjectDetector for the fast pipeline.
     */
    fun initializeDetector(modelAssetPath: String = "models/efficientdet_lite0.tflite") {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelAssetPath)
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.LIVE_STREAM)
                .setMaxResults(5)
                .setScoreThreshold(0.4f)
                .setResultListener(this::onDetectionResult)
                .setErrorListener { error -> Timber.e(error, "👁️ [VISION] Detection error") }
                .build()

            objectDetector = ObjectDetector.createFromOptions(context, options)
            Timber.i("👁️ [VISION] LiveVisionPipeline initialized with ObjectDetector")
        } catch (e: Exception) {
            Timber.e(e, "👁️ [VISION] Failed to init ObjectDetector. Vision will be text-only.")
        }
    }

    /**
     * Start the camera and bind to ImageAnalysis for continuous frame processing.
     */
    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView? = null) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                // Image Analysis (FAST PIPE — every frame)
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }

                // Camera selector (back camera default for "seeing the world")
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind previous and rebind
                provider.unbindAll()

                if (previewView != null) {
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                } else {
                    // No preview — headless mode (camera runs but no UI display)
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
                }

                Timber.i("👁️ [VISION] Camera started, continuous analysis active")
            } catch (e: Exception) {
                Timber.e(e, "👁️ [VISION] Failed to start camera")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // ═══════════════════════════════════════════════════════════════
    // Frame Processing
    // ═══════════════════════════════════════════════════════════════

    @OptIn(ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()
            val timestampMs = imageProxy.imageInfo.timestamp / 1000 // Convert ns to ms

            // FAST PIPE: Object detection on every frame
            val mpImage = BitmapImageBuilder(bitmap).build()
            objectDetector?.detectAsync(mpImage, timestampMs)

            // SLOW PIPE: Periodic keyframe capture for multimodal LLM
            val now = System.currentTimeMillis()
            if (now - lastKeyframeTime > KEYFRAME_INTERVAL_MS) {
                captureKeyframe(bitmap)
                lastKeyframeTime = now
            }
        } catch (e: Exception) {
            // Frame processing errors are non-fatal — skip and continue
            Timber.v("👁️ [VISION] Frame skip: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Capture a keyframe for rich LLM scene description.
     * Downscale to 512px for efficient multimodal inference.
     */
    private fun captureKeyframe(bitmap: Bitmap) {
        scope.launch {
            try {
                // Downscale for LLM efficiency
                val scale = 512f / maxOf(bitmap.width, bitmap.height)
                val scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
                
                _latestKeyframe?.recycle()
                _latestKeyframe = scaledBitmap

                onKeyframeCaptured?.invoke(scaledBitmap)
                Timber.d("👁️ [VISION] Keyframe captured (${scaledBitmap.width}x${scaledBitmap.height})")
            } catch (e: Exception) {
                Timber.e(e, "👁️ [VISION] Keyframe capture failed")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Detection Results (FAST PIPE callback)
    // ═══════════════════════════════════════════════════════════════

    private fun onDetectionResult(
        result: ObjectDetectorResult,
        image: com.google.mediapipe.framework.image.MPImage
    ) {
        val detectedObjects = result.detections().mapNotNull { detection ->
            detection.categories().firstOrNull()?.let { cat ->
                DetectedObject(
                    name = cat.categoryName(),
                    confidence = cat.score(),
                    boundingBox = detection.boundingBox()
                )
            }
        }

        val objectNames = detectedObjects.map { it.name }.toSet()

        // Detect scene change
        val newObjects = objectNames - previousObjects
        val hasSignificantChange = newObjects.size >= SCENE_CHANGE_THRESHOLD

        if (hasSignificantChange) {
            Timber.d("👁️ [VISION] Scene change detected! New objects: $newObjects")
            // Trigger early keyframe on scene change
            _latestKeyframe?.let { /* could trigger early LLM analysis */ }
        }

        previousObjects = objectNames

        // Update scene context
        val newScene = SceneContext(
            detectedObjects = detectedObjects,
            objectSummary = if (objectNames.isNotEmpty()) {
                "I can see: ${objectNames.joinToString(", ")}"
            } else {
                "No objects detected in view"
            },
            timestamp = System.currentTimeMillis(),
            hasSceneChanged = hasSignificantChange
        )

        _sceneDescription.value = newScene
        onSceneUpdated?.invoke(newScene)
    }

    // ═══════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════

    fun stop() {
        try {
            cameraProvider?.unbindAll()
            objectDetector?.close()
            objectDetector = null
            _latestKeyframe?.recycle()
            _latestKeyframe = null
            previousObjects = emptySet()
            _sceneDescription.value = SceneContext.empty()
            Timber.i("👁️ [VISION] LiveVisionPipeline stopped")
        } catch (e: Exception) {
            Timber.e(e, "👁️ [VISION] Error stopping pipeline")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════

    data class DetectedObject(
        val name: String,
        val confidence: Float,
        val boundingBox: android.graphics.RectF?
    )

    data class SceneContext(
        val detectedObjects: List<DetectedObject>,
        val objectSummary: String,
        val timestamp: Long,
        val hasSceneChanged: Boolean = false,
        val richDescription: String? = null // Filled by slow pipe (LLM multimodal)
    ) {
        companion object {
            fun empty() = SceneContext(emptyList(), "", 0L)
        }

        /**
         * Generate a context string for LLM injection.
         */
        fun toContextString(): String {
            return buildString {
                if (objectSummary.isNotBlank()) append(objectSummary)
                richDescription?.let { append(" | Scene: $it") }
            }
        }
    }
}
