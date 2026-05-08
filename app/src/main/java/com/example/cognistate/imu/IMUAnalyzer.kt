package com.example.cognistate.imu

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class IMUAnalyzer(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _imuScoreFlow = MutableStateFlow(0f)

    val imuScoreFlow: StateFlow<Float> =
        _imuScoreFlow.asStateFlow()

    private val window =
        ArrayDeque<Pair<Long, Float>>()

    private val WINDOW_MS = 2000L

    private var lastAccel = FloatArray(3)

    private var lastTimestampMs = 0L

    private val MAX_JERK = 50f

    fun start() {

        val accel =
            sensorManager.getDefaultSensor(
                Sensor.TYPE_ACCELEROMETER
            )

        sensorManager.registerListener(
            this,
            accel,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    fun stop() {

        sensorManager.unregisterListener(this)

        window.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {

        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER)
            return

        val nowMs =
            event.timestamp / 1_000_000L

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        val mag =
            sqrt(ax * ax + ay * ay + az * az)

        val dtMs = nowMs - lastTimestampMs

        val jerk =
            if (lastTimestampMs > 0 && dtMs > 0) {

                val lastMag =
                    sqrt(
                        lastAccel[0] * lastAccel[0] +
                                lastAccel[1] * lastAccel[1] +
                                lastAccel[2] * lastAccel[2]
                    )

                kotlin.math.abs(mag - lastMag) /
                        (dtMs / 1000f)

            } else {
                0f
            }

        lastAccel =
            floatArrayOf(ax, ay, az)

        lastTimestampMs = nowMs

        window.addLast(
            Pair(nowMs, jerk)
        )

        while (
            window.isNotEmpty() &&
            (nowMs - window.first().first) > WINDOW_MS
        ) {

            window.removeFirst()
        }

        val meanJerk =
            if (window.isEmpty()) {
                0f
            } else {

                window.sumOf {
                    it.second.toDouble()
                }.toFloat() / window.size
            }

        _imuScoreFlow.value =
            (meanJerk / MAX_JERK)
                .coerceIn(0f, 1f)
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) = Unit
}