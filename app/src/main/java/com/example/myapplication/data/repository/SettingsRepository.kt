package com.example.myapplication.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.data.model.SleepSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sleep_settings")

/**
 * Setting repository w/ DataStore
 */
class SettingsRepository(private val context: Context) {
    
    private object Keys {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val START_HOUR = intPreferencesKey("start_hour")
        val START_MINUTE = intPreferencesKey("start_minute")
        val END_HOUR = intPreferencesKey("end_hour")
        val END_MINUTE = intPreferencesKey("end_minute")
        val ADJUST_BRIGHTNESS = booleanPreferencesKey("adjust_brightness")
        val TARGET_BRIGHTNESS = floatPreferencesKey("target_brightness")
        val MAX_BRIGHTNESS = intPreferencesKey("max_brightness")
        val ADJUST_VOLUME = booleanPreferencesKey("adjust_volume")
        val TARGET_VOLUME = floatPreferencesKey("target_volume")
        val ENABLE_DND = booleanPreferencesKey("enable_dnd")
        val DROWSY_THRESHOLD = intPreferencesKey("drowsy_threshold")
        val SLEEPING_THRESHOLD = intPreferencesKey("sleeping_threshold")
        val ENABLE_CAMERA_VERIFICATION = booleanPreferencesKey("enable_camera_verification")
        val CAMERA_VERIFICATION_DURATION = intPreferencesKey("camera_verification_duration")
        val USE_AUDIO_SAMPLING = booleanPreferencesKey("use_audio_sampling")
    }

    /**
     * Flow of current settings
     */
    val settingsFlow: Flow<SleepSettings> = context.dataStore.data.map { prefs ->
        SleepSettings(
            isEnabled = prefs[Keys.IS_ENABLED] ?: false,
            startHour = prefs[Keys.START_HOUR] ?: 22,
            startMinute = prefs[Keys.START_MINUTE] ?: 0,
            endHour = prefs[Keys.END_HOUR] ?: 7,
            endMinute = prefs[Keys.END_MINUTE] ?: 0,
            adjustBrightness = prefs[Keys.ADJUST_BRIGHTNESS] ?: true,
            targetBrightness = prefs[Keys.TARGET_BRIGHTNESS] ?: 0.1f,
            maxBrightness = prefs[Keys.MAX_BRIGHTNESS] ?: 255,
            adjustVolume = prefs[Keys.ADJUST_VOLUME] ?: true,
            targetVolume = prefs[Keys.TARGET_VOLUME] ?: 0.0f,
            enableDnd = prefs[Keys.ENABLE_DND] ?: true,
            drowsyThreshold = prefs[Keys.DROWSY_THRESHOLD] ?: 50,
            sleepingThreshold = prefs[Keys.SLEEPING_THRESHOLD] ?: 70,
            enableCameraVerification = prefs[Keys.ENABLE_CAMERA_VERIFICATION] ?: true,
            cameraVerificationDuration = prefs[Keys.CAMERA_VERIFICATION_DURATION] ?: 10,
            useAudioSampling = prefs[Keys.USE_AUDIO_SAMPLING] ?: false
        )
    }
    
    /**
     * Update all settings at once.
     * Probably bad but easier this way
     */
    suspend fun updateSettings(settings: SleepSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_ENABLED] = settings.isEnabled
            prefs[Keys.START_HOUR] = settings.startHour
            prefs[Keys.START_MINUTE] = settings.startMinute
            prefs[Keys.END_HOUR] = settings.endHour
            prefs[Keys.END_MINUTE] = settings.endMinute
            prefs[Keys.ADJUST_BRIGHTNESS] = settings.adjustBrightness
            prefs[Keys.TARGET_BRIGHTNESS] = settings.targetBrightness
            prefs[Keys.MAX_BRIGHTNESS] = settings.maxBrightness
            prefs[Keys.ADJUST_VOLUME] = settings.adjustVolume
            prefs[Keys.TARGET_VOLUME] = settings.targetVolume
            prefs[Keys.ENABLE_DND] = settings.enableDnd
            prefs[Keys.DROWSY_THRESHOLD] = settings.drowsyThreshold
            prefs[Keys.SLEEPING_THRESHOLD] = settings.sleepingThreshold
            prefs[Keys.ENABLE_CAMERA_VERIFICATION] = settings.enableCameraVerification
            prefs[Keys.CAMERA_VERIFICATION_DURATION] = settings.cameraVerificationDuration
            prefs[Keys.USE_AUDIO_SAMPLING] = settings.useAudioSampling
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_ENABLED] = enabled
        }
    }
}
