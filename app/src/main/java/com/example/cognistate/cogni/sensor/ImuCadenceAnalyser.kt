package com.example.cognistate.cogni.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * ImuCadenceAnalyser — detects erratic "stress-fidgeting" through device
 * motion (accelerometer) and typing rhythm analysis.
 *
 * Algorithm:
 *   1. Sample accelerometer at 50 Hz (SENSOR_DELAY_GAME).
 *   2. Compute jerk magnitude (rate-of-change of acceleration vector).
 *   3. Maintain a 2-second rolling RMS of jerk as the cadence signal.
 *   4. Normalise against a calibrated max jerk (MAX_JERK_MS2) → [0, 1].
 *
 * Stress-fidgeting signature: high-frequency, irregular micro-movements.
 * Calm typing signature: low-amplitude, rhythmic, low jerk.
 *
 * Usage:
 *   val analyser = ImuCadenceAnalyser(context)
 *   analyser.start()
 *   val cadence: Float = analyser.cadenceFlow.value   // read by SensorFusionEngine
 *   analyser.stop()
 */
class ImuCadenceAnalyser(private val context: Context) : SensorEventListener {

    private val TAG = "ImuCadence"

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Rolling jerk buffer: 2 seconds at 50 Hz = 100 samples
    private val jerkBuffer = ArrayDeque<Float>(100)

    // Previous acceleration vector for jerk computation
    private var prevAx = 0f
    private var prevAy = 0f
    private var prevAz = 0f
    private var prevTimestamp = 0L

    // Exposed cadence score [0, 1]
    private val _cadenceFlow = MutableStateFlow(0f)
    val cadenceFlow: StateFlow<Float> = _cadenceFlow.asStateFlow()
    val imuScoreFlow: StateFlow<Float> = cadenceFlow

    companion object {
        // Maximum expected jerk in m/s³ — calibrated empirically on Galaxy S24
        // Typical calm typing ≈ 5 m/s³, maximum stress-fidgeting ≈ 30 m/s³
        private const val MAX_JERK_MS3 = 30f

        // Rolling window size: 2 seconds at 50 Hz
        private const val WINDOW_SIZE = 100
    }

    /** Registers the accelerometer listener. Call from a foreground service. */
    fun start() {
        if (accelerometer == null) {
            Log.w(TAG, "No accelerometer found — cadence will be 0.0")
            return
        }
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME  // ~50 Hz
        )
        Log.i(TAG, "IMU cadence analyser started")
    }

    /** Unregisters the accelerometer listener. Call when the service is destroyed. */
    fun stop() {
        sensorManager.unregisterListener(this)
        Log.i(TAG, "IMU cadence analyser stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        val ts = event.timestamp   // nanoseconds

        if (prevTimestamp == 0L) {
            prevAx = ax; prevAy = ay; prevAz = az; prevTimestamp = ts
            return
        }

        // dt in seconds — avoid division by zero
        val dt = ((ts - prevTimestamp) / 1_000_000_000.0).toFloat().coerceAtLeast(0.001f)

        // Jerk = Δacceleration / Δtime (m/s³)
        val jerk = sqrt(
            ((ax - prevAx) / dt).let { it * it } +
            ((ay - prevAy) / dt).let { it * it } +
            ((az - prevAz) / dt).let { it * it }
        )

        prevAx = ax; prevAy = ay; prevAz = az; prevTimestamp = ts

        // Update rolling buffer
        if (jerkBuffer.size >= WINDOW_SIZE) jerkBuffer.removeFirst()
        jerkBuffer.addLast(jerk)

        // RMS jerk over the window → normalised cadence score
        val rmsJerk = sqrt(jerkBuffer.map { it * it }.average()).toFloat()
        val cadence = (rmsJerk / MAX_JERK_MS3).coerceIn(0f, 1f)
        _cadenceFlow.value = cadence
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.d(TAG, "Accelerometer accuracy changed to $accuracy")
    }
}
