package com.example.cognistate.cogni.ai

import android.content.Context
import android.util.Log
import com.cognishield.model.BioFrame
import com.cognishield.model.CogniState
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * CogniClassifier — loads the TFLite neural friction model and runs inference.
 *
 * Model spec:
 *   Input:  Float32[1, 20, 5]  — sliding window of last 20 BioFrames × 5 features
 *   Output: Float32[1, 3]      — softmax probabilities [FLOW, RECOVERY, REDLINING]
 *
 * Delegate priority:
 *   1. Samsung NPU via NNAPI delegate  (target: <15 ms per window)
 *   2. GPU delegate                    (fallback)
 *   3. CPU (4 threads)                 (final fallback — always works)
 *
 * Usage (called only from CogniEngine — do not instantiate elsewhere):
 *   val classifier = CogniClassifier(context)
 *   classifier.load()           // call once on a background coroutine
 *   classifier.warmup()         // run 3 dummy inferences to prime the NPU
 *   val state = classifier.classify(window)
 *   classifier.close()
 */
class CogniClassifier(private val context: Context) {

    private val TAG = "CogniAI"

    // TFLite interpreter and its delegate (nullable — set during load())
    private var interpreter: Interpreter? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var gpuDelegate: GpuDelegate? = null

    // Tracks which hardware path is active — for CogniState.source field
    private var activeSource: String = "CPU"

    // Input tensor: batch=1, window=20 frames, features=5
    private val INPUT_WINDOW = 20
    private val INPUT_FEATURES = 5    // [eda, 1-hrv, ppg, imuCadence, gazeScore]
    private val NUM_CLASSES = 3       // FLOW=0, RECOVERY=1, REDLINING=2

    // Reusable byte buffers — allocated once to avoid GC pressure during inference
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(1 * INPUT_WINDOW * INPUT_FEATURES * 4)
            .apply { order(ByteOrder.nativeOrder()) }

    private val outputBuffer: Array<FloatArray> = Array(1) { FloatArray(NUM_CLASSES) }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Loads the TFLite model from assets and initialises the interpreter.
     * Tries NPU → GPU → CPU in order.
     *
     * Must be called on a background coroutine before [classify].
     * Throws [IllegalStateException] if the model file is missing from assets.
     */
    fun load() {
        val modelBuffer = loadModelFile("cogni_classifier.tflite")
        interpreter = buildInterpreterWithBestDelegate(modelBuffer)
        Log.i(TAG, "Model loaded — running on $activeSource")
    }

    /**
     * Runs 3 dummy inference passes to warm up the NPU/GPU JIT compiler.
     * Without warmup, the first real inference can take 5–10× longer than steady state.
     *
     * Call this immediately after [load], before the first real [classify] call.
     */
    fun warmup() {
        Log.i(TAG, "Warming up model on $activeSource...")
        val dummyWindow = List(INPUT_WINDOW) { BioFrame.EMPTY }
        repeat(3) { i ->
            val start = System.currentTimeMillis()
            runInference(dummyWindow)
            Log.d(TAG, "Warmup pass ${i + 1}: ${System.currentTimeMillis() - start} ms")
        }
        Log.i(TAG, "Warmup complete")
    }

    // -------------------------------------------------------------------------
    // Inference
    // -------------------------------------------------------------------------

