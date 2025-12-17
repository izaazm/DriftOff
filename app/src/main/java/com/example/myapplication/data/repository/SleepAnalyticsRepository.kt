package com.example.myapplication.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.data.model.SleepAnalyticsSummary
import com.example.myapplication.data.model.SleepSession
import com.example.myapplication.data.model.SleepTrend
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val Context.sleepDataStore: DataStore<Preferences> by preferencesDataStore(name = "sleep_analytics")

/**
 * Repository for storing and retrieving sleep session analytics.
 */
class SleepAnalyticsRepository(private val context: Context) {
    
    companion object {
        // Minimum sleep duration to record
        const val MIN_SLEEP_DURATION_MINUTES = 3
    }
    
    private object Keys {
        val SESSIONS_JSON = stringPreferencesKey("sessions_json")
        val TOTAL_SESSIONS = intPreferencesKey("total_sessions")
    }
    
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    
    // In-memory cache for current session
    private var currentSession: SleepSession? = null
    private var sessionScores = mutableListOf<Float>()
    private var disturbanceCount = 0
    private var cameraVerifications = 0
    private var sleepConfirmedTime: LocalDateTime? = null

    /**
     * Start a new sleep monitoring session.
     */
    fun startSession() {
        val now = LocalDateTime.now()
        currentSession = SleepSession(
            date = LocalDate.now(),
            sleepStartTime = null,
            sleepEndTime = null,
            monitoringStartTime = now,
            monitoringEndTime = null
        )
        sessionScores.clear()
        disturbanceCount = 0
        cameraVerifications = 0
        sleepConfirmedTime = null
        android.util.Log.d("SleepAnalytics", "startSession: monitoringStartTime=$now, sessionId=${currentSession?.id}")
    }
    
    /**
     * Record a drowsiness score reading.
     */
    fun recordScore(score: Float) {
        sessionScores.add(score)
        android.util.Log.v("SleepAnalytics", "recordScore: score=$score, sampleCount=${sessionScores.size}")
    }
    

    fun confirmSleepStart() {
        if (sleepConfirmedTime == null) {
            sleepConfirmedTime = LocalDateTime.now()
            currentSession = currentSession?.copy(
                sleepStartTime = sleepConfirmedTime
            )
            android.util.Log.d("SleepAnalytics", "confirmSleepStart: sleepStartTime=$sleepConfirmedTime, sessionId=${currentSession?.id}")
        }
    }

    fun recordCameraVerification() {
        cameraVerifications++
        android.util.Log.d("SleepAnalytics", "recordCameraVerification: count=$cameraVerifications")
    }

    fun recordDisturbance() {
        if (sleepConfirmedTime != null) {
            disturbanceCount++
            android.util.Log.d("SleepAnalytics", "recordDisturbance: total=$disturbanceCount")
        }
    }

    fun recordWakeUp() {
        if (sleepConfirmedTime != null) {
            currentSession = currentSession?.copy(
                sleepEndTime = LocalDateTime.now()
            )
            android.util.Log.d("SleepAnalytics", "recordWakeUp: sleepEndTime=${currentSession?.sleepEndTime}")
        }
    }

