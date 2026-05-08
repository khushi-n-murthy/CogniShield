package com.example.cognistate.cogni.engine

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.cognistate.cogni.ai.CogniClassifier
import com.example.cognistate.cogni.fusion.SensorFusionEngine
import com.example.cognistate.cogni.model.BioFrame
import com.example.cognistate.cogni.model.CogniState
import com.example.cognistate.cogni.sensor.PhoneBioService
import com.example.cognistate.cogni.sensor.GazeGatingAnalyser
import com.example.cognistate.cogni.sensor.ImuCadenceAnalyser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * CogniEngine — the singleton orchestrator for the entire CogniShield AI pipeline.
 *
 * This object is the ONLY entry point for all other modules.
 * Palak (Shield UI) and Sinchana (Full-Stack) must observe [CogniStateFlow] here
 * and nowhere else. They must NOT hold references to any sensor or classifier class.
 *
 * Architecture:
 *
 *   BioSensorService  ──┐
 *   ImuCadenceAnalyser ─┼──► SensorFusionEngine ──► rule-based CogniState ─┐
 *   GazeGatingAnalyser ─┘                                                   │
 *                                                                            ▼
 *                        CogniClassifier (TFLite/NPU) ──► AI CogniState ──► blend
 *                                                                            │
 *                                                                            ▼
 *                                                                   CogniStateFlow  ◄── consumers
 *
 * Lifecycle:
 *   Call [start] once from Application.onCreate() or a foreground Service.
 *   Call [stop] when the app is backgrounded / destroyed.
 *
 * Thread safety:
 *   All internal state is confined to [engineScope] (Dispatchers.Default).
 *   [CogniStateFlow] is a StateFlow — safe to collect on any thread.
 */
object CogniEngine {

    private const val TAG = "CogniEngine"

    // Coroutine scope for the entire engine — cancelled by [stop]
    private var engineScope: CoroutineScope? = null

    // Module instances — created fresh on each [start] call
    private var classifier: CogniClassifier? = null
    var imuAnalyzer: ImuCadenceAnalyser? = null
        private set
    private var gazeAnalyser: GazeGatingAnalyser? = null

    // Sliding window: last 20 BioFrames fed into the TFLite model
    private val bioWindow = ArrayDeque<BioFrame>(20)
    private const val WINDOW_SIZE = 20

    // Whether the TFLite model has finished loading and warmup
    @Volatile private var modelReady = false

    // -------------------------------------------------------------------------
    // Public API — CogniStateFlow (THE only source of truth)
    // -------------------------------------------------------------------------

    /**
     * The single source of truth for cognitive state across the entire app.
     *
     * Consumers (Palak, Sinchana) collect this StateFlow:
     *
     *   lifecycleScope.launch {
     *       CogniEngine.CogniStateFlow.collect { state ->
     *           when (state.stateLabel) {
     *               CogniState.StateLabel.REDLINING -> activateShield()
     *               CogniState.StateLabel.RECOVERY  -> softShield()
     *               CogniState.StateLabel.FLOW      -> deactivateShield()
     *           }
     *       }
     *   }
     *
     * Emits [CogniState.INITIALISING] immediately on subscription before
     * the first real inference completes (~1–2 seconds after [start]).
     */
    private val _cogniStateFlow = MutableStateFlow(CogniState.INITIALISING)
    val CogniStateFlow: StateFlow<CogniState> = _cogniStateFlow.asStateFlow()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the full sensor → fusion → inference pipeline.
     *
     * Steps (all on background coroutines):
     *   1. Start IMU and Gaze analysers.
     *   2. Load and warm up the TFLite model.
     *   3. Collect BioFrames from BioSensorService.
     *   4. On every frame: run rule-based fusion; once model is ready, also run
     *      TFLite inference; blend and publish to [CogniStateFlow].
     *
     * @param context  Application context (not Activity — this runs in the background).
     */
    fun start(context: Context) {
        if (engineScope?.isActive == true) {
            Log.w(TAG, "CogniEngine.start() called but engine is already running — ignoring")
            return
        }

        Log.i(TAG, "CogniEngine starting...")
        engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scope = engineScope!!

        // Step 1 — Start side-channel analysers
        imuAnalyzer = ImuCadenceAnalyser(context.applicationContext).also { it.start() }
        gazeAnalyser = GazeGatingAnalyser(context.applicationContext).also { it.init() }

        // Step 2 — Load TFLite model on IO dispatcher (file I/O)
        classifier = CogniClassifier(context.applicationContext)
        scope.launch(Dispatchers.IO) {
            try {
                classifier?.load()
                classifier?.warmup()
                modelReady = true
                Log.i(TAG, "TFLite model ready — switching to AI inference")
            } catch (e: Exception) {
                Log.e(TAG, "Model load failed — will run on rule-based fusion only", e)
                // modelReady stays false; rule-based path continues indefinitely
            }
        }

        // Step 2.5 — Start PhoneBioService with a small delay to ensure context is ready
        scope.launch {
            delay(1000)
            val intent = Intent(context, PhoneBioService::class.java)
            context.startForegroundService(intent)
            Log.i(TAG, "Requested PhoneBioService start")
        }

        // Step 3 — Main inference loop: collect BioFrames and publish CogniState
        scope.launch {
            Log.i(TAG, "Starting BioFrame collection loop...")
            PhoneBioService.bioFrameFlow.collect { frame ->
                Log.d(TAG, "Received BioFrame: eda=${frame.eda}, hrv=${frame.hrv}")
                processBioFrame(frame)
            }
        }

        Log.i(TAG, "CogniEngine started — collecting from BioSensorService")
    }

