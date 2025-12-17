package com.example.myapplication.providers

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Provider for accessing heart rate data from Health Connect.
 */
class HealthConnectProvider(private val context: Context) {
    
    companion object {
        private const val LOOKBACK_MINUTES = 5L
    }
    
    private var healthConnectClient: HealthConnectClient? = null
    
    /**
     * Initialize Health Connect client if available.
     */
    fun initialize(): Boolean {
        return try {
            if (isAvailable()) {
                healthConnectClient = HealthConnectClient.getOrCreate(context)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * Check if Health Connect is available on this device.
     */
    fun isAvailable(): Boolean {
        return try {
            val status = HealthConnectClient.getSdkStatus(context)
            status == HealthConnectClient.SDK_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * Get the latest heart rate from Health Connect.
     */
    suspend fun getLatestHeartRate(): Float? {
        val client = healthConnectClient ?: return null
        
        return try {
            val now = Instant.now()
            val startTime = now.minus(LOOKBACK_MINUTES, ChronoUnit.MINUTES)
            
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, now)
                )
            )
            
            // Get average of all samples in the time range
            val samples = response.records.flatMap { record ->
                record.samples.map { it.beatsPerMinute }
            }
            
            if (samples.isNotEmpty()) {
                samples.average().toFloat()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

