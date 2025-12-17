package com.example.myapplication.data.repository

import com.example.myapplication.data.model.DrowsinessState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton repository for score between backend and UI.
 */
object ScoreRepository {
    
    private val _currentScore = MutableStateFlow(0f)
    val currentScore: StateFlow<Float> = _currentScore.asStateFlow()
    
    private val _currentState = MutableStateFlow(DrowsinessState.AWAKE)
    val currentState: StateFlow<DrowsinessState> = _currentState.asStateFlow()
    
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    fun updateScore(score: Float, state: DrowsinessState) {
        _currentScore.value = score
        _currentState.value = state
    }

    fun setMonitoring(active: Boolean) {
        _isMonitoring.value = active
        if (!active) {
            // Reset score when monitoring stops
            _currentScore.value = 0f
            _currentState.value = DrowsinessState.AWAKE
        }
    }
}
