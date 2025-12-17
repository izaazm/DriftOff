package com.example.myapplication.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.DrowsinessState
import com.example.myapplication.data.model.SleepAnalyticsSummary
import com.example.myapplication.data.model.SleepSession
import com.example.myapplication.data.model.SleepSettings
import com.example.myapplication.data.model.SleepTrend
import com.example.myapplication.data.model.UserFeedback
import com.example.myapplication.data.model.WakeUpFeeling
import com.example.myapplication.data.repository.FeedbackRepository
import com.example.myapplication.data.repository.ScoreRepository
import com.example.myapplication.data.repository.SettingsRepository
import com.example.myapplication.data.repository.SleepAnalyticsRepository
import com.example.myapplication.service.SleepMonitorService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ViewModel for the Sleep Helper app.
 */
@androidx.camera.core.ExperimentalGetImage
class SleepViewModel(application: Application) : AndroidViewModel(application) {
    
    private val settingsRepository = SettingsRepository(application)
    private val feedbackRepository = FeedbackRepository(application)
    private val analyticsRepository = SleepAnalyticsRepository(application)
    
    // Settings state
    val settings: StateFlow<SleepSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SleepSettings()
        )
    
    // Adaptive multiplier
    val adaptiveMultiplier: StateFlow<Float> = feedbackRepository.adaptiveMultiplierFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 1.0f
        )
    
    // Real-time score from ScoreRepository
    val currentScore: StateFlow<Float> = ScoreRepository.currentScore
    val currentState: StateFlow<DrowsinessState> = ScoreRepository.currentState
    val isMonitoring: StateFlow<Boolean> = ScoreRepository.isMonitoring
    
    // Show feedback dialog
    private val _showFeedbackDialog = MutableStateFlow(false)
    val showFeedbackDialog: StateFlow<Boolean> = _showFeedbackDialog.asStateFlow()
    
    // Analytics data
    private val _analyticsSummary = MutableStateFlow(
        SleepAnalyticsSummary(
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
    )
    val analyticsSummary: StateFlow<SleepAnalyticsSummary> = _analyticsSummary.asStateFlow()
    
    private val _recentSessions = MutableStateFlow<List<SleepSession>>(emptyList())
    val recentSessions: StateFlow<List<SleepSession>> = _recentSessions.asStateFlow()

    init {
        // Check if we should show feedback dialog
        viewModelScope.launch {
            val hasFeedback = feedbackRepository.hasFeedbackToday()
            val now = java.time.LocalTime.now()
            if (!hasFeedback && now.hour in 6..12) {
                _showFeedbackDialog.value = true
            }
        }

        // Load initial analytics
        loadAnalytics()
    }
    
    /**
     * Load analytics data.
     */
    fun loadAnalytics() {
        viewModelScope.launch {
            _analyticsSummary.value = analyticsRepository.getAnalyticsSummary(7)
            _recentSessions.value = analyticsRepository.getRecentSessions(7)
        }
    }
    
    /**
     * Update settings.
     */
    fun updateSettings(settings: SleepSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings)
        }
    }
    
    /**
     * Toggle service enabled data
     */
    fun toggleService() {
        viewModelScope.launch {
            val newEnabled = !settings.value.isEnabled
            settingsRepository.setEnabled(newEnabled)
            
            val context = getApplication<Application>()
            val intent = Intent(context, SleepMonitorService::class.java).apply {
                action = if (newEnabled) {
                    SleepMonitorService.ACTION_START
                } else {
                    SleepMonitorService.ACTION_STOP
                }
            }
            
            if (newEnabled) {
                context.startForegroundService(intent)
                ScoreRepository.setMonitoring(true)
            } else {
                context.stopService(intent)
                ScoreRepository.setMonitoring(false)
            }
        }
    }
    
    /**
     * Submit morning feedback.
     */
    fun submitFeedback(rating: Int, feeling: WakeUpFeeling, notes: String? = null) {
        viewModelScope.launch {
            feedbackRepository.submitFeedback(
                UserFeedback(
                    date = LocalDate.now(),
                    sleepQualityRating = rating,
                    wakeUpFeeling = feeling,
                    notes = notes
                )
            )
            _showFeedbackDialog.value = false
        }
    }

    fun dismissFeedbackDialog() {
        _showFeedbackDialog.value = false
    }

    fun showFeedback() {
        _showFeedbackDialog.value = true
    }

    fun resetMultiplier() {
        viewModelScope.launch {
            feedbackRepository.resetMultiplier()
        }
    }
}
