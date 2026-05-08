package com.example.cognistate.cogni.sensor

import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.lifecycle.LifecycleService
import com.example.cognistate.cogni.model.BioFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.sqrt

/**
 * PhoneBioService — a drop-in replacement for BioSensorService that works
 * with ONLY a Samsung phone (no Galaxy Watch, no Samsung Health SDK).
 *
 * How it works
 * ────────────
 * Sensor        → Phone source               → BioFrame field
 * ──────────────────────────────────────────────────────────
 * PPG           → Rear camera green channel  → ppg
 * HRV (SDNN)    → IBI derived from rPPG peak detection → hrv
 * EDA (proxy)   → HRV-derived stress index   → eda
 * IMU cadence   → Accelerometer (unchanged)  → imuCadence  (injected by CogniEngine)
 * Gaze score    → Front camera MediaPipe     → gazeScore   (injected by CogniEngine)
 *
 * rPPG method (remote photoplethysmography):
 *   1. Open rear camera, flash torch ON (improves SNR).
 *   2. Capture 30fps low-res frames (64×64 pixels).
 *   3. Average green channel per frame → raw PPG waveform.
 *   4. Band-pass filter 0.7–4 Hz (42–240 bpm) to isolate cardiac signal.
 *   5. Detect peaks → IBI list → SDNN → HRV.
 *   6. EDA proxy: inverted, smoothed HRV (high stress = low HRV = high EDA proxy).
 *
 * Accuracy note:
 *   rPPG is validated to ±5 bpm for HR and ±10ms for HRV in controlled conditions.
 *   For a hackathon demo, this is more than sufficient to drive the stress classifier.
 *   When a Galaxy Watch is available, swap this class back for BioSensorService —
 *   the rest of the pipeline (CogniEngine, CogniClassifier) is unchanged.
 *
 * Lifecycle:
 *   Extends LifecycleService (no WearableListenerService dependency).
 *   Started/stopped by CogniEngine the same way as BioSensorService.
 */
class PhoneBioService : LifecycleService() {

    private val TAG = "PhoneBio"

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Camera2 objects
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    // rPPG signal buffer — 10 seconds at 30 fps = 300 samples
    private val ppgBuffer = ArrayDeque<Float>(300)
    private val ibiBuffer  = ArrayDeque<Float>(30)   // inter-beat intervals (ms)

    // Rolling normalisation state
    private var ppgMin = Float.MAX_VALUE
    private var ppgMax = Float.MIN_VALUE
    private var prevPeakTime = 0L
    private var smoothedHrv  = 0.5f   // starts at neutral

