package com.example.cognistate.cogni.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * CogniState — the single source of truth for cognitive state in CogniShield.
 *
 * This data class is owned by Khushi N (AI Core & Edge Pipeline).
 * It is consumed by:
 *   - Palak Jangid A  → triggers Shield UI changes (grayscale, scroll dampening)
 *   - Sinchana R Achar → drives full-stack integration & frontend updates
 *
 * ⚠️  DO NOT modify field names or types without coordinating with the full team.
 *     This class is frozen after Day 1.
 *
 * @param stressScore   Normalised stress level in [0.0, 1.0].
 *                      0.0 = completely calm, 1.0 = maximum cognitive overload.
 * @param stateLabel    Human-readable label derived from stressScore thresholds.
 *                      "FLOW"      → stressScore < 0.4   (deep focus, all good)
 *                      "REDLINING" → stressScore ≥ 0.7   (cognitive overload, shield activates)
 *                      "RECOVERY"  → 0.4 ≤ stressScore < 0.7 (transition / cool-down)
 * @param confidence    Model confidence for the predicted label in [0.0, 1.0].
 *                      Values below 0.5 should be treated as unreliable by consumers.
 * @param timestamp     Unix epoch milliseconds when this state was produced.
 *                      Use System.currentTimeMillis() — not SystemClock.elapsedRealtime().
 * @param source        Which inference path produced this state.
 *                      "NPU"  = Samsung NPU delegate (fastest, preferred)
 *                      "GPU"  = TfLiteGpu delegate (fallback)
 *                      "CPU"  = pure-CPU fallback (slowest, used when NPU/GPU unavailable)
 *                      "RULE" = rule-based heuristic (used before model is warmed up)
 */
@Parcelize
data class CogniState(
    val stressScore: Float,
    val stateLabel: StateLabel,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "NPU"
) : Parcelable {

    /**
     * Sealed enum for stateLabel — prevents consumers from comparing raw strings.
     * Use this instead of string literals everywhere in the codebase.
     */
    enum class StateLabel {
        FLOW,       // Deep focus — no intervention needed
        RECOVERY,   // Transitioning — mild intervention possible
        REDLINING   // Cognitive overload — full Shield activates
    }

    companion object {
        /**
         * Stress score thresholds used both here and in SensorFusionEngine.
         * Centralised here so all modules stay in sync.
         */
        const val THRESHOLD_FLOW_MAX = 0.4f
        const val THRESHOLD_REDLINE_MIN = 0.7f

        /**
         * Returns the appropriate StateLabel for a given stress score.
         * Used by SensorFusionEngine and CogniClassifier to keep labelling consistent.
         */
        fun labelFromScore(score: Float): StateLabel = when {
            score < THRESHOLD_FLOW_MAX    -> StateLabel.FLOW
            score >= THRESHOLD_REDLINE_MIN -> StateLabel.REDLINING
            else                           -> StateLabel.RECOVERY
        }

        /** Sentinel value emitted before the first real inference completes. */
        val INITIALISING = CogniState(
            stressScore = 0f,
            stateLabel = StateLabel.FLOW,
            confidence = 0f,
            source = "RULE"
        )
    }
}
