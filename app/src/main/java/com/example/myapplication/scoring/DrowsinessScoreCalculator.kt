package com.example.myapplication.scoring

import com.example.myapplication.data.model.DrowsinessFeatures
import com.example.myapplication.data.model.DrowsinessResult
import com.example.myapplication.data.model.DrowsinessState
import com.example.myapplication.data.model.SleepSettings
import com.example.myapplication.data.repository.FeedbackRepository
import com.example.myapplication.providers.AudioSampleProvider
import com.example.myapplication.providers.HealthConnectProvider
import com.example.myapplication.providers.SensorDataProvider
import java.time.LocalTime

/**
 * Calculator class for drowsiness score
 */
class DrowsinessScoreCalculator(
    private val model: DrowsinessModel,
    private val sensorProvider: SensorDataProvider,
    private val healthConnectProvider: HealthConnectProvider,
    private val feedbackRepository: FeedbackRepository,
    private val audioSampleProvider: AudioSampleProvider? = null
) {
    
    companion object {
        const val DEFAULT_HEART_RATE = 60f  // Default HR when unavailable
        const val EMA_SMOOTHING_FACTOR = 0.3f
        const val MIN_STATE_HOLD_CYCLES = 3
    }
    
    // Smoothed score using EMA
    private var smoothedScore: Float? = null
    private var currentStateHoldCount: Int = 0
    private var pendingState: DrowsinessState? = null
    
    /**
     * Calculate the current drowsiness result smoothed with EMA.
     */
    suspend fun calculate(settings: SleepSettings): DrowsinessResult {
        // Collect features from all sources
        val features = collectFeatures(settings)
        
        // Get adaptive multiplier from feedback
        val adaptiveMultiplier = feedbackRepository.getAdaptiveMultiplier()
        
        // Run model prediction
        val rawScore = model.predict(features)
        
        // Apply adaptive multiplier
        val adjustedScore = (rawScore * adaptiveMultiplier).coerceIn(0f, 100f)
        
        // Apply EMA smoothing
        val finalScore = applySmoothing(adjustedScore)
        
        // Determine state based on thresholds with hysteresis
        val state = determineStateWithHysteresis(finalScore, settings)
        
        // Determine if camera verification should be triggered
        val shouldVerifyWithCamera = settings.enableCameraVerification &&
                                     state == DrowsinessState.LIKELY_SLEEPING
        
        return DrowsinessResult(
            score = finalScore,
            state = state,
            features = features,
            shouldVerifyWithCamera = shouldVerifyWithCamera,
            adaptiveMultiplier = adaptiveMultiplier
        )
    }
    
    /**
     * Apply EMA to the score.
     */
    private fun applySmoothing(newScore: Float): Float {
        val previousScore = smoothedScore
        
        val smoothed = if (previousScore == null) {
            newScore
        } else {
            // EMA formula: smoothed = alpha * new + (1 - alpha) * previous
            EMA_SMOOTHING_FACTOR * newScore + (1 - EMA_SMOOTHING_FACTOR) * previousScore
        }
        
        smoothedScore = smoothed
        return smoothed
    }
    
    /**
     * Determine state with hysteresis to prevent rapid state changes.
     */
    private fun determineStateWithHysteresis(score: Float, settings: SleepSettings): DrowsinessState {
        // Calculate the "raw" state from score
        val rawState = when {
            score >= settings.sleepingThreshold -> DrowsinessState.LIKELY_SLEEPING
            score >= settings.drowsyThreshold -> DrowsinessState.DROWSY
            score >= 30 -> DrowsinessState.RELAXING
            else -> DrowsinessState.AWAKE
        }
        
        // Check if state is changing
        if (rawState == pendingState) {
            currentStateHoldCount++
        } else {
            // Different state - reset counter
            pendingState = rawState
            currentStateHoldCount = 1
        }
        
        // Only switch to new state if held for enough cycles
        return if (currentStateHoldCount >= MIN_STATE_HOLD_CYCLES) {
            rawState
        } else {
            pendingState ?: DrowsinessState.AWAKE
        }
    }
    
    /**
     * Reset the smoothing state (e.g., when service restarts).
     */
    fun reset() {
        smoothedScore = null
        currentStateHoldCount = 0
        pendingState = null
    }
    
    /**
     * Collect all input features from various data sources.
     */
    private suspend fun collectFeatures(settings: SleepSettings): DrowsinessFeatures {
        // Get sensor data
        val ambientLight = sensorProvider.getAmbientLight()
        val stillnessScore = sensorProvider.getStillnessScore()
        val screenOffDuration = sensorProvider.getScreenOffDurationMinutes()
        val sessionDuration = sensorProvider.getSessionDurationMinutes()
        
        val heartRate = healthConnectProvider.getLatestHeartRate() ?: DEFAULT_HEART_RATE
        val timeProximity = calculateTimeProximity(settings)
        val ambientNoiseDb: Float? = if (settings.useAudioSampling &&
                                          audioSampleProvider != null && 
                                          audioSampleProvider.hasPermission()) {
            audioSampleProvider.sampleAmbientLevel()
        } else {
            null
        }
        
        return DrowsinessFeatures(
            ambientLightLux = ambientLight,
            phoneStillnessScore = stillnessScore,
            timeProximityScore = timeProximity,
            heartRateBpm = heartRate,
            sessionDurationMinutes = sessionDuration,
            screenOffDurationMinutes = screenOffDuration,
            ambientNoiseDb = ambientNoiseDb
        )
    }
    
    /**
     * Calculate how close current time is to the center of the sleep window.
     */
    private fun calculateTimeProximity(settings: SleepSettings): Float {
        val now = LocalTime.now()
        val currentMinutes = now.hour * 60 + now.minute
        
        // Convert sleep window to minutes
        val startMinutes = settings.startHour * 60 + settings.startMinute
        var endMinutes = settings.endHour * 60 + settings.endMinute
        
        // Handle overnight window (e.g., 22:00 to 07:00)
        if (endMinutes < startMinutes) {
            endMinutes += 24 * 60
        }
        
        // Adjust current time for overnight calculation
        var adjustedCurrent = currentMinutes
        if (currentMinutes < startMinutes && endMinutes > 24 * 60) {
            adjustedCurrent += 24 * 60
        }
        
        // Check if in the sleep window
        if (adjustedCurrent < startMinutes || adjustedCurrent > endMinutes) {
            return 0f  // Outside sleep window
        }
        
        // Calculate center of sleep window
        val windowDuration = endMinutes - startMinutes
        val centerMinutes = startMinutes + windowDuration / 2
        
        // Distance from center, normalized to 0-1
        val distanceFromCenter = kotlin.math.abs(adjustedCurrent - centerMinutes).toFloat()
        val maxDistance = windowDuration / 2f
        
        // Invert so center = 1.0, edges = lower
        return 1f - (distanceFromCenter / maxDistance).coerceIn(0f, 1f)
    }
}
