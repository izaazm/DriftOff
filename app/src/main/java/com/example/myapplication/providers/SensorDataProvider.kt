package com.example.myapplication.providers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Provides sensor data scores for drowsiness detection.
 */
class SensorDataProvider(private val context: Context) : SensorEventListener {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    // Current sensor values
    private var currentLux: Float = 100f  // Default to moderate light
    private var movementHistory: MutableList<Float> = mutableListOf()

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private val alpha = 0.8f
    
    // Screen state tracking
    private var screenOffTimestamp: Long? = null
    private var screenOnTimestamp: Long? = null
    private var isScreenOn: Boolean = true
    
    // Session tracking
    private var sessionStartTime: Long? = null
    
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    screenOffTimestamp = System.currentTimeMillis()
                    screenOnTimestamp = null
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    screenOnTimestamp = System.currentTimeMillis()
                    screenOffTimestamp = null
                }
            }
        }
    }
    
    companion object {
        private const val MOVEMENT_HISTORY_SIZE = 75  // 15 seconds @ 5 Hz SENSOR_DELAY_NORMAL
        private const val STILLNESS_THRESHOLD = 0.5f  // m/s² variance threshold
    }
    
    /**
     * Start collecting sensor data.
     */
    fun start() {
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        
        // Register screen state receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(screenReceiver, filter)
        
        // Start session
        sessionStartTime = System.currentTimeMillis()
    }
    
    /**
     * Stop collecting sensor data.
     */
    fun stop() {
        sensorManager.unregisterListener(this)
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (_: IllegalArgumentException) {
            // fallthrough
        }
        sessionStartTime = null
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                currentLux = event.values[0]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Remove gravity with high-pass filter
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Isolate gravity
                lastX = alpha * lastX + (1 - alpha) * x
                lastY = alpha * lastY + (1 - alpha) * y
                lastZ = alpha * lastZ + (1 - alpha) * z

                // Remove gravity to get linear acceleration
                val linearX = x - lastX
                val linearY = y - lastY
                val linearZ = z - lastZ

                val movement = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
                movementHistory.add(movement)
                if (movementHistory.size > MOVEMENT_HISTORY_SIZE) {
                    movementHistory.removeAt(0)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Error if not implemented
    }
    
    /**
     * Get current ambient light level in lux.
     */
    fun getAmbientLight(): Float = currentLux
    
    /**
     * Get stillness score from 0 (moving) - 1 (still).
     */
    fun getStillnessScore(): Float {
        if (movementHistory.isEmpty()) return 0.5f
        
        // Calculate variance of movement
        val mean = movementHistory.average().toFloat()
        val variance = movementHistory.map { (it - mean) * (it - mean) }.average().toFloat()
        
        // Lower variance = more still = higher score
        return (1f - (variance / STILLNESS_THRESHOLD).coerceIn(0f, 1f))
    }
    
    /**
     * Get the recent maximum movement magnitude
     */
    fun getRecentMovementMagnitude(): Float {
        if (movementHistory.isEmpty()) return 0f
        return movementHistory.maxOrNull() ?: 0f
    }
    
    /**
     * Get how long the screen has been ON in minutes.
     * Returns 0 if screen is off.
     */
    fun getScreenOnDurationMinutes(): Float {
        if (!isScreenOn) return 0f
        
        // Use screenOffTimestamp as "switch on time" if screen is currently on.
        // Note: Logic in receiver needs slight adjustment to track ON timestamp too.
        val onTime = screenOnTimestamp ?: return 0f
        val durationMs = System.currentTimeMillis() - onTime
        return durationMs / 60000f
    }
    
    /**
    /**
     * Get how long the screen has been OFF in minutes.
     */
    fun getScreenOffDurationMinutes(): Float {
        if (isScreenOn) return 0f
        
        val offTime = screenOffTimestamp ?: return 0f
        val durationMs = System.currentTimeMillis() - offTime
        return durationMs / 60000f
    }
    
    /**
     * Get session duration in minutes.
     */
    fun getSessionDurationMinutes(): Float {
        val startTime = sessionStartTime ?: return 0f
        val durationMs = System.currentTimeMillis() - startTime
        return durationMs / 60000f
    }
}
