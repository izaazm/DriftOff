package com.example.myapplication.data.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Sleep session analytic
 */
data class SleepSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val date: LocalDate,
    val sleepStartTime: LocalDateTime?,
    val sleepEndTime: LocalDateTime?,
    val monitoringStartTime: LocalDateTime,
    val monitoringEndTime: LocalDateTime?,
    val totalSleepMinutes: Int = 0,
    val timeToFallAsleepMinutes: Int = 0,
    val disturbanceCount: Int = 0,           // Times user woke up mid-sleep
    val averageDrowsinessScore: Float = 0f,
    val peakDrowsinessScore: Float = 0f,
    val hibernationActivated: Boolean = false,
    val cameraVerificationsCount: Int = 0
)

/**
 * Summary of sleep analytics over a time period.
 */
data class SleepAnalyticsSummary(
    val totalNights: Int,
    val averageSleepDurationMinutes: Int,
    val averageTimeToFallAsleepMinutes: Int,
    val averageDisturbances: Float,
    val bestNightDate: LocalDate?,
    val worstNightDate: LocalDate?,
    val sleepTrend: SleepTrend,  // e.g., IMPROVING, DECLINING, STABLE
    val averageBedtime: String,
    val averageWakeTime: String
)

enum class SleepTrend {
    IMPROVING,
    DECLINING,
    STABLE,
    INSUFFICIENT_DATA
}
