package com.example.myapplication.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.data.model.UserFeedback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.feedbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_feedback")

/**
 * Repository for managing user feedback and adaptive multiplier
 */
class FeedbackRepository(private val context: Context) {
    
    private object Keys {
        val ADAPTIVE_MULTIPLIER = floatPreferencesKey("adaptive_multiplier")
        val LAST_FEEDBACK_DATE = stringPreferencesKey("last_feedback_date")
        val LAST_FEEDBACK_RATING = intPreferencesKey("last_feedback_rating")
        val LAST_FEEDBACK_FEELING = stringPreferencesKey("last_feedback_feeling")
        val FEEDBACK_COUNT = intPreferencesKey("feedback_count")
    }
    
    companion object {
        const val DEFAULT_MULTIPLIER = 1.0f
        const val MIN_MULTIPLIER = 0.5f
        const val MAX_MULTIPLIER = 1.5f
    }

    val adaptiveMultiplierFlow: Flow<Float> = context.feedbackDataStore.data.map { prefs ->
        prefs[Keys.ADAPTIVE_MULTIPLIER] ?: DEFAULT_MULTIPLIER
    }

    suspend fun getAdaptiveMultiplier(): Float {
        return context.feedbackDataStore.data.first()[Keys.ADAPTIVE_MULTIPLIER] ?: DEFAULT_MULTIPLIER
    }
    
    /**
     * Check if feedback has already been given today
     */
    suspend fun hasFeedbackToday(): Boolean {
        val lastDate = context.feedbackDataStore.data.first()[Keys.LAST_FEEDBACK_DATE]
        return lastDate == LocalDate.now().toString()
    }
    
    /**
     * Submit user feedback and adjust the adaptive multiplier
     */
    suspend fun submitFeedback(feedback: UserFeedback) {
        context.feedbackDataStore.edit { prefs ->
            val currentMultiplier = prefs[Keys.ADAPTIVE_MULTIPLIER] ?: DEFAULT_MULTIPLIER
            
            val adjustment = when (feedback.sleepQualityRating) {
                1 -> -0.15f
                2 -> -0.08f
                3 -> 0f
                4 -> 0.05f
                5 -> 0.10f
                else -> 0f
            }
            
            val newMultiplier = (currentMultiplier + adjustment).coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
            
            prefs[Keys.ADAPTIVE_MULTIPLIER] = newMultiplier
            prefs[Keys.LAST_FEEDBACK_DATE] = feedback.date.toString()
            prefs[Keys.LAST_FEEDBACK_RATING] = feedback.sleepQualityRating
            prefs[Keys.LAST_FEEDBACK_FEELING] = feedback.wakeUpFeeling.name
            prefs[Keys.FEEDBACK_COUNT] = (prefs[Keys.FEEDBACK_COUNT] ?: 0) + 1
        }
    }
    
    /**
     * Reset the adaptive multiplier
     */
    suspend fun resetMultiplier() {
        context.feedbackDataStore.edit { prefs ->
            prefs[Keys.ADAPTIVE_MULTIPLIER] = DEFAULT_MULTIPLIER
        }
    }
}