    suspend fun endSession() {
        val session = currentSession ?: run {
            android.util.Log.d("SleepAnalytics", "endSession: no current session to end")
            return
        }
        val now = LocalDateTime.now()
        android.util.Log.d("SleepAnalytics", "endSession: ending sessionId=${session.id}, now=$now")

        val sleepDuration = if (session.sleepStartTime != null) {
            val endTime = session.sleepEndTime ?: now
            ChronoUnit.MINUTES.between(session.sleepStartTime, endTime).toInt()
        } else 0
        
        val timeToSleep = if (session.sleepStartTime != null) {
            ChronoUnit.MINUTES.between(session.monitoringStartTime, session.sleepStartTime).toInt()
        } else 0
        
        val avgScore = if (sessionScores.isNotEmpty()) {
            sessionScores.average().toFloat()
        } else 0f
        
        val peakScore = sessionScores.maxOrNull() ?: 0f
        
        val finalSession = session.copy(
            monitoringEndTime = now,
            totalSleepMinutes = sleepDuration,
            timeToFallAsleepMinutes = timeToSleep,
            disturbanceCount = disturbanceCount,
            averageDrowsinessScore = avgScore,
            peakDrowsinessScore = peakScore,
            hibernationActivated = sleepConfirmedTime != null,
            cameraVerificationsCount = cameraVerifications
        )
        
        android.util.Log.d("SleepAnalytics", "endSession: computed finalSession=$finalSession")

        // Check for false positives
        if (sleepDuration >= MIN_SLEEP_DURATION_MINUTES) {
            saveSession(finalSession)
        } else {
            android.util.Log.d("SleepAnalytics", 
                "Session not saved: sleep duration ($sleepDuration min) < minimum ($MIN_SLEEP_DURATION_MINUTES min)")
        }
        
        // Clear current session
        currentSession = null
        sessionScores.clear()
    }
    
    /**
     * Save a session to persistent storage.
     */
    private suspend fun saveSession(session: SleepSession) {
        android.util.Log.d("SleepAnalytics", "saveSession: saving sessionId=${session.id}, date=${session.date}")
        context.sleepDataStore.edit { prefs ->
            val existingSessions = prefs[Keys.SESSIONS_JSON] ?: "[]"
            val sessions = try {
                parseSessionsJson(existingSessions).toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }
            
            // Keep only last 30 days of data
            val cutoffDate = LocalDate.now().minusDays(30)
            sessions.removeAll { it.date.isBefore(cutoffDate) }
            
            // Add new session
            sessions.add(session)
            
            prefs[Keys.SESSIONS_JSON] = serializeSessionsJson(sessions)
            prefs[Keys.TOTAL_SESSIONS] = (prefs[Keys.TOTAL_SESSIONS] ?: 0) + 1
        }
        android.util.Log.d("SleepAnalytics", "saveSession: saved sessionId=${session.id}")
    }
    
    /**
     * Get all sessions for the last N days.
     */
    suspend fun getRecentSessions(days: Int = 7): List<SleepSession> {
        val prefs = context.sleepDataStore.data.first()
        val sessionsJson = prefs[Keys.SESSIONS_JSON] ?: return emptyList()
        
        val cutoffDate = LocalDate.now().minusDays(days.toLong())
        return parseSessionsJson(sessionsJson)
            .filter { it.date.isAfter(cutoffDate) || it.date.isEqual(cutoffDate) }
            .sortedByDescending { it.date }
    }
    
    /**
     * Get analytics summary for the last N days.
     */
    suspend fun getAnalyticsSummary(days: Int = 7): SleepAnalyticsSummary {
        val sessions = getRecentSessions(days)
        
        if (sessions.isEmpty()) {
            return SleepAnalyticsSummary(
                totalNights = 0,
                averageSleepDurationMinutes = 0,
                averageTimeToFallAsleepMinutes = 0,
                averageDisturbances = 0f,
                bestNightDate = null,
                worstNightDate = null,
                sleepTrend = SleepTrend.INSUFFICIENT_DATA,
                averageBedtime = "--:--",
                averageWakeTime = "--:--"
            )
        }
        
        val avgDuration = sessions.map { it.totalSleepMinutes }.average().toInt()
        val avgTimeToSleep = sessions.map { it.timeToFallAsleepMinutes }.average().toInt()
        val avgDisturbances = sessions.map { it.disturbanceCount }.average().toFloat()
        
        val bestNight = sessions.maxByOrNull { it.totalSleepMinutes }
        val worstNight = sessions.minByOrNull { it.totalSleepMinutes }
        
        // Calculate trend (compare first half to second half)
        val trend = calculateTrend(sessions)
        
        // Calculate average bedtime/wake time
        val avgBedtime = calculateAverageTime(sessions.mapNotNull { it.sleepStartTime })
        val avgWakeTime = calculateAverageTime(sessions.mapNotNull { it.sleepEndTime })
        
        return SleepAnalyticsSummary(
            totalNights = sessions.size,
            averageSleepDurationMinutes = avgDuration,
            averageTimeToFallAsleepMinutes = avgTimeToSleep,
            averageDisturbances = avgDisturbances,
            bestNightDate = bestNight?.date,
            worstNightDate = worstNight?.date,
            sleepTrend = trend,
            averageBedtime = avgBedtime,
            averageWakeTime = avgWakeTime
        )
    }
    
