package com.aican.biometricattendance.ml.camera

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.aican.biometricattendance.data.interfaces.LandmarkerListener
import com.aican.biometricattendance.data.models.camera.FaceBox
import com.aican.biometricattendance.data.models.camera.enums.SimpleFaceStatus
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus
import com.aican.biometricattendance.data.models.facelandmark.ResultBundle
import com.aican.biometricattendance.ml.facelandmark.FaceLandmarkerHelper
import com.aican.biometricattendance.presentation.screens.camera.components.CoordinateDebugger
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * Unified analyzer for both REGISTRATION and ATTENDANCE.
 * - When enableLiveness=false  → behaves like the original Simple analyzer.
 * - When enableLiveness=true   → exposes liveness-like getters & slightly stricter gating.
 */
class SimpleFaceAnalyzer(
    private val context: Context,
    private val previewWidth: Float,
    private val previewHeight: Float,
    private val isMirrored: Boolean = false,
    private val enableLiveness: Boolean = false,
    private val onFacesDetected: (List<FaceBox>, SimpleFaceStatus) -> Unit,
) : ImageAnalysis.Analyzer {

    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private var frameCount = 0
    private var isInitialized = false

    private var currentFaceStatus = SimpleFaceStatus.NO_FACE
    private var currentLivenessStatus: LivenessStatus = LivenessStatus.NO_FACE

    private val qualityHistory = ArrayDeque<Float>()
    private val QUALITY_HISTORY_MAX = 8

    companion object {
        private const val TAG = "SimpleFaceAnalyzer"
        private const val PROCESS_EVERY_N_FRAMES = 3 // Process every 3rd frame

        // Simple quality thresholds
        private const val MIN_FACE_SIZE_RATIO = 0.08f // Minimum face size relative to image
        private const val MAX_FACE_SIZE_RATIO = 0.7f  // Maximum face size relative to image
        private const val MIN_FACE_AREA_PIXELS = 5000f // Minimum face area in pixels
    }

    init { setupFaceLandmarker() }

    private fun setupFaceLandmarker() {
        try {
            faceLandmarkerHelper = FaceLandmarkerHelper(
                context = context,
                runningMode = RunningMode.LIVE_STREAM,
                minFaceDetectionConfidence = 0.6f,
                minFaceTrackingConfidence = 0.5f,
                minFacePresenceConfidence = 0.5f,
                maxNumFaces = 1,
                currentDelegate = FaceLandmarkerHelper.DELEGATE_CPU,
                faceLandmarkerHelperListener = object : LandmarkerListener {
                    override fun onError(error: String, errorCode: Int) {
                        Log.e(TAG, "FaceLandmarker error: $error")
                        reportNoFace()
                    }

                    override fun onResults(resultBundle: ResultBundle) {
                        processResults(resultBundle)
                    }
                }
            )
            isInitialized = true
            Log.d(TAG, "Unified SimpleFaceAnalyzer initialized (enableLiveness=$enableLiveness)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FaceLandmarker", e)
            isInitialized = false
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isInitialized) {
            imageProxy.close(); return
        }

        frameCount++
        if (frameCount % PROCESS_EVERY_N_FRAMES != 0) {
            imageProxy.close(); return
        }

        try {
            faceLandmarkerHelper?.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = isMirrored
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in face analysis", e)
            imageProxy.close()
            reportNoFace()
        }
    }

    private fun processResults(resultBundle: ResultBundle) {
        // Debug: input + preview
        CoordinateDebugger.logImageInfo(resultBundle)
        CoordinateDebugger.logPreviewInfo(previewWidth, previewHeight)

        val result = resultBundle.results.firstOrNull()
        if (result == null || result.faceLandmarks().isEmpty()) {
            reportNoFace()
            return
        }

        val landmarks = result.faceLandmarks()[0]
        CoordinateDebugger.logFaceDetection(landmarks, resultBundle)

        val faceQuality = calculateBasicQuality(landmarks, resultBundle)
        val faceStatus = determineFaceStatus(faceQuality)

        // Track quality (rolling average)
        qualityHistory.addLast(faceQuality)
        if (qualityHistory.size > QUALITY_HISTORY_MAX) qualityHistory.removeFirst()

        // Map to liveness style (shim)
        currentLivenessStatus = when {
            !enableLiveness -> {
                when (faceStatus) {
                    SimpleFaceStatus.FACE_DETECTED -> LivenessStatus.LIVE_FACE
                    SimpleFaceStatus.POOR_QUALITY -> LivenessStatus.POOR_QUALITY
                    SimpleFaceStatus.NO_FACE      -> LivenessStatus.NO_FACE
                }
            }
            else -> {
                when (faceStatus) {
                    SimpleFaceStatus.FACE_DETECTED ->
                        if (avgQuality() >= 0.6f) LivenessStatus.LIVE_FACE else LivenessStatus.CHECKING
                    SimpleFaceStatus.POOR_QUALITY -> LivenessStatus.POOR_QUALITY
                    SimpleFaceStatus.NO_FACE      -> LivenessStatus.NO_FACE
                }
            }
        }

        currentFaceStatus = faceStatus

        val faceBoxes = if (faceStatus != SimpleFaceStatus.NO_FACE) {
            val boxes = convertLandmarksToFaceBoxes(result, resultBundle)
            if (boxes.isNotEmpty()) {
                CoordinateDebugger.logFaceBoxConversion(
                    landmarks, resultBundle, previewWidth, previewHeight, boxes.first()
                )
                CoordinateDebugger.compareOvalVsFaceBox(previewWidth, previewHeight, boxes.first())
            }
            boxes
        } else emptyList()

        Log.d(TAG, "Face Status: $faceStatus, Quality: ${String.format("%.2f", faceQuality)}, Liveness: $currentLivenessStatus")
        onFacesDetected(faceBoxes, faceStatus)
    }

    private fun avgQuality(): Float =
        if (qualityHistory.isEmpty()) 0f else qualityHistory.average().toFloat()

    // --- ViewModel-facing API (matches what FaceAnalyzer previously exposed) ---
    fun getCurrentLivenessStatus(): LivenessStatus = currentLivenessStatus
    fun getFaceQualityScore(): Float = avgQuality()
    fun isFaceSuitableForCapture(): Boolean {
        return if (!enableLiveness) {
            currentLivenessStatus == LivenessStatus.LIVE_FACE
        } else {
            currentLivenessStatus == LivenessStatus.LIVE_FACE && avgQuality() >= 0.6f
        }
    }
    // ---------------------------------------------------------------------------

    private fun calculateBasicQuality(
        landmarks: List<NormalizedLandmark>,
        resultBundle: ResultBundle,
    ): Float {
        val minX = landmarks.minOf { it.x() }
        val maxX = landmarks.maxOf { it.x() }
        val minY = landmarks.minOf { it.y() }
        val maxY = landmarks.maxOf { it.y() }

        val faceWidthPixels = (maxX - minX) * resultBundle.inputImageWidth
        val faceHeightPixels = (maxY - minY) * resultBundle.inputImageHeight
        val faceAreaPixels = faceWidthPixels * faceHeightPixels

        val imageArea = resultBundle.inputImageWidth * resultBundle.inputImageHeight
        val faceSizeRatio = faceAreaPixels / imageArea

        var qualityScore = 1.0f

        when {
            faceSizeRatio < MIN_FACE_SIZE_RATIO -> qualityScore *= 0.3f
            faceSizeRatio > MAX_FACE_SIZE_RATIO -> qualityScore *= 0.4f
            faceAreaPixels < MIN_FACE_AREA_PIXELS -> qualityScore *= 0.5f
        }

        val centerX = landmarks.map { it.x() }.average().toFloat()
        val centerY = landmarks.map { it.y() }.average().toFloat()
        val distanceFromCenter = kotlin.math.sqrt(
            ((centerX - 0.5f) * (centerX - 0.5f) + (centerY - 0.5f) * (centerY - 0.5f)).toDouble()
        ).toFloat()

        if (distanceFromCenter > 0.3f) qualityScore *= 0.8f

        return qualityScore.coerceIn(0f, 1f)
    }

    private fun determineFaceStatus(quality: Float): SimpleFaceStatus {
        return if (quality < 0.4f) SimpleFaceStatus.POOR_QUALITY else SimpleFaceStatus.FACE_DETECTED
    }

    private fun convertLandmarksToFaceBoxes(
        result: FaceLandmarkerResult,
        resultBundle: ResultBundle,
    ): List<FaceBox> {
        val faceBoxes = mutableListOf<FaceBox>()
        val scaleX = previewWidth / resultBundle.inputImageWidth
        val scaleY = previewHeight / resultBundle.inputImageHeight

        result.faceLandmarks().forEach { landmarks ->
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            landmarks.forEach { landmark ->
                val x = landmark.x() * resultBundle.inputImageWidth * scaleX
                val y = landmark.y() * resultBundle.inputImageHeight * scaleY
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }

            val padding = 0.15f
            val width = maxX - minX
            val height = maxY - minY
            val padX = width * padding
            val padY = height * padding

            val faceBox = FaceBox(
                left = (minX - padX).coerceAtLeast(0f),
                top = (minY - padY).coerceAtLeast(0f),
                right = (maxX + padX).coerceAtMost(previewWidth),
                bottom = (maxY + padY).coerceAtMost(previewHeight)
            )

            faceBoxes.add(faceBox)
        }

        return faceBoxes
    }

    private fun reportNoFace() {
        currentFaceStatus = SimpleFaceStatus.NO_FACE
        currentLivenessStatus = LivenessStatus.NO_FACE
        onFacesDetected(emptyList(), SimpleFaceStatus.NO_FACE)
    }

    fun getCurrentFaceStatus(): SimpleFaceStatus = currentFaceStatus

    fun close() {
        faceLandmarkerHelper?.clearFaceLandmarker()
        faceLandmarkerHelper = null
        isInitialized = false
        qualityHistory.clear()
        Log.d(TAG, "SimpleFaceAnalyzer closed")
    }
}
