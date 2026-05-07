package com.example.facemesh.gaze

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton repository acting as the single source of truth for the gaze score.
 * It bridges the background GazeAnalyzer to the rest of the application (e.g. UI).
 */
object GazeRepository {

    // Default flow to return if the analyzer hasn't been initialized yet
    private val _defaultFlow = MutableStateFlow(0f)

    /**
     * The active GazeAnalyzer instance.
     * This is set by the GazeService when it initializes.
     */
    var analyzer: GazeAnalyzer? = null

    /**
     * Exposes the computed gaze score.
     * Automatically delegates to the active analyzer's flow if available.
     */
    val gazeScoreFlow: StateFlow<Float>
        get() = analyzer?.gazeScoreFlow ?: _defaultFlow.asStateFlow()
}
