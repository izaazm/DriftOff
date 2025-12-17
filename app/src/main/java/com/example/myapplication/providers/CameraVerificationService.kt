package com.example.myapplication.providers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.myapplication.data.model.SleepVerificationResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

/**
 * Camera-based sleep verification service
 */
class CameraVerificationService(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraVerification"
        private const val EYE_CLOSED_THRESHOLD = 0.3f  // Below = eyes closed
        private const val FRAMES_TO_ANALYZE = 10
        private const val FRAME_DELAY_MS = 500L
    }
    
    private val faceDetector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var isVerifying = false
    
    /**
     * Check permission
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Verify if user is sleeping by analyzing eye open probability.
     * I guess?
     */
    @androidx.camera.core.ExperimentalGetImage
    suspend fun verifySleepState(
        durationSeconds: Int,
        lifecycleOwner: LifecycleOwner
    ): SleepVerificationResult? {
        if (!hasPermission()) {
            Log.w(TAG, "CAMERA permission not granted")
            return null
        }

        if (isVerifying) {
            return SleepVerificationResult(
                isSleeping = false,
                confidence = 0f,
                eyeOpenProbability = 1f,
                faceDetected = false
            )
        }
        
        isVerifying = true
        
        try {
            // Calculate number of frames to analyze
            val framesToAnalyze = minOf(
                FRAMES_TO_ANALYZE,
                (durationSeconds * 1000 / FRAME_DELAY_MS).toInt()
            )
            
            val eyeOpenResults = mutableListOf<Float>()
            var facesDetected = 0
            
            // Get camera provider
            val provider = getCameraProvider()
            
            // Configure image analysis
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            
            // Bind camera
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                imageAnalysis
            )
            
            // Analyze frames
            repeat(framesToAnalyze) { frameIndex ->
                try {
                    val result = analyzeFrame(imageAnalysis)
                    if (result != null) {
                        eyeOpenResults.add(result)
                        facesDetected++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error analyzing frame $frameIndex", e)
                }
                delay(FRAME_DELAY_MS)
            }
            
            // Unbind camera
            provider.unbindAll()
            
            // Calculate results
            return if (eyeOpenResults.isNotEmpty()) {
                val avgEyeOpen = eyeOpenResults.average().toFloat()
                val isSleeping = avgEyeOpen < EYE_CLOSED_THRESHOLD
                val confidence = if (isSleeping) {
                    1f - (avgEyeOpen / EYE_CLOSED_THRESHOLD)
                } else {
                    (avgEyeOpen - EYE_CLOSED_THRESHOLD) / (1f - EYE_CLOSED_THRESHOLD)
                }
                
                SleepVerificationResult(
                    isSleeping = isSleeping,
                    confidence = confidence.coerceIn(0f, 1f),
                    eyeOpenProbability = avgEyeOpen,
                    faceDetected = true
                )
            } else {
                // No face detected -> User not looking at phone -> Assume sleeping if high drowsiness score for a long time
                Log.d(TAG, "No face detected - assuming user is asleep")
                SleepVerificationResult(
                    isSleeping = true,
                    confidence = 0.7f,
                    eyeOpenProbability = 0f,
                    faceDetected = false
                )
            }
        } finally {
            isVerifying = false
        }
    }
    
    /**
     * Analyze a single frame for eye open probability.
     */
    @androidx.camera.core.ExperimentalGetImage
    private suspend fun analyzeFrame(imageAnalysis: ImageAnalysis): Float? {
        return suspendCancellableCoroutine { continuation ->
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImage(imageProxy) { faces ->
                    imageAnalysis.clearAnalyzer()
                    
                    if (faces.isNotEmpty()) {
                        val face = faces.first()
                        val leftEyeOpen = face.leftEyeOpenProbability ?: 0.5f
                        val rightEyeOpen = face.rightEyeOpenProbability ?: 0.5f
                        val avgEyeOpen = (leftEyeOpen + rightEyeOpen) / 2f
                        
                        if (continuation.isActive) {
                            continuation.resume(avgEyeOpen)
                        }
                    } else {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Process an image with ML Kit face detection.
     */
    @androidx.camera.core.ExperimentalGetImage
    private fun processImage(imageProxy: ImageProxy, onResult: (List<Face>) -> Unit) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    onResult(faces)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Face detection failed", e)
                    onResult(emptyList())
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
            onResult(emptyList())
        }
    }
    
    /**
     * Get or create camera provider.
     */
    private suspend fun getCameraProvider(): ProcessCameraProvider {
        return suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    cameraProvider = future.get()
                    continuation.resume(cameraProvider!!)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        faceDetector.close()
    }
}