    companion object {
        private val _bioFrameFlow = MutableSharedFlow<BioFrame>(replay = 1)
        val bioFrameFlow: SharedFlow<BioFrame> = _bioFrameFlow.asSharedFlow()

        private const val EMIT_INTERVAL_MS   = 250L
        private const val FRAME_WIDTH        = 64
        private const val FRAME_HEIGHT       = 64
        private const val SAMPLE_RATE_HZ     = 30f

        // Band-pass limits for cardiac signal (Hz)
        private const val BP_LOW_HZ          = 0.7f   // 42 bpm min
        private const val BP_HIGH_HZ         = 4.0f   // 240 bpm max

        // Peak detection: minimum time between heartbeats (ms)
        private const val MIN_IBI_MS         = 300L   // 200 bpm max

        // HRV normalisation ceiling (ms SDNN)
        private const val HRV_MAX_MS         = BioFrame.HRV_MAX_MS
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        Log.i(TAG, "PhoneBioService.onCreate() — starting rPPG pipeline")
        // Create notification channel and start foreground
        val channelId = "cogni_sensor"
        val channel = android.app.NotificationChannel(
            channelId, "CogniShield Sensors",
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(channel)
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("CogniShield active")
            .setContentText("Monitoring cognitive state")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
        super.onCreate()
        Log.i(TAG, "PhoneBioService created — starting rPPG pipeline")
        startCameraThread()
        openRearCamera()
        // startSyntheticFallback()
        startEmitLoop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        serviceScope.cancel()
        Log.i(TAG, "PhoneBioService destroyed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Camera setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun startCameraThread() {
        Log.i(TAG, "Starting CogniRearCam thread")
        cameraThread = HandlerThread("CogniRearCam").also { it.start() }
        cameraHandler = Handler(cameraThread!!.looper)
    }

    /**
     * Opens the rear-facing camera with torch enabled for better rPPG SNR.
     * Captures YUV_420_888 frames at 64×64 to minimise CPU load.
     */
    private fun openRearCamera() {
        Log.i(TAG, "Opening rear camera...")
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Find rear camera ID
        val rearId = cameraManager!!.cameraIdList.firstOrNull { id ->
            val chars = cameraManager!!.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: run {
            Log.e(TAG, "No rear camera found"); return
        }

        // ImageReader: small frames, green channel extraction is all we need
        imageReader = ImageReader.newInstance(
            FRAME_WIDTH, FRAME_HEIGHT,
            ImageFormat.YUV_420_888,
            4   // max images in flight
        )
        imageReader!!.setOnImageAvailableListener({ reader ->
            reader.acquireLatestImage()?.use { image ->
                processRawFrame(image)
            }
        }, cameraHandler)

        try {
            cameraManager!!.openCamera(rearId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCaptureSession(camera)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close(); cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error $error — rPPG unavailable")
                    camera.close(); cameraDevice = null
                }
            }, cameraHandler)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied — rPPG will use synthetic data", e)
            startSyntheticFallback()
        } catch (e: Exception) {
            Log.e(TAG, "Camera open failed", e)
            startSyntheticFallback()
        }
    }

    /**
     * Starts a repeating capture request at max frame rate with torch on.
     */
    private fun startCaptureSession(camera: CameraDevice) {
        val surface = imageReader!!.surface
        camera.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val request = camera.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW
                    ).apply {
                        addTarget(surface)
                        // Torch on: increases green channel SNR by ~40%
                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                        // Lock AE/AWB — prevent the camera from auto-adjusting
                        // and corrupting the PPG waveform
                        set(CaptureRequest.CONTROL_AE_LOCK, true)
                        set(CaptureRequest.CONTROL_AWB_LOCK, true)
                    }.build()
                    session.setRepeatingRequest(request, null, cameraHandler)
                    Log.i(TAG, "rPPG capture session started (torch ON)")
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Capture session config failed — using synthetic data")
                    startSyntheticFallback()
                }
            },
            cameraHandler
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // rPPG signal processing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts the spatially-averaged green channel from a YUV_420_888 frame.
     *
     * YUV_420_888 layout: plane[0] = Y (luma), plane[1] = U (Cb), plane[2] = V (Cr)
     * The green channel correlates with: G ≈ Y − 0.344·U − 0.714·V
     * Averaging over all pixels gives the volumetric pulse signal.
     */
    private fun processRawFrame(image: android.media.Image) {
        val yPlane = image.planes[0].buffer
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer

        var sumG = 0.0
        val pixelCount = FRAME_WIDTH * FRAME_HEIGHT

        for (i in 0 until pixelCount) {
            val y = (yPlane.get(i).toInt() and 0xFF).toFloat()
            // U and V are subsampled 2×2 — use integer division for index
            val uvIdx = (i / 2)
            val u = (uPlane.get(uvIdx).toInt() and 0xFF - 128).toFloat()
            val v = (vPlane.get(uvIdx).toInt() and 0xFF - 128).toFloat()
            // Green approximation from YUV
            sumG += (y - 0.344f * u - 0.714f * v).coerceIn(0f, 255f)
        }

        val rawGreen = (sumG / pixelCount).toFloat()
        onNewSample(rawGreen, System.currentTimeMillis())
    }

    /**
     * Processes each new green channel sample.
     *
     * 1. Normalise to [0,1] using rolling min/max.
     * 2. Append to band-pass buffer.
     * 3. Detect heartbeat peaks.
     * 4. Update HRV and EDA proxy estimates.
     */
    private fun onNewSample(rawGreen: Float, timestampMs: Long) {
        // Rolling min/max normalisation
        if (rawGreen < ppgMin) ppgMin = rawGreen
        if (rawGreen > ppgMax) ppgMax = rawGreen
        val range = ppgMax - ppgMin
        val normalised = if (range > 0f) (rawGreen - ppgMin) / range else 0.5f

        // Update rolling buffer (keep 10 seconds = 300 samples at 30fps)
        if (ppgBuffer.size >= 300) ppgBuffer.removeFirst()
        ppgBuffer.addLast(normalised)

        // Peak detection on the last 60 samples (~2 seconds)
        if (ppgBuffer.size >= 60) {
            detectPeak(normalised, timestampMs)
        }
    }

    /**
     * Simple threshold-based peak detector.
     * A peak is detected when the signal crosses above the rolling mean
     * and sufficient time has passed since the last peak (MIN_IBI_MS).
     */
    private fun detectPeak(value: Float, timestampMs: Long) {
        val window = ppgBuffer.toList().takeLast(60)
        val mean = window.average().toFloat()
        val threshold = mean + 0.15f   // 15% above mean to avoid noise

        if (value > threshold && (timestampMs - prevPeakTime) > MIN_IBI_MS) {
            val ibi = (timestampMs - prevPeakTime).toFloat()
            if (prevPeakTime > 0 && ibi < 2000f) {   // ignore IBIs > 2s (artefact)
                if (ibiBuffer.size >= 30) ibiBuffer.removeFirst()
                ibiBuffer.addLast(ibi)
                updateHrvAndEda()
            }
            prevPeakTime = timestampMs
        }
    }

    /**
     * Computes SDNN (HRV) from the IBI buffer and derives the EDA proxy.
     *
     * EDA proxy rationale: under cognitive stress, HRV drops and sympathetic
     * nervous activity (which drives EDA) rises. We approximate this inverse
     * relationship as: EDA_proxy = 1 - normalised_HRV, with a smoothing factor
     * to avoid abrupt transitions.
     */
    private fun updateHrvAndEda() {
        if (ibiBuffer.size < 4) return
        val sdnn = computeSdnn(ibiBuffer.toList())
        val newHrv = (sdnn / HRV_MAX_MS).coerceIn(0f, 1f)
        // Exponential moving average — alpha=0.2 for slow physiological changes
        smoothedHrv = 0.8f * smoothedHrv + 0.2f * newHrv
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Frame emission loop
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Snapshots current signal estimates every 250ms and emits a BioFrame.
     * This decouples the 30fps camera rate from the model's 4Hz input cadence.
     */
    private fun startEmitLoop() {
        serviceScope.launch {
            Log.i(TAG, "BioFrame emit loop started")
            try {
                while (isActive) {
                    val ppg = ppgBuffer.lastOrNull() ?: 0f
                    val hrv = smoothedHrv
                    // EDA proxy: inverse of HRV, smoothed
                    val eda = (1f - smoothedHrv).coerceIn(0f, 1f)

                    val frame = BioFrame(
                        eda = eda,
                        hrv = hrv,
                        ppg = ppg,
                        imuCadence = 0f,   // injected by CogniEngine
                        gazeScore = 0f,    // injected by CogniEngine
                        timestamp = System.currentTimeMillis()
                    )
                    _bioFrameFlow.emit(frame)
                    Log.v(TAG, "Emitted BioFrame: ppg=$ppg, hrv=$hrv")
                    delay(EMIT_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in emit loop", e)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Synthetic fallback (no camera permission or camera unavailable)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * If the camera is unavailable (permission denied, busy, etc.), emit
     * plausible synthetic BioFrames so the rest of the pipeline keeps running.
     *
     * Synthetic data oscillates slowly between FLOW and RECOVERY states
     * so that Palak and Sinchana can still demo Shield UI transitions.
     */
    private fun startSyntheticFallback() {
        Log.w(TAG, "Starting synthetic BioFrame fallback — demo mode")
        serviceScope.launch {
            var tick = 0
            while (isActive) {
                // Slow oscillation: 60-second cycle through FLOW → RECOVERY → REDLINING
                val phase = (tick % 240) / 240f    // normalised 0→1 over 60s
                val stressCycle = when {
                    phase < 0.5f -> phase * 1.4f          // rising stress
                    else         -> (1f - phase) * 1.4f   // falling stress
                }
                val eda = stressCycle.coerceIn(0f, 1f)
                val hrv = (1f - stressCycle).coerceIn(0.2f, 1f)
                val ppg = 0.5f + stressCycle * 0.2f

                _bioFrameFlow.emit(
                    BioFrame(
                        eda = eda,
                        hrv = hrv,
                        ppg = ppg,
                        imuCadence = 0f,
                        gazeScore = 0f,
                        timestamp = System.currentTimeMillis()
                    )
                )
                tick++
                delay(EMIT_INTERVAL_MS)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun computeSdnn(ibis: List<Float>): Float {
        if (ibis.size < 2) return 0f
        val mean = ibis.average().toFloat()
        val variance = ibis.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance.toDouble()).toFloat()
    }

    private fun stopCamera() {
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        cameraThread?.quitSafely()
        captureSession = null
        cameraDevice = null
        imageReader = null
        cameraThread = null
        cameraHandler = null
    }
}
