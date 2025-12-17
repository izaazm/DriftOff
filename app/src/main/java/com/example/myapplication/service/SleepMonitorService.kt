package com.example.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.MainActivity
import com.example.myapplication.data.model.DrowsinessState
import com.example.myapplication.data.model.SleepSettings
import com.example.myapplication.data.repository.FeedbackRepository
import com.example.myapplication.data.repository.ScoreRepository
import com.example.myapplication.data.repository.SettingsRepository
import com.example.myapplication.data.repository.SleepAnalyticsRepository
import com.example.myapplication.providers.AudioSampleProvider
import com.example.myapplication.providers.CameraVerificationService
import com.example.myapplication.providers.HealthConnectProvider
import com.example.myapplication.providers.SensorDataProvider
import com.example.myapplication.scoring.DrowsinessScoreCalculator
import com.example.myapplication.scoring.HeuristicDrowsinessModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalTime

/**
 * Foreground service that monitors drowsiness and adjusts phone settings.
 */
@androidx.camera.core.ExperimentalGetImage
class SleepMonitorService : LifecycleService() {
    
    companion object {
        private const val TAG = "SleepMonitorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sleep_monitor_channel"
        
        // Monitoring intervals
        private const val ACTIVE_MONITORING_INTERVAL_MS = 15000L   // 15 seconds when active
        private const val HIBERNATION_CHECK_INTERVAL_MS = 300000L  // 5 minutes when hibernating
        
        // Hibernation thresholds
        private const val HIBERNATION_CONFIRM_CYCLES = 20// Consecutive cycles needed (300 seconds)
        
        const val ACTION_START = "com.example.myapplication.START_MONITORING"
        const val ACTION_STOP = "com.example.myapplication.STOP_MONITORING"
        const val ACTION_WAKE_UP = "com.example.myapplication.WAKE_UP"
    }
    
    // Dependencies
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var feedbackRepository: FeedbackRepository
    private lateinit var analyticsRepository: SleepAnalyticsRepository
    private lateinit var sensorProvider: SensorDataProvider
    private lateinit var healthConnectProvider: HealthConnectProvider
    private lateinit var cameraVerificationService: CameraVerificationService
    private lateinit var settingsController: PhoneSettingsController
    private lateinit var scoreCalculator: DrowsinessScoreCalculator
    
    private var monitoringJob: Job? = null
    private var currentSettings: SleepSettings = SleepSettings()
    private var lastDrowsinessState: DrowsinessState = DrowsinessState.AWAKE
    
    // Hibernation state
    private var isHibernating = false
    private var highScoreConsecutiveCount = 0
    private var sleepConfirmed = false
    
