package com.example.myapplication.data.model

/**
 * Input features class
 */
data class DrowsinessFeatures(
    val ambientLightLux: Float,           // Light sensor reading
    val phoneStillnessScore: Float,       // 0-1, based on accelerometer (1 = completely still)
    val timeProximityScore: Float,        // 0-1, how close to configured sleep window center
    val heartRateBpm: Float,              // Heart rate or default value (70)
    val sessionDurationMinutes: Float,    // Time spent in drowsy conditions
    val screenOffDurationMinutes: Float,  // How long screen has been off
    val ambientNoiseDb: Float? = null,    // Ambient noise level in dB (null if audio sampling disabled)
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result class
 */
data class DrowsinessResult(
    val score: Float,
    val state: DrowsinessState,
    val features: DrowsinessFeatures,
    val shouldVerifyWithCamera: Boolean,
    val adaptiveMultiplier: Float = 1.0f
)

/**
 * Drowsiness state classification
 */
enum class DrowsinessState {
    AWAKE,           // Score 0-30: No adjustments
    RELAXING,        // Score 31-50: Gentle adjustments
    DROWSY,          // Score 51-70: Apply configured settings
    LIKELY_SLEEPING  // Score 71-100: Full sleep mode + camera verification
}