    /**
     * Stops the engine and releases all resources.
     * After calling stop(), [CogniStateFlow] will retain its last emitted value
     * but will not update further.
     */
    fun stop() {
        Log.i(TAG, "CogniEngine stopping...")
        engineScope?.cancel()
        engineScope = null

        classifier?.close()
        classifier = null

        imuAnalyzer?.stop()
        imuAnalyzer = null

        gazeAnalyser?.close()
        gazeAnalyser = null

        bioWindow.clear()
        modelReady = false
        Log.i(TAG, "CogniEngine stopped")
    }

    // -------------------------------------------------------------------------
    // Frame processing (called on every 250 ms BioFrame)
    // -------------------------------------------------------------------------

    /**
     * Core processing function — called on every incoming BioFrame.
     *
     * 1. Enriches the frame with current IMU cadence and gaze score.
     * 2. Maintains the 20-frame sliding window for the model.
     * 3. Runs rule-based fusion (always, instant).
     * 4. If model is ready, runs TFLite inference and blends results.
     * 5. Publishes the final CogniState to [CogniStateFlow].
     */
    private suspend fun processBioFrame(rawFrame: BioFrame) {
        // Enrich frame with side-channel signals
        val enrichedFrame = rawFrame.copy(
            imuCadence = imuAnalyzer?.cadenceFlow?.value ?: 0f,
            gazeScore = gazeAnalyser?.gazeScoreFlow?.value ?: 0f
        )

        // Update sliding window (drop oldest if at capacity)
        if (bioWindow.size >= WINDOW_SIZE) bioWindow.removeFirst()
        bioWindow.addLast(enrichedFrame)

        // Count how many sensor channels have non-zero data
        val activeSensors = listOf(
            enrichedFrame.eda,
            enrichedFrame.hrv,
            enrichedFrame.ppg,
            enrichedFrame.imuCadence,
            enrichedFrame.gazeScore
        ).count { it > 0f }

        // Rule-based fusion — always fast, used as baseline and fallback
        val ruleState = SensorFusionEngine.fuse(
            frame = enrichedFrame,
            imuCadence = enrichedFrame.imuCadence,
            gazeScore = enrichedFrame.gazeScore,
            activeSensors = activeSensors
        )

        // AI inference — only when model is ready and window is full
        val finalState = if (modelReady && bioWindow.size == WINDOW_SIZE) {
            try {
                val aiState = classifier!!.classify(bioWindow.toList())
                SensorFusionEngine.blend(ruleState, aiState)
            } catch (e: Exception) {
                Log.e(TAG, "Inference failed — publishing rule-based state", e)
                ruleState
            }
        } else {
            // Model not ready yet — publish rule-based output
            ruleState
        }

        // Publish to the StateFlow
        _cogniStateFlow.value = finalState

        Log.v(TAG, "Published: label=${finalState.stateLabel} " +
            "score=${"%.3f".format(finalState.stressScore)} " +
            "conf=${"%.2f".format(finalState.confidence)} " +
            "src=${finalState.source}")
    }

    // -------------------------------------------------------------------------
    // Gaze frame injection (called by the camera analyser in the UI layer)
    // -------------------------------------------------------------------------

    /**
     * Forwards a camera frame to [GazeGatingAnalyser] for pupil tracking.
     *
     * Call this from your CameraX ImageAnalysis.Analyzer implementation:
     *
     *   override fun analyze(imageProxy: ImageProxy) {
     *       val bitmap = imageProxy.toBitmap()
     *       CogniEngine.submitGazeFrame(bitmap, imageProxy.imageInfo.timestamp / 1_000_000)
     *       imageProxy.close()
     *   }
     *
     * @param bitmap       Camera frame as Bitmap (ARGB_8888).
     * @param timestampMs  Frame capture time in milliseconds.
     */
    fun submitGazeFrame(bitmap: android.graphics.Bitmap, timestampMs: Long) {
        gazeAnalyser?.analyseFrame(bitmap, timestampMs)
    }
}
