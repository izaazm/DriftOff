package com.example.myapplication.data.model

/**
 * User-configurable settings
 */
data class SleepSettings(
    val isEnabled: Boolean = false,
    // Schedule
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    // Phone adjustments
    val adjustBrightness: Boolean = true,
    val targetBrightness: Float = 0.1f,
    val maxBrightness: Int = 255,
    val adjustVolume: Boolean = true,
    val targetVolume: Float = 0.0f,
    val enableDnd: Boolean = true,
    // Scoring thresholds
    val drowsyThreshold: Int = 50,
    val sleepingThreshold: Int = 70,
    // Camera verification (user can opt out)
    val enableCameraVerification: Boolean = true,
    val cameraVerificationDuration: Int = 10,  // seconds
    // Audio sampling for ambient noise detection (have to opt in)
    val useAudioSampling: Boolean = false
)
