package com.example.myapplication.service

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log

/**
 * Controls phone settings (brightness, volume, DND) based on drowsiness state.
 */
class PhoneSettingsController(private val context: Context) {
    
    companion object {
        private const val TAG = "PhoneSettingsController"
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    // Store original settings for restoration
    private var originalBrightness: Int? = null
    private var originalBrightnessMode: Int? = null
    private var originalMediaVolume: Int? = null
    private var originalRingVolume: Int? = null
    private var originalNotificationVolume: Int? = null
    private var originalDndState: Int? = null
    
    /**
     * Check permissions
     */
    fun canModifySettings(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun canModifyNotificationPolicy(): Boolean {
        return notificationManager.isNotificationPolicyAccessGranted
    }

    fun saveCurrentSettings() {
        try {
            originalBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            originalBrightnessMode = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.w(TAG, "Could not read brightness settings", e)
        }
        
        originalMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        originalRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        originalNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        
        try {
            originalDndState = notificationManager.currentInterruptionFilter
        } catch (e: Exception) {
            Log.w(TAG, "Could not read DND state", e)
        }
    }
    
    /**
     * Apply gentle brightness reduction
     */
    fun applyGentleAdjustments(targetBrightness: Float = 0.5f, maxBrightness: Int = 255) {
        if (!canModifySettings()) {
            Log.w(TAG, "Cannot modify settings - permission not granted")
            return
        }
        
        try {
            // Disable auto-brightness
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            
            // Reduce brightness gently
            val brightnessValue = (targetBrightness * maxBrightness).toInt().coerceIn(10, maxBrightness)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightnessValue
            )
            
            Log.d(TAG, "Gentle adjustment applied: brightness=$brightnessValue (${(targetBrightness * 100).toInt()}%)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply gentle adjustments", e)
        }
    }
    
    /**
     * Apply full sleep mode settings.
     */
    fun applyFullSleepMode(
        targetBrightness: Float = 0.1f,
        targetVolume: Float = 0.0f,
        enableDnd: Boolean = true,
        maxBrightness: Int = 255
    ) {
        applyBrightness(targetBrightness, maxBrightness)
        applyVolume(targetVolume)
        if (enableDnd) {
            enableDoNotDisturb()
        }
    }
    
    /**
     * Set screen brightness.
     */
    fun applyBrightness(level: Float, maxBrightness: Int = 255) {
        if (!canModifySettings()) {
            Log.w(TAG, "Cannot modify brightness - permission not granted")
            return
        }
        
        try {
            // Disable auto-brightness
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            
            // Set brightness (minimum 10 to keep screen visible)
            val brightnessValue = (level * maxBrightness).toInt().coerceIn(10, maxBrightness)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightnessValue
            )
            
            Log.d(TAG, "Brightness set to $brightnessValue (max: $maxBrightness)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set brightness", e)
        }
    }
    
    /**
     * Set media volume.
     */
    fun applyVolume(level: Float) {
        try {
            val maxMedia = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            val maxNotification = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            
            val mediaLevel = (level * maxMedia).toInt()
            val ringLevel = (level * maxRing).toInt()
            val notificationLevel = (level * maxNotification).toInt()
            
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mediaLevel, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, ringLevel, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notificationLevel, 0)
            
            Log.d(TAG, "Volume set to level $level")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume", e)
        }
    }
    
    /**
     * Enable Do Not Disturb mode.
     */
    fun enableDoNotDisturb() {
        if (!canModifyNotificationPolicy()) {
            Log.w(TAG, "Cannot modify DND - permission not granted")
            return
        }
        
        try {
            notificationManager.setInterruptionFilter(
                NotificationManager.INTERRUPTION_FILTER_ALARMS
            )
            Log.d(TAG, "DND enabled (alarms only)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable DND", e)
        }
    }
    
    /**
     * Restore all settings to their original values.
     */
    fun restoreSettings() {
        // Restore brightness
        if (canModifySettings()) {
            try {
                originalBrightnessMode?.let {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        it
                    )
                }
                originalBrightness?.let {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        it
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore brightness", e)
            }
        }
        
        // Restore volume
        try {
            originalMediaVolume?.let {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
            }
            originalRingVolume?.let {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, it, 0)
            }
            originalNotificationVolume?.let {
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, it, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore volume", e)
        }
        
        // Restore DND
        if (canModifyNotificationPolicy()) {
            try {
                originalDndState?.let {
                    notificationManager.setInterruptionFilter(it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore DND", e)
            }
        }
        
        Log.d(TAG, "Settings restored")
    }
}
