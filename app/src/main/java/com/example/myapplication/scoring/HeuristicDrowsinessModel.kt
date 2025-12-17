package com.example.myapplication.scoring

import android.util.Log
import com.example.myapplication.data.model.DrowsinessFeatures
import kotlin.math.exp
import kotlin.math.min

/**
 * Heuristic-based drowsiness model using weighted feature scoring.
 */
class HeuristicDrowsinessModel : DrowsinessModel {
    
    companion object {
        private const val TAG = "HeuristicModel"
        
        // Feature weights without audio
        private const val WEIGHT_LIGHT = 0.25f
        private const val WEIGHT_STILLNESS = 0.20f
        private const val WEIGHT_TIME = 0.20f
        private const val WEIGHT_HEART_RATE = 0.15f
        private const val WEIGHT_SESSION = 0.10f
        private const val WEIGHT_SCREEN_OFF = 0.10f
        
        // Feature weights with audio
        private const val WEIGHT_LIGHT_AUDIO = 0.22f
        private const val WEIGHT_STILLNESS_AUDIO = 0.18f
        private const val WEIGHT_TIME_AUDIO = 0.18f
        private const val WEIGHT_HEART_RATE_AUDIO = 0.12f
        private const val WEIGHT_SESSION_AUDIO = 0.10f
        private const val WEIGHT_SCREEN_OFF_AUDIO = 0.10f
        private const val WEIGHT_AUDIO = 0.10f
        
        // Heart rate normalization bounds
        private const val MAX_HR = 100f
        private const val MIN_HR = 40f
        
        // Audio normalization bounds (dB)
        private const val QUIET_DB = 35f      // Very quiet, high score
        private const val LOUD_DB = 70f       // Loud, low score
        
        // Duration saturation points
        private const val SESSION_SATURATION_MINUTES = 30f
        private const val SCREEN_OFF_SATURATION_MINUTES = 15f
    }
    
    override fun predict(features: DrowsinessFeatures): Float {
        val hasAudio = features.ambientNoiseDb != null
        
        // Normalize each feature to 0-1 range where 1 = more drowsy
        val lightScore = normalizeLightToScore(features.ambientLightLux)
        val stillnessScore = features.phoneStillnessScore.coerceIn(0f, 1f)
        val timeScore = features.timeProximityScore.coerceIn(0f, 1f)
        val hrScore = normalizeHeartRateToScore(features.heartRateBpm)
        val sessionScore = min(features.sessionDurationMinutes / SESSION_SATURATION_MINUTES, 1f)
        val screenOffScore = min(features.screenOffDurationMinutes / SCREEN_OFF_SATURATION_MINUTES, 1f)
        val audioScore = features.ambientNoiseDb?.let { normalizeAudioToScore(it) }
        
        // Select weights based on whether audio is available
        val weights = if (hasAudio) {
            listOf(WEIGHT_LIGHT_AUDIO, WEIGHT_STILLNESS_AUDIO, WEIGHT_TIME_AUDIO,
                WEIGHT_HEART_RATE_AUDIO, WEIGHT_SESSION_AUDIO, WEIGHT_SCREEN_OFF_AUDIO, WEIGHT_AUDIO)
        } else {
            listOf(WEIGHT_LIGHT, WEIGHT_STILLNESS, WEIGHT_TIME,
                WEIGHT_HEART_RATE, WEIGHT_SESSION, WEIGHT_SCREEN_OFF, 0f)
        }

        val wLight = weights[0]
        val wStill = weights[1]
        val wTime = weights[2]
        val wHr = weights[3]
        val wSession = weights[4]
        val wScreen = weights[5]
        val wAudio = weights[6]
        
        // Log individual scores
        Log.d(TAG, "=== Drowsiness Score Breakdown ===")
        Log.d(TAG, "Light:     %.2f (raw: %.1f lux) × %.2f = %.3f".format(
            lightScore, features.ambientLightLux, wLight, lightScore * wLight))
        Log.d(TAG, "Stillness: %.2f × %.2f = %.3f".format(stillnessScore, wStill, stillnessScore * wStill))
        Log.d(TAG, "Time:      %.2f × %.2f = %.3f".format(timeScore, wTime, timeScore * wTime))
        Log.d(TAG, "HeartRate: %.2f (raw: %.0f bpm) × %.2f = %.3f".format(
            hrScore, features.heartRateBpm, wHr, hrScore * wHr))
        Log.d(TAG, "Session:   %.2f (%.1f min) × %.2f = %.3f".format(
            sessionScore, features.sessionDurationMinutes, wSession, sessionScore * wSession))
        Log.d(TAG, "ScreenOff: %.2f (%.1f min) × %.2f = %.3f".format(
            screenOffScore, features.screenOffDurationMinutes, wScreen, screenOffScore * wScreen))
        if (hasAudio && audioScore != null) {
            Log.d(TAG, "Audio:     %.2f (raw: %.1f dB) × %.2f = %.3f".format(
                audioScore, features.ambientNoiseDb, wAudio, audioScore * wAudio))
        }
        
        // Weighted sum
        var rawScore = (lightScore * wLight +
                       stillnessScore * wStill +
                       timeScore * wTime +
                       hrScore * wHr +
                       sessionScore * wSession +
                       screenOffScore * wScreen)
        
        if (hasAudio && audioScore != null) {
            rawScore += audioScore * wAudio
        }
        
        // Scale to 0-100
        val finalScore = (rawScore * 100f).coerceIn(0f, 100f)
        
        Log.d(TAG, "=== TOTAL: %.1f/100 %s===".format(finalScore, if (hasAudio) "(with audio) " else ""))
        
        return finalScore
    }
    
    private fun normalizeLightToScore(lux: Float): Float {
        val normalized = 1f - (1f - exp(-lux / 50f))
        return normalized.coerceIn(0f, 1f)
    }
    
    private fun normalizeHeartRateToScore(bpm: Float): Float {
        val clampedBpm = bpm.coerceIn(MIN_HR, MAX_HR)
        return 1f - ((clampedBpm - MIN_HR) / (MAX_HR - MIN_HR))
    }
    
    /**
     * Convert ambient noise level (dB) to score
     */
    private fun normalizeAudioToScore(db: Float): Float {
        return when {
            db <= QUIET_DB -> 1.0f
            db >= LOUD_DB -> 0.0f
            else -> 1f - ((db - QUIET_DB) / (LOUD_DB - QUIET_DB))
        }
    }
}
