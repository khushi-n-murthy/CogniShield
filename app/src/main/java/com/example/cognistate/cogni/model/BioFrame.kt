package com.example.cognistate.cogni.model

/**
 * BioFrame — a single raw sensor snapshot captured every 250 ms.
 *
 * Produced by: BioSensorService (Samsung Health Sensor SDK listener)
 * Consumed by: SensorFusionEngine, CogniClassifier (sliding window)
 *
 * All raw values are normalised at the point of capture so downstream
 * consumers never need to know the SDK's native units.
 *
 * @param eda       Electrodermal activity (skin conductance), normalised [0.0, 1.0].
 *                  Raw unit from BioActive sensor: µS (microsiemens).
 *                  Normalisation: value / EDA_MAX_US where EDA_MAX_US = 20.0f.
 * @param hrv       Heart-rate variability (SDNN), normalised [0.0, 1.0].
 *                  Raw unit: milliseconds. High HRV = low stress.
 *                  Normalisation: value / HRV_MAX_MS where HRV_MAX_MS = 100.0f.
 *                  NOTE: inverted before fusion — higher HRV lowers stress score.
 * @param ppg       Photoplethysmography amplitude (relative blood volume pulse), [0.0, 1.0].
 *                  Raw unit: arbitrary ADC counts. Normalised by rolling max over 60 s.
 * @param imuCadence  IMU-derived typing/movement cadence, normalised [0.0, 1.0].
 *                  0 = completely still, 1 = maximum erratic movement.
 *                  Produced by ImuCadenceAnalyser, merged here for convenience.
 * @param gazeScore Pupil-dilation–derived gaze stress score from MediaPipe, [0.0, 1.0].
 *                  0 = calm gaze, 1 = maximum dilation / saccadic movement.
 *                  Produced by GazeGatingAnalyser, merged here.
 * @param timestamp Unix epoch milliseconds when this frame was captured.
 */
data class BioFrame(
    val eda: Float,
    val hrv: Float,
    val ppg: Float,
    val imuCadence: Float = 0f,
    val gazeScore: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        // Normalisation constants — adjust after calibration with real hardware
        const val EDA_MAX_US = 20.0f
        const val HRV_MAX_MS = 100.0f

        /** Returns a zeroed BioFrame used during warm-up before real data arrives. */
        val EMPTY = BioFrame(eda = 0f, hrv = 0f, ppg = 0f)
    }

    /**
     * Converts this BioFrame into a FloatArray of exactly 5 features
     * in the order expected by the TFLite model input tensor:
     * [eda, 1-hrv (inverted), ppg, imuCadence, gazeScore]
     *
     * HRV is inverted because high HRV indicates low stress, but the model
     * is trained on a "higher = more stressed" convention for all features.
     */
    fun toFeatureArray(): FloatArray = floatArrayOf(
        eda,
        1f - hrv,   // invert HRV so higher value = more stressed
        ppg,
        imuCadence,
        gazeScore
    )
}
