package com.example.myapplication.providers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.log10

/**
 * Provider for sampling ambient audio
 */
class AudioSampleProvider(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioSampleProvider"
        
        // Audio configuration
        private const val SAMPLE_RATE = 8000  // Very2 low quality
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val SAMPLE_DURATION_MS = 500  // 0.5 s sample
    }
    
    private var audioRecord: AudioRecord? = null
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    
    /**
     * Check permission
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Take a brief audio sample and return the ambient noise level in decibels.
     */
    fun sampleAmbientLevel(): Float? {
        if (!hasPermission()) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            return null
        }
        
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return null
        }
        
        return try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return null
            }
            
            audioRecord?.startRecording()
            
            // Read samples for SAMPLE_DURATION_MS
            val sampleCount = (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000)
            val buffer = ShortArray(sampleCount)
            val readCount = audioRecord?.read(buffer, 0, sampleCount) ?: 0
            
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            if (readCount <= 0) {
                Log.w(TAG, "No audio samples read")
                return null
            }
            
            // Calculate RMS (root mean square) amplitude
            var sumSquares = 0.0
            for (i in 0 until readCount) {
                sumSquares += buffer[i].toDouble() * buffer[i].toDouble()
            }
            val rms = kotlin.math.sqrt(sumSquares / readCount)
            
            // Convert to decibels (dB SPL approximation)
            // Reference: 16-bit audio max value is 32767
            val db = if (rms > 0) {
                20 * log10(rms / 32767.0) + 90  // Approximate dB SPL
            } else {
                0.0
            }
            
            val dbFloat = db.toFloat().coerceIn(0f, 120f)
            Log.d(TAG, "Ambient noise level: %.1f dB".format(dbFloat))
            
            dbFloat
            
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while recording", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error sampling audio", e)
            audioRecord?.release()
            audioRecord = null
            null
        }
    }
}
