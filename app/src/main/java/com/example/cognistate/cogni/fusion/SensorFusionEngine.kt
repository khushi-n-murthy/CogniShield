package com.example.cognistate.cogni.fusion

import android.util.Log
import com.cognishield.model.BioFrame
import com.cognishield.model.CogniState
import com.cognishield.sensor.ImuCadenceAnalyser
import com.cognishield.sensor.GazeGatingAnalyser
import kotlinx.coroutines.flow.StateFlow

/**
 * SensorFusionEngine — merges raw biometric signals into a single [CogniState].
 *
 * Inputs:
 *   - [BioFrame]  — EDA, HRV, PPG from Galaxy Watch BioActive sensor
 *   - IMU cadence — erratic motion score from [ImuCadenceAnalyser]
 *   - Gaze score  — pupil dilation / saccade score from [GazeGatingAnalyser]
 *
 * Fusion method: weighted average of all normalised signals [0, 1].
 * Weights are defined as tunable constants below — adjust after user studies.
 *
 * This engine produces a rule-based [CogniState] (source = "RULE") that is used:
 *   1. During model warm-up (before the TFLite model is ready).
 *   2. As a sanity-check alongside AI inference in production.
 *
 * The AI inference path (TFLite model) in [CogniClassifier] overrides this
 * once the model is warm. [CogniEngine] decides which output to trust.
 *
 * All methods are pure functions — no side effects, no coroutine scope needed.
 */
object SensorFusionEngine {

    private const val TAG = "SensorFusion"

    // -------------------------------------------------------------------------
    // Tunable fusion weights
    // Weights do not need to sum to 1.0 — the result is normalised anyway.
    // Adjust these after real-hardware calibration sessions.
    // -------------------------------------------------------------------------
    private const val WEIGHT_EDA         = 0.35f   // Strongest single stress indicator
    private const val WEIGHT_HRV         = 0.25f   // High HRV = low stress (inverted in BioFrame)
    private const val WEIGHT_PPG         = 0.10f   // Blood volume pulse — supplementary
    private const val WEIGHT_IMU_CADENCE = 0.15f   // Erratic motion → stress
    private const val WEIGHT_GAZE        = 0.15f   // Pupil dilation / saccade → stress

    // Confidence penalty applied when fewer than all 5 sensors are active
    private const val MISSING_SENSOR_CONFIDENCE_PENALTY = 0.1f

    /**
     * Fuses a [BioFrame] (which already includes imuCadence and gazeScore) into a [CogniState].
     *
     * Call this on every 250 ms tick before the TFLite model is warm,
     * or on every frame to produce a secondary rule-based estimate.
     *
     * @param frame          The latest BioFrame from [BioSensorService].
     * @param imuCadence     Current cadence score from [ImuCadenceAnalyser.cadenceFlow].
     * @param gazeScore      Current gaze score from [GazeGatingAnalyser.gazeScoreFlow].
     * @param activeSensors  How many of the 5 sensor channels are live.
     *                       Used to compute confidence — full confidence requires all 5.
     * @return               A rule-based [CogniState].
     */
    fun fuse(
        frame: BioFrame,
        imuCadence: Float,
        gazeScore: Float,
        activeSensors: Int = 5
    ): CogniState {
        // Weighted sum (BioFrame.toFeatureArray() already inverts HRV)
        val features = floatArrayOf(
            frame.eda,
            1f - frame.hrv,     // HRV inverted: high HRV = low stress → small contribution
            frame.ppg,
            imuCadence,
            gazeScore
        )
        val weights = floatArrayOf(
            WEIGHT_EDA,
            WEIGHT_HRV,
            WEIGHT_PPG,
            WEIGHT_IMU_CADENCE,
            WEIGHT_GAZE
        )

        val weightedSum = features.zip(weights.toList()).sumOf { (f, w) -> (f * w).toDouble() }
        val totalWeight = weights.sum().toDouble()
        val rawScore = (weightedSum / totalWeight).toFloat().coerceIn(0f, 1f)

        // Confidence degrades when sensors are missing
        val missingCount = (5 - activeSensors).coerceIn(0, 5)
        val confidence = (1f - missingCount * MISSING_SENSOR_CONFIDENCE_PENALTY).coerceIn(0f, 1f)

        val label = CogniState.labelFromScore(rawScore)

        Log.d(TAG, "Fusion → score=${"%.3f".format(rawScore)} label=$label conf=${"%.2f".format(confidence)}")

        return CogniState(
            stressScore = rawScore,
            stateLabel = label,
            confidence = confidence,
            timestamp = frame.timestamp,
            source = "RULE"
        )
    }

    /**
     * Merges a rule-based [CogniState] with an AI-inferred [CogniState].
     *
     * Strategy: trust AI when its confidence is high; blend with rule-based
     * output when confidence is moderate; fall back to rule-based when low.
     *
     * @param ruleBased   Output from [fuse].
     * @param aiInferred  Output from [CogniClassifier.classify].
     * @return            The final blended [CogniState] published to [CogniEngine].
     */
    fun blend(ruleBased: CogniState, aiInferred: CogniState): CogniState {
        return when {
            aiInferred.confidence >= 0.80f -> {
                // High AI confidence — trust the model completely
                aiInferred
            }
            aiInferred.confidence >= 0.50f -> {
                // Moderate confidence — blend 70% AI, 30% rule
                val blendedScore = 0.7f * aiInferred.stressScore + 0.3f * ruleBased.stressScore
                aiInferred.copy(
                    stressScore = blendedScore,
                    stateLabel = CogniState.labelFromScore(blendedScore),
                    confidence = aiInferred.confidence * 0.9f   // slight confidence discount
                )
            }
            else -> {
                // Low AI confidence — fall back to rule-based
                Log.w(TAG, "AI confidence too low (${aiInferred.confidence}) — using rule-based output")
                ruleBased
            }
        }
    }
}
