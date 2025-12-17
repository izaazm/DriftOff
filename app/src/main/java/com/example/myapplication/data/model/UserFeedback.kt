package com.example.myapplication.data.model

import java.time.LocalDate

/**
 * User feedback class.
 */
data class UserFeedback(
    val date: LocalDate,
    val sleepQualityRating: Int,    // 1-5 stars
    val wakeUpFeeling: WakeUpFeeling,
    val notes: String? = null
)

enum class WakeUpFeeling {
    TERRIBLE,   // Very bad, app may have been too aggressive or not helpful
    POOR,       // Below average
    OKAY,       // Neutral
    GOOD,       // Above average
    GREAT       // Excellent, app helped significantly
}

/**
 * Camera verification result class
 */
data class SleepVerificationResult(
    val isSleeping: Boolean,
    val confidence: Float,          // 0-1 confidence in the result
    val eyeOpenProbability: Float,  // Average eye open probability (lower = more likely sleeping)
    val faceDetected: Boolean       // Whether a face was detected at all
)