    /**
     * Classifies the cognitive state from a sliding window of BioFrames.
     *
     * @param window  Exactly [INPUT_WINDOW] (20) BioFrames in chronological order.
     *                If fewer than 20 frames are available, pad with [BioFrame.EMPTY]
     *                at the front (CogniEngine handles this).
     * @return        A [CogniState] with source = activeSource.
     *
     * Logs inference latency to Logcat with tag [CogniAI] on every call.
     */
    suspend fun classify(window: List<BioFrame>): CogniState {
        require(interpreter != null) { "CogniClassifier.load() must be called before classify()" }

        val start = System.currentTimeMillis()
        val probs = runInference(window)
        val latencyMs = System.currentTimeMillis() - start

        Log.d(TAG, "[CogniAI] inference=${latencyMs}ms " +
            "FLOW=${"%.3f".format(probs[0])} " +
            "RECOVERY=${"%.3f".format(probs[1])} " +
            "REDLINING=${"%.3f".format(probs[2])}")

        // Class with highest probability wins
        val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
        val confidence = probs[maxIdx]

        // Weighted stress score: FLOW→0, RECOVERY→0.55, REDLINING→1.0
        // Smooth blending across classes is more useful than a hard 0/1 flag
        val stressScore = (probs[0] * 0f + probs[1] * 0.55f + probs[2] * 1.0f)
            .coerceIn(0f, 1f)

        val label = when (maxIdx) {
            0    -> CogniState.StateLabel.FLOW
            1    -> CogniState.StateLabel.RECOVERY
            else -> CogniState.StateLabel.REDLINING
        }

        return CogniState(
            stressScore = stressScore,
            stateLabel = label,
            confidence = confidence,
            timestamp = window.last().timestamp,
            source = activeSource
        )
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Fills the input ByteBuffer from the window and runs interpreter.run().
     * Returns raw softmax probabilities as FloatArray[3].
     */
    private fun runInference(window: List<BioFrame>): FloatArray {
        // Pad or trim to exactly INPUT_WINDOW frames
        val paddedWindow = when {
            window.size >= INPUT_WINDOW -> window.takeLast(INPUT_WINDOW)
            else -> List(INPUT_WINDOW - window.size) { BioFrame.EMPTY } + window
        }

        // Fill input tensor: shape [1, 20, 5]
        inputBuffer.rewind()
        for (frame in paddedWindow) {
            for (feature in frame.toFeatureArray()) {
                inputBuffer.putFloat(feature)
            }
        }

        // Clear previous output
        outputBuffer[0].fill(0f)

        // Run inference
        interpreter?.run(inputBuffer, outputBuffer)

        return outputBuffer[0].copyOf()
    }

    /**
     * Attempts to build an Interpreter with the best available delegate.
     * Falls through NPU → GPU → CPU without crashing.
     */
    private fun buildInterpreterWithBestDelegate(modelBuffer: MappedByteBuffer): Interpreter {
        // --- Attempt 1: NNAPI delegate (routes to Samsung NPU on Galaxy S-series) ---
        try {
            val nnApi = NnApiDelegate()
            val options = Interpreter.Options()
                .addDelegate(nnApi)
                .setNumThreads(1)   // delegate handles parallelism internally
            val interp = Interpreter(modelBuffer, options)
            nnApiDelegate = nnApi
            activeSource = "NPU"
            Log.i(TAG, "NNAPI delegate active — targeting Samsung NPU")
            return interp
        } catch (e: Exception) {
            Log.w(TAG, "NNAPI delegate unavailable: ${e.message} — trying GPU")
        }

        // --- Attempt 2: GPU delegate ---
        try {
            val gpu = GpuDelegate()
            val options = Interpreter.Options()
                .addDelegate(gpu)
                .setNumThreads(1)
            val interp = Interpreter(modelBuffer, options)
            gpuDelegate = gpu
            activeSource = "GPU"
            Log.i(TAG, "GPU delegate active")
            return interp
        } catch (e: Exception) {
            Log.w(TAG, "GPU delegate unavailable: ${e.message} — falling back to CPU")
        }

        // --- Attempt 3: CPU fallback ---
        val options = Interpreter.Options().setNumThreads(4)
        activeSource = "CPU"
        Log.i(TAG, "Running on CPU (4 threads) — inference may exceed 15 ms target")
        return Interpreter(modelBuffer, options)
    }

    /**
     * Memory-maps the TFLite model file from the app's assets folder.
     * Memory-mapping avoids loading the entire file into heap memory.
     *
     * @param filename  Asset filename, e.g. "cogni_classifier.tflite"
     * @throws IllegalStateException if the file is not found in assets.
     */
    private fun loadModelFile(filename: String): MappedByteBuffer {
        val assetFd = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFd.startOffset,
            assetFd.declaredLength
        )
    }

    /**
     * Releases all TFLite resources. Call from CogniEngine.stop().
     * Safe to call multiple times.
     */
    fun close() {
        interpreter?.close()
        nnApiDelegate?.close()
        gpuDelegate?.close()
        interpreter = null
        nnApiDelegate = null
        gpuDelegate = null
        Log.i(TAG, "CogniClassifier closed")
    }
}
