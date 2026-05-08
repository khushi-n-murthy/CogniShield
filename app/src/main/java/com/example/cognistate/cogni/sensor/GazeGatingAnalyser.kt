package com.example.cognistate.cogni.sensor

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * GazeGatingAnalyser — estimates cognitive overload from pupil dilation and
 * saccadic eye movement using the selfie camera + MediaPipe FaceLandmarker.
 *
 * All inference runs on the Samsung NPU via MediaPipe's GPU/NNAPI delegate.
 * No frames leave the device.
 *
 * Algorithm:
 *   1. Receive camera frames from the caller (e.g., CameraX ImageAnalysis).
 *   2. Run MediaPipe FaceLandmarker to extract iris landmarks (landmarks 468–477).
 *   3. Compute iris diameter ratio (iris diameter / face width) as dilation proxy.
 *   4. Track saccadic velocity (iris centre displacement between frames / Δt).
 *   5. Fuse dilation ratio and saccade velocity into a gaze stress score [0, 1].
 *
 * Usage:
 *   val gaze = GazeGatingAnalyser(context)
 *   gaze.init()                     // call once, off main thread
 *   gaze.analyseFrame(bitmap, ts)   // call from CameraX Analyser
 *   val score = gaze.gazeScoreFlow.value
 *   gaze.close()
 *
 * Note: Camera permission (Manifest.permission.CAMERA) must be granted before
 * calling init(). CogniEngine handles the permission check.
 */
class GazeGatingAnalyser(private val context: Context) {

    private val TAG = "GazeGating"

    private var faceLandmarker: FaceLandmarker? = null

    // Previous iris centre position for saccade velocity calculation
    private var prevIrisCx = 0f
    private var prevIrisCy = 0f
    private var prevFrameTs = 0L

    // Rolling baseline for dilation (60-frame ~= 2 seconds at 30 fps)
    private val dilationWindow = ArrayDeque<Float>(60)

    // Exposed gaze stress score [0, 1]
    private val _gazeScoreFlow = MutableStateFlow(0f)
    val gazeScoreFlow: StateFlow<Float> = _gazeScoreFlow.asStateFlow()

    companion object {
        // MediaPipe iris landmark indices (within the 478-point FaceMesh model)
        // 473 = left iris centre, 468 = right iris centre
        // 474-477 = left iris border, 469-472 = right iris border
        private const val LEFT_IRIS_CENTER = 473
        private const val RIGHT_IRIS_CENTER = 468
        private const val LEFT_IRIS_TOP = 474
        private const val LEFT_IRIS_BOTTOM = 476

        // Max saccadic velocity in normalised face-width units per second
        // Calm reading ≈ 0.05, stressed scanning ≈ 0.4+
        private const val MAX_SACCADE_VELOCITY = 0.4f
    }

    /**
     * Initialises the MediaPipe FaceLandmarker with GPU/NNAPI delegate.
     * Must be called before [analyseFrame]. Safe to call on a background thread.
     */
    fun init() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")  // bundled in assets/
                .setDelegate(Delegate.GPU)                   // falls back to CPU automatically
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumFaces(1)                              // single user, save compute
                .setOutputFaceBlendshapes(false)             // not needed
                .setRunningMode(
                    com.google.mediapipe.tasks.vision.core.RunningMode.IMAGE
                )
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.i(TAG, "MediaPipe FaceLandmarker initialised (GPU delegate)")
        } catch (e: Exception) {
            Log.e(TAG, "FaceLandmarker init failed — gaze score will be 0.0", e)
        }
    }

    /**
     * Analyses a single camera frame and updates [gazeScoreFlow].
     *
     * @param bitmap     Camera frame as Bitmap (ARGB_8888 recommended).
     * @param timestampMs Frame capture time in milliseconds.
     */
    fun analyseFrame(bitmap: Bitmap, timestampMs: Long) {
        val lm = faceLandmarker ?: return   // graceful no-op if not initialised

        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: FaceLandmarkerResult = lm.detect(mpImage)

            if (result.faceLandmarks().isEmpty()) {
                // No face detected — keep last known score, don't spike
                return
            }

            val landmarks = result.faceLandmarks()[0]   // first (only) face

            // --- Pupil dilation proxy ---
            // Iris diameter / face bounding width → normalised dilation ratio
            val irisTop = landmarks[LEFT_IRIS_TOP]
            val irisBottom = landmarks[LEFT_IRIS_BOTTOM]
            val irisDiameter = sqrt(
                (irisTop.x() - irisBottom.x()).let { it * it } +
                (irisTop.y() - irisBottom.y()).let { it * it }
            )
            // Track rolling baseline and compute dilation relative to baseline
            if (dilationWindow.size >= 60) dilationWindow.removeFirst()
            dilationWindow.addLast(irisDiameter)
            val baseline = dilationWindow.average().toFloat()
            val dilationRatio = if (baseline > 0f) {
                ((irisDiameter - baseline) / baseline).coerceIn(-1f, 1f)
                    .let { (it + 1f) / 2f }  // remap [-1,1] → [0,1]
            } else 0.5f

            // --- Saccadic velocity ---
            val irisCentre = landmarks[LEFT_IRIS_CENTER]
            val dt = ((timestampMs - prevFrameTs) / 1000f).coerceAtLeast(0.016f) // seconds
            val saccadeVelocity = if (prevFrameTs > 0L) {
                val dx = irisCentre.x() - prevIrisCx
                val dy = irisCentre.y() - prevIrisCy
                sqrt(dx * dx + dy * dy) / dt
            } else 0f

            prevIrisCx = irisCentre.x()
            prevIrisCy = irisCentre.y()
            prevFrameTs = timestampMs

            val saccadeScore = (saccadeVelocity / MAX_SACCADE_VELOCITY).coerceIn(0f, 1f)

            // Fuse: 60% dilation, 40% saccade velocity
            val gazeScore = 0.6f * dilationRatio + 0.4f * saccadeScore
            _gazeScoreFlow.value = gazeScore

        } catch (e: Exception) {
            Log.e(TAG, "Frame analysis error — keeping previous gaze score", e)
        }
    }

    /** Releases MediaPipe resources. Call from the owning Service's onDestroy(). */
    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
        Log.i(TAG, "GazeGatingAnalyser closed")
    }
}