    // Screen state receiver for waking from hibernation
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (isHibernating) {
                        Log.d(TAG, "User interaction detected - waking from hibernation")
                        wakeFromHibernation()
                    }
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        initializeDependencies()
        createNotificationChannel()
        registerScreenReceiver()
    }

    private fun initializeDependencies() {
        settingsRepository = SettingsRepository(this)
        feedbackRepository = FeedbackRepository(this)
        analyticsRepository = SleepAnalyticsRepository(this)
        sensorProvider = SensorDataProvider(this)
        healthConnectProvider = HealthConnectProvider(this)
        cameraVerificationService = CameraVerificationService(this)
        settingsController = PhoneSettingsController(this)
        
        // Audio sampling provider (for optional ambient noise detection)
        val audioSampleProvider = AudioSampleProvider(this)
        
        val model = HeuristicDrowsinessModel()
        scoreCalculator = DrowsinessScoreCalculator(
            model = model,
            sensorProvider = sensorProvider,
            healthConnectProvider = healthConnectProvider,
            feedbackRepository = feedbackRepository,
            audioSampleProvider = audioSampleProvider
        )
        
        healthConnectProvider.initialize()
    }
    
    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
            }
            ACTION_WAKE_UP -> {
                wakeFromHibernation()
            }
            ACTION_START, null -> {
                startMonitoring()
            }
        }
        
        return START_STICKY
    }

    private fun startMonitoring() {
        Log.d(TAG, "Starting sleep monitoring service")
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification("Starting monitoring..."))
        
        // Save original settings
        settingsController.saveCurrentSettings()
        
        // Start sensor collection
        sensorProvider.start()
        
        // Reset states
        scoreCalculator.reset()
        isHibernating = false
        highScoreConsecutiveCount = 0
        sleepConfirmed = false
        
        // Start analytics session
        analyticsRepository.startSession()
        
        // Notify UI that monitoring has started
        ScoreRepository.setMonitoring(true)
        
        // Start monitoring loop
        monitoringJob = lifecycleScope.launch {
            // Load initial settings
            currentSettings = settingsRepository.settingsFlow.first()
            
            // Update notification immediately
            updateNotification("Monitoring active")
            
            // Collect settings updates
            launch {
                settingsRepository.settingsFlow.collect { settings ->
                    currentSettings = settings
                }
            }
            
            // Run first monitoring cycle immediately
            try {
                if (isWithinSleepWindow(currentSettings)) {
                    performMonitoringCycle()
                } else {
                    updateNotification("Outside sleep hours - standby")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in initial monitoring cycle", e)
            }
            
            // Main monitoring loop
            while (isActive) {
                // Use different interval based on hibernation state
                val interval = if (isHibernating) {
                    HIBERNATION_CHECK_INTERVAL_MS
                } else {
                    ACTIVE_MONITORING_INTERVAL_MS
                }
                
                delay(interval)
                
                try {
                    if (checkForWakeSignals()) {
                        handleWakeUp()
                    } else if (isWithinSleepWindow(currentSettings)) {
                        if (isHibernating) {
                            // Light check during hibernation - just verify still in sleep window
                            updateNotification("💤 Sleeping - monitoring paused")
                        } else {
                            performMonitoringCycle()
                        }
                    } else {
                        // Outside sleep window
                        handleEndOfSleepWindow()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring cycle", e)
                }
            }
        }
    }
    

    private fun checkForWakeSignals(): Boolean {
        // Sustained screen usage, screen on > 1 minute
        val screenDuration = sensorProvider.getScreenOnDurationMinutes()
        if (screenDuration > 1.0f) {
            Log.d(TAG, "Wake signal: Screen on for $screenDuration minutes")
            return true
        }
        
        // Significant movement, movement > 2.0 m/s^2
        val movement = sensorProvider.getRecentMovementMagnitude()
        if (movement > 2.0f) {
            Log.d(TAG, "Wake signal: High movement detected ($movement)")
            return true
        }
        
        return false
    }
    
    private fun handleWakeUp() {
        if (lastDrowsinessState == DrowsinessState.AWAKE && !isHibernating) return
        
        Log.d(TAG, "Active wake signal detected - stopping sleep session")
        
        if (isHibernating) {
            wakeFromHibernation()
        } else {
            lastDrowsinessState = DrowsinessState.AWAKE
            analyticsRepository.recordWakeUp()

            // Call endSession from a coroutine
            // Don't know why this works....
            lifecycleScope.launch {
                try {
                    analyticsRepository.endSession()
                } catch (e: Exception) {
                    Log.e(TAG, "Error ending analytics session", e)
                }
            }

            // Reset state
            sleepConfirmed = false
            highScoreConsecutiveCount = 0
            scoreCalculator.reset()
            
            // Restore settings
            settingsController.restoreSettings()
            
            // Start new session tracking (for next sleep attempt)
            analyticsRepository.startSession()
            updateNotification("👀 Awake detected - session ended")
        }
    }

    private suspend fun performMonitoringCycle() {
        val result = scoreCalculator.calculate(currentSettings)
        
        Log.d(TAG, "Score: ${result.score.toInt()}, State: ${result.state}")
        
        analyticsRepository.recordScore(result.score)
        
        // Update notification with current state
        // Cool idea honestly :D
        val stateEmoji = when (result.state) {
            DrowsinessState.AWAKE -> "👀"
            DrowsinessState.RELAXING -> "😌"
            DrowsinessState.DROWSY -> "😴"
            DrowsinessState.LIKELY_SLEEPING -> "💤"
        }
        updateNotification("$stateEmoji ${result.state.name} (${result.score.toInt()})")
        
        ScoreRepository.updateScore(result.score, result.state)

        if (result.score >= currentSettings.sleepingThreshold.toFloat()) {
            highScoreConsecutiveCount++
        } else {
            highScoreConsecutiveCount = 0
        }
        
        when (result.state) {
            DrowsinessState.AWAKE -> {
                if (lastDrowsinessState != DrowsinessState.AWAKE) {
                    settingsController.restoreSettings()
                    if (sleepConfirmed) {
                        analyticsRepository.recordDisturbance()
                    }
                }
            }
            DrowsinessState.RELAXING, DrowsinessState.DROWSY -> {
                // Continuous control based on drowsiness score
                val minScore = 30f
                val maxScore = currentSettings.sleepingThreshold.toFloat()
                val normalizedScore = ((result.score - minScore) / (maxScore - minScore)).coerceIn(0f, 1f)

                // Brightness: interpolate from 0.7 (at score 30) to targetBrightness (at sleepingThreshold)
                val maxBrightnessLevel = 0.7f
                val minBrightnessLevel = currentSettings.targetBrightness
                val targetBrightness = maxBrightnessLevel - (normalizedScore * (maxBrightnessLevel - minBrightnessLevel))

                settingsController.applyGentleAdjustments(
                    targetBrightness = targetBrightness,
                    maxBrightness = currentSettings.maxBrightness
                )

                if (currentSettings.adjustVolume) {
                    val maxVolumeLevel = 0.7f
                    val minVolumeLevel = currentSettings.targetVolume
                    val targetVolume = maxVolumeLevel - (normalizedScore * (maxVolumeLevel - minVolumeLevel))
                    settingsController.applyVolume(targetVolume)
                }

                Log.d(TAG, "Continuous control: score=${result.score.toInt()}, normalized=${"%.2f".format(normalizedScore)}, brightness=${"%.2f".format(targetBrightness)}")
            }
            DrowsinessState.LIKELY_SLEEPING -> {
                handleLikelySleeping(result.shouldVerifyWithCamera)
            }
        }
        
        lastDrowsinessState = result.state
    }

    private suspend fun handleLikelySleeping(shouldVerifyWithCamera: Boolean) {
        val canAttemptCamera = shouldVerifyWithCamera && cameraVerificationService.hasPermission()
        if (shouldVerifyWithCamera && !cameraVerificationService.hasPermission()) {
            Log.w(TAG, "Camera verification requested but CAMERA permission not granted - falling back to score-only flow")
        }

        // Verify with camera if enabled and permission is available
        if (canAttemptCamera && !sleepConfirmed) {
            val verification = try {
                // Record an attempted verification
                analyticsRepository.recordCameraVerification()
                cameraVerificationService.verifySleepState(
                    currentSettings.cameraVerificationDuration,
                    this@SleepMonitorService
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera verification failed", e)
                null
            }

            if (verification?.isSleeping == true) {
                sleepConfirmed = true
                analyticsRepository.confirmSleepStart()
            } else if (verification == null) {
                if (highScoreConsecutiveCount >= HIBERNATION_CONFIRM_CYCLES) {
                    Log.w(
                        TAG,
                        "Verification unavailable but sustained high scores detected - confirming sleep start"
                    )
                    sleepConfirmed = true
                    analyticsRepository.confirmSleepStart()
                }
            }
        }
        
        // Apply full sleep mode
        settingsController.applyFullSleepMode(
            targetBrightness = currentSettings.targetBrightness,
            targetVolume = currentSettings.targetVolume,
            enableDnd = currentSettings.enableDnd,
            maxBrightness = currentSettings.maxBrightness
        )
        
        // Check if should enter hibernation
        if (highScoreConsecutiveCount >= HIBERNATION_CONFIRM_CYCLES && 
            (sleepConfirmed || !currentSettings.enableCameraVerification)) {
            enterHibernation()
        }
    }
    
    /**
     * Enter hibernation mode
     */
    private fun enterHibernation() {
        if (isHibernating) return
        
        Log.d(TAG, "Entering hibernation mode - user confirmed asleep")
        isHibernating = true
        sleepConfirmed = true
        analyticsRepository.confirmSleepStart()
        
        // Stop active sensor monitoring
        sensorProvider.stop()
        
        // Update notification
        updateNotification("💤 Sleeping - monitoring paused")
        
        // Put phone to sleep (turn off screen)
        putPhoneToSleep()
    }

    private fun putPhoneToSleep() {
        try {
            // For now, we'll just ensure settings are in sleep mode
            // The screen will turn off naturally due to timeout
            // a more aggressive approach would require Device Admin privileges...
            
            Log.d(TAG, "Sleep mode active - screen will turn off on timeout")
            
            // Ensure all sleep settings are applied
            settingsController.applyFullSleepMode(
                targetBrightness = 0.01f,  // Minimum brightness
                targetVolume = 0f,
                enableDnd = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error putting phone to sleep", e)
        }
    }

    private fun wakeFromHibernation() {
        if (!isHibernating) return
        
        Log.d(TAG, "Waking from hibernation")
        isHibernating = false
        highScoreConsecutiveCount = 0
        
        // Record wake up for analytics
        analyticsRepository.recordWakeUp()
        
        // Restart sensor monitoring
        sensorProvider.start()
        scoreCalculator.reset()
        
        // Update notification
        updateNotification("👀 Awake - resuming monitoring")
        
        // Restore settings briefly (will be adjusted in next cycle)
        settingsController.restoreSettings()
    }
    
    private suspend fun handleEndOfSleepWindow() {
        if (lastDrowsinessState != DrowsinessState.AWAKE || isHibernating) {
            settingsController.restoreSettings()
            lastDrowsinessState = DrowsinessState.AWAKE
            
            // End the sleep session
            analyticsRepository.endSession()
            sleepConfirmed = false
            isHibernating = false
        }
        updateNotification("Outside sleep hours - standby")
    }
    
    private fun isWithinSleepWindow(settings: SleepSettings): Boolean {
        val now = LocalTime.now()
        val start = LocalTime.of(settings.startHour, settings.startMinute)
        val end = LocalTime.of(settings.endHour, settings.endMinute)
        
        return if (start.isAfter(end)) {
            now.isAfter(start) || now.isBefore(end)
        } else {
            now.isAfter(start) && now.isBefore(end)
        }
    }
    
    private fun stopMonitoring() {
        Log.d(TAG, "Stopping sleep monitoring service")
        
        monitoringJob?.cancel()
        sensorProvider.stop()
        settingsController.restoreSettings()
        cameraVerificationService.shutdown()
        
        // Notify UI that monitoring has stopped
        ScoreRepository.setMonitoring(false)
        
        // have to get wake up time to record properly
        if (sleepConfirmed) {
            analyticsRepository.recordWakeUp()
        }

        // End analytics session
        lifecycleScope.launch {
            analyticsRepository.endSession()
        }
        
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Receiver not registered
            Log.e(TAG, "Error in stopping monitoring cycle", e)
        }
    }
    
    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sleep Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows sleep monitoring status"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SleepMonitorService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DriftOff")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
