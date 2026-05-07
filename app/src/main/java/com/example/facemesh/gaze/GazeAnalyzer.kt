package com.example.facemesh.gaze

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * GazeAnalyzer processes camera frames and computes a normalized gaze score.
 * It implements ImageAnalysis.Analyzer for CameraX and exposes results via StateFlow.
 */
class GazeAnalyzer(
    private val scope: CoroutineScope
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "GazeAnalyzer"
        private const val EMISSION_INTERVAL_MS = 500L
        private const val SMOOTHING_FACTOR = 0.2f // Alpha for Exponential Moving Average
    }

    // Internal mutable state for the gaze score
    private val _gazeScoreFlow = MutableStateFlow(0f)
    
    /**
     * Exposes the computed gaze score.
     * Value ranges from 0.0 to 1.0.
     */
    val gazeScoreFlow: StateFlow<Float> = _gazeScoreFlow.asStateFlow()

    private var emissionJob: Job? = null
    
    // Internal cache for the latest computed score to emit on a fixed interval
    private var latestComputedScore: Float = 0f
    private var isScoreInitialized: Boolean = false

    init {
        startEmissionLoop()
    }

    /**
     * Emits the latest gaze score at a fixed interval (500ms) using coroutines.
     */
    private fun startEmissionLoop() {
        emissionJob?.cancel()
        emissionJob = scope.launch(Dispatchers.Default) {
            while (true) {
                // Emit the latest available computed score
                _gazeScoreFlow.value = latestComputedScore
                delay(EMISSION_INTERVAL_MS)
            }
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            // 1. Prepare structure for MediaPipe FaceMesh (Placeholder)
            val faceLandmarks = extractFaceLandmarksPlaceholder(imageProxy)
            
            if (faceLandmarks.isNotEmpty()) {
                // 2. Extract iris landmarks conceptually (MediaPipe uses 468-472 for iris)
                val irisPoints = extractIrisLandmarks(faceLandmarks)
                
                // 3. Compute iris diameter using distance between landmarks
                val irisDiameter = computeIrisDiameter(irisPoints)
                
                // Add safety check
                if (irisDiameter <= 0f) return
                
                // 4. Compute face width using key facial landmarks
                val faceWidth = computeFaceWidth(faceLandmarks)
                
                // 5. Normalize iris size using face width
                if (faceWidth > 0) {
                    val normalizedIrisSize = irisDiameter / faceWidth
                    
                    // 6. Compute raw gaze score (0.0 to 1.0)
                    val rawScore = computeGazeScore(normalizedIrisSize)
                    
                    // 7. Apply Exponential Moving Average (EMA) smoothing
                    if (!isScoreInitialized) {
                        latestComputedScore = rawScore
                        isScoreInitialized = true
                    } else {
                        latestComputedScore = (rawScore * SMOOTHING_FACTOR) + (latestComputedScore * (1f - SMOOTHING_FACTOR))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing frame", e)
        } finally {
            // Crucial: always close the imageProxy to receive the next frame
            imageProxy.close()
        }
    }

    /**
     * Placeholder for extracting face landmarks from the ImageProxy.
     * In a real integration, this would pass the frame to MediaPipe.
     */
    private fun extractFaceLandmarksPlaceholder(imageProxy: ImageProxy): List<Point2D> {
        // Return dummy 478 landmarks with slight random noise so distances are non-zero and fluctuate
        return List(478) { index -> 
            val noiseX = kotlin.random.Random.nextFloat() * 5f
            val noiseY = kotlin.random.Random.nextFloat() * 5f
            Point2D(index.toFloat() * 0.5f + noiseX, index.toFloat() * 0.5f + noiseY)
        }
    }

    /**
     * Extracts landmarks corresponding to the iris.
     */
    private fun extractIrisLandmarks(landmarks: List<Point2D>): List<Point2D> {
        // Safely extract conceptual indices 468 through 472
        if (landmarks.size < 473) return emptyList()
        return landmarks.subList(468, 473)
    }

    /**
     * Computes the diameter of the iris using the distance between its boundary landmarks.
     */
    private fun computeIrisDiameter(irisLandmarks: List<Point2D>): Float {
        if (irisLandmarks.size < 4) return 0f
        
        // Conceptually: Distance between left and right iris boundaries
        val leftPoint = irisLandmarks[1] // Placeholder index relative to iris
        val rightPoint = irisLandmarks[3] // Placeholder index relative to iris
        
        return calculateDistance(leftPoint, rightPoint)
    }

    /**
     * Computes the face width using key peripheral landmarks.
     */
    private fun computeFaceWidth(landmarks: List<Point2D>): Float {
        if (landmarks.size < 454) return 0f
        
        // Conceptually: indices 234 and 454 are often left/right side bounds in MediaPipe FaceMesh
        val leftSide = landmarks[234]
        val rightSide = landmarks[454]
        
        return calculateDistance(leftSide, rightSide)
    }

    /**
     * Converts the normalized iris size into a gaze score between 0.0 and 1.0.
     */
    private fun computeGazeScore(normalizedIrisSize: Float): Float {
        // Map the normalized size using a baseline and max threshold
        val baselineThreshold = 0.03f
        val maxThreshold = 0.06f
        
        if (normalizedIrisSize <= baselineThreshold) return 0f
        if (normalizedIrisSize >= maxThreshold) return 1f
        
        return ((normalizedIrisSize - baselineThreshold) / (maxThreshold - baselineThreshold)).coerceIn(0f, 1f)
    }

    /**
     * Helper to calculate Euclidean distance between two 2D points.
     */
    private fun calculateDistance(p1: Point2D, p2: Point2D): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx.pow(2) + dy.pow(2))
    }
    
    /**
     * Simple 2D point structure for conceptual landmark representation.
     */
    data class Point2D(val x: Float, val y: Float)
    
    /**
     * Shuts down internal coroutines. Should be called when analyzer is detached.
     */
    fun shutdown() {
        emissionJob?.cancel()
    }
}