    private fun calculateTrend(sessions: List<SleepSession>): SleepTrend {
        if (sessions.size < 4) return SleepTrend.INSUFFICIENT_DATA
        
        val sorted = sessions.sortedBy { it.date }
        val midpoint = sorted.size / 2
        val firstHalf = sorted.take(midpoint)
        val secondHalf = sorted.drop(midpoint)
        
        val firstAvg = firstHalf.map { it.totalSleepMinutes }.average()
        val secondAvg = secondHalf.map { it.totalSleepMinutes }.average()
        
        val diff = secondAvg - firstAvg
        return when {
            diff > 15 -> SleepTrend.IMPROVING
            diff < -15 -> SleepTrend.DECLINING
            else -> SleepTrend.STABLE
        }
    }
    
    private fun calculateAverageTime(times: List<LocalDateTime>): String {
        if (times.isEmpty()) return "--:--"
        
        val avgMinutes = times.map { it.hour * 60 + it.minute }.average().toInt()
        val hour = avgMinutes / 60
        val minute = avgMinutes % 60
        
        val time = LocalDateTime.now().withHour(hour % 24).withMinute(minute)
        return time.format(timeFormatter)
    }
    
    // Simple JSON serialization
    // Don't know how to it better yet....
    private fun serializeSessionsJson(sessions: List<SleepSession>): String {
        return sessions.joinToString(separator = "|||") { session ->
            listOf(
                session.id,
                session.date.toString(),
                session.sleepStartTime?.toString() ?: "",
                session.sleepEndTime?.toString() ?: "",
                session.monitoringStartTime.toString(),
                session.monitoringEndTime?.toString() ?: "",
                session.totalSleepMinutes.toString(),
                session.timeToFallAsleepMinutes.toString(),
                session.disturbanceCount.toString(),
                session.averageDrowsinessScore.toString(),
                session.peakDrowsinessScore.toString(),
                session.hibernationActivated.toString(),
                session.cameraVerificationsCount.toString()
            ).joinToString(":::")
        }
    }
    
    private fun parseSessionsJson(json: String): List<SleepSession> {
        if (json.isBlank() || json == "[]") return emptyList()
        
        return json.split("|||").mapNotNull { sessionStr ->
            try {
                val parts = sessionStr.split(":::")
                if (parts.size >= 13) {
                    SleepSession(
                        id = parts[0],
                        date = LocalDate.parse(parts[1]),
                        sleepStartTime = parts[2].takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it) },
                        sleepEndTime = parts[3].takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it) },
                        monitoringStartTime = LocalDateTime.parse(parts[4]),
                        monitoringEndTime = parts[5].takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it) },
                        totalSleepMinutes = parts[6].toIntOrNull() ?: 0,
                        timeToFallAsleepMinutes = parts[7].toIntOrNull() ?: 0,
                        disturbanceCount = parts[8].toIntOrNull() ?: 0,
                        averageDrowsinessScore = parts[9].toFloatOrNull() ?: 0f,
                        peakDrowsinessScore = parts[10].toFloatOrNull() ?: 0f,
                        hibernationActivated = parts[11].toBooleanStrictOrNull() ?: false,
                        cameraVerificationsCount = parts[12].toIntOrNull() ?: 0
                    )
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
