package com.aican.biometricattendance.ml.camera

import android.content.Context // Android context
import android.util.Log // For logging messages
import androidx.annotation.OptIn // Annotation for opting into experimental APIs
import androidx.camera.core.ExperimentalGetImage // Opt-in for getImage() on ImageProxy
import androidx.camera.core.ImageAnalysis // CameraX use case for image analysis
import androidx.camera.core.ImageProxy // Represents an image from CameraX
import com.aican.biometricattendance.data.interfaces.LandmarkerListener // Interface for MediaPipe landmarker results
import com.aican.biometricattendance.data.models.camera.FaceBoundary // Custom data class for face boundary info
import com.aican.biometricattendance.data.models.camera.FaceBox // Custom data class for face bounding box
import com.aican.biometricattendance.data.models.camera.FacePosition // Custom data class for face position info
import com.aican.biometricattendance.data.models.camera.LandmarkFrame // Custom data class to store landmarks with timestamp
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus // Enum for liveness status
import com.aican.biometricattendance.data.models.facelandmark.ResultBundle // Custom data class to bundle MediaPipe results
import com.aican.biometricattendance.ml.facelandmark.FaceLandmarkerHelper // Helper class for MediaPipe FaceLandmarker
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark // MediaPipe landmark representation
import com.google.mediapipe.tasks.vision.core.RunningMode // MediaPipe running mode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult // MediaPipe FaceLandmarker result
import kotlinx.coroutines.CoroutineScope // Kotlin Coroutine Scope
import kotlinx.coroutines.Dispatchers // Coroutine Dispatchers
import kotlinx.coroutines.Job // Coroutine Job
import kotlinx.coroutines.delay // Suspends a coroutine for a given time
import kotlinx.coroutines.launch // Launches a new coroutine

/**
 * Custom ImageAnalysis.Analyzer that integrates MediaPipe's FaceLandmarker to detect faces,
 * analyze liveness, quality, and position, and provide feedback.
 *
 * @param context The application context.
 * @param previewWidth The width of the camera preview surface.
 * @param previewHeight The height of the camera preview surface.
 * @param isMirrored Indicates if the camera preview is mirrored (typically true for front camera).
 * @param onFacesDetected Callback lambda to report detected face boxes to the ViewModel.
 */
class FaceAnalyzer(
    private val context: Context,
    private val previewWidth: Float,
    private val previewHeight: Float,
    private val isMirrored: Boolean = false,
    private val onFacesDetected: (List<FaceBox>) -> Unit
) : ImageAnalysis.Analyzer {

    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null // Helper for MediaPipe FaceLandmarker
    private var frameCount = 0 // Counter for analyzed frames
    private var isInitialized = false // Flag indicating if MediaPipe helper is initialized

    // --- Liveness Stability System ---
    // These variables help in smoothing out liveness status changes to avoid flickering.
    private var currentLivenessStatus = LivenessStatus.NO_FACE // The currently reported stable liveness status
    private var stableStatusCount = 0 // Counter for how many consecutive frames a status has been observed
    private var lastStableStatus = LivenessStatus.NO_FACE // The status that is currently being counted for stability
    private var liveFrameCount = 0 // Counter for consecutive LIVE_FACE frames

    // --- History Tracking for Analysis ---
    private var landmarkHistory = mutableListOf<LandmarkFrame>() // History of detected face landmarks
    private var faceQualityHistory = mutableListOf<Float>() // History of face quality scores
    private var positionHistory = mutableListOf<FacePosition>() // History of face positions
    private var boundaryHistory = mutableListOf<Boolean>() // New: History of whether the face was completely in frame

    private var stabilityJob: Job? = null // Job for handling stability timeouts
    private val coroutineScope = CoroutineScope(Dispatchers.Default) // Coroutine scope for background tasks

    companion object {
        private const val TAG = "FaceAnalyzer"
        private const val PROCESS_EVERY_N_FRAMES = 2 // Process every 2nd frame to save CPU
        private const val LANDMARK_HISTORY_SIZE = 6 // Number of frames to keep in landmark history
        private const val QUALITY_HISTORY_SIZE = 8 // Number of frames to keep in quality history
        private const val POSITION_HISTORY_SIZE = 5 // Number of frames to keep in position history
        private const val BOUNDARY_HISTORY_SIZE = 6 // Number of frames to keep in boundary history

        // BALANCED thresholds for status transitions
        private const val STABLE_FRAMES_REQUIRED = 4 // How many consistent frames for a status to be considered "stable"
        private const val LIVE_FRAMES_REQUIRED = 8 // How many consistent frames for LIVE_FACE status
        private const val MOVEMENT_THRESHOLD_LOW = 0.0003f // Minimum movement for liveness
        private const val MOVEMENT_THRESHOLD_HIGH = 0.004f // Maximum movement to avoid too much motion
        private const val QUALITY_THRESHOLD = 0.6f // Minimum face quality score
        private const val STABILITY_TIMEOUT_MS = 2000L // Timeout for CHECKING status to force a decision

        // More lenient position validation parameters
        private const val MIN_FACE_SIZE_RATIO = 0.05f // Minimum acceptable face size relative to image
        private const val MAX_FACE_SIZE_RATIO = 0.8f // Maximum acceptable face size relative to image
        private const val CENTER_TOLERANCE = 0.35f // Tolerance for face being off-center
        private const val POSITION_STABILITY_FRAMES = 3 // Frames needed for position stability (not explicitly used in updateLivenessStatusBalanced)

        // NEW: Boundary validation parameters
        private const val BOUNDARY_MARGIN = 0.05f // 5% margin from screen edges for face to stay within
        private const val FACE_COMPLETENESS_THRESHOLD = 0.95f // 95% of face must be visible
        private const val BOUNDARY_STABILITY_FRAMES = 4 // Face must be within bounds for this many frames consistently
    }

    init {
        setupMediaPipeFaceLandmarker() // Initialize the MediaPipe helper when Analyzer is created
    }

    /**
     * Sets up and initializes the MediaPipe FaceLandmarkerHelper.
     * Configures the running mode, detection confidence, and a listener for results.
     */
    private fun setupMediaPipeFaceLandmarker() {
        try {
            faceLandmarkerHelper = FaceLandmarkerHelper(
                context = context,
                runningMode = RunningMode.LIVE_STREAM, // Optimized for real-time video streams
                minFaceDetectionConfidence = 0.5f,
                minFaceTrackingConfidence = 0.5f,
                minFacePresenceConfidence = 0.5f,
                maxNumFaces = 1, // Only detect one face
                currentDelegate = FaceLandmarkerHelper.DELEGATE_CPU, // Use CPU delegate (GPU can be an option)
                faceLandmarkerHelperListener = object : LandmarkerListener {
                    override fun onError(error: String, errorCode: Int) {
                        Log.e(TAG, "MediaPipe FaceLandmarker error: $error")
                        resetToNoFace() // Reset state on error
                        onFacesDetected(emptyList()) // Report no faces
                    }

                    override fun onResults(resultBundle: ResultBundle) {
                        processMediaPipeResults(resultBundle) // Process detection results
                    }
                }
            )
            isInitialized = true // Mark as initialized
            Log.d(TAG, "MediaPipe FaceLandmarker initialized with boundary validation")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe FaceLandmarker", e)
            isInitialized = false // Mark as not initialized on error
        }
    }

    /**
     * Implements the `analyze` method from `ImageAnalysis.Analyzer`.
     * This method is called by CameraX for each incoming camera frame.
     * @param imageProxy The `ImageProxy` containing the camera frame data.
     */
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isInitialized) {
            imageProxy.close() // Close the image if not initialized
            return
        }

        frameCount++ // Increment frame counter

        // Process only every Nth frame to reduce CPU load.
        if (frameCount % PROCESS_EVERY_N_FRAMES != 0) {
            imageProxy.close()
            return
        }

        try {
            // Pass the image to the MediaPipe helper for live stream detection.
            faceLandmarkerHelper?.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = isMirrored // Inform helper if front camera is used for mirroring
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in MediaPipe face analysis", e)
            imageProxy.close()
            resetToNoFace() // Reset state on error
            onFacesDetected(emptyList())
        }
    }

    /**
     * Processes the results received from MediaPipe's FaceLandmarker.
     * This is the core logic for analyzing face attributes and determining liveness status.
     * @param resultBundle The bundled results from the FaceLandmarkerHelper.
     */
    private fun processMediaPipeResults(resultBundle: ResultBundle) {
        val result = resultBundle.results.firstOrNull() // Get the first (and only) face result

        // If no face detected or landmarks are empty, reset to NO_FACE status.
        if (result == null || result.faceLandmarks().isEmpty()) {
            resetToNoFace()
            onFacesDetected(emptyList())
            return
        }

        val currentLandmarks = result.faceLandmarks()[0] // Get the landmarks for the first face

        // Calculate various metrics for the current frame
        val facePosition = calculateFacePosition(currentLandmarks, resultBundle)
        val faceQuality = calculateFaceQuality(result, resultBundle)
        val positionScore = validateFacePosition(facePosition)
        val faceBoundary = validateFaceBoundaries(currentLandmarks, resultBundle) // NEW: Boundary validation

        // Add current frame's data to histories for temporal analysis
        addToHistories(
            currentLandmarks,
            faceQuality,
            facePosition,
            faceBoundary.isCompletelyInFrame // NEW: Add boundary compliance to history
        )

        // Perform enhanced validation with all calculated metrics, including boundaries.
        val newStatus = performEnhancedValidationWithBoundaries(
            landmarks = currentLandmarks,
            quality = faceQuality,
            position = facePosition,
            positionScore = positionScore,
            faceBoundary = faceBoundary
        )

        // Update the overall liveness status, with stabilization logic.
        updateLivenessStatusBalanced(newStatus)

        // Determine whether to show face bounding boxes based on current status.
        val faceBoxes = if (shouldShowFaceBox()) {
            convertLandmarksToFaceBoxes(result, resultBundle)
        } else {
            emptyList()
        }

        // Enhanced logging for debugging and understanding status changes.
        Log.d(
            TAG, """
            Status: $currentLivenessStatus (${lastStableStatus})
            Quality: ${String.format("%.2f", faceQuality)} (avg: ${String.format("%.2f", faceQualityHistory.average())})
            Position: ${String.format("%.2f", positionScore)}
            Boundary: ${faceBoundary.isCompletelyInFrame} (visibility: ${String.format("%.2f", faceBoundary.visibilityRatio)})
            LiveFrames: $liveFrameCount/$LIVE_FRAMES_REQUIRED
            StableFrames: $stableStatusCount/$STABLE_FRAMES_REQUIRED
            BoundaryHistory: ${boundaryHistory.count { it }}/${boundaryHistory.size}
        """.trimIndent()
        )

        onFacesDetected(faceBoxes) // Report detected face boxes back to the ViewModel
    }

    /**
     * NEW: Validates if the detected face is completely within the defined screen boundaries,
     * including a margin, and calculates its visibility ratio.
     * This is crucial for ensuring the entire face is captured without being cut off.
     *
     * @param landmarks The list of normalized landmarks for the face.
     * @param resultBundle The result bundle containing image dimensions.
     * @return A `FaceBoundary` object with boundary compliance information.
     */
    private fun validateFaceBoundaries(
        landmarks: List<NormalizedLandmark>,
        resultBundle: ResultBundle
    ): FaceBoundary {
        val scaleX = previewWidth / resultBundle.inputImageWidth
        val scaleY = previewHeight / resultBundle.inputImageHeight

        // Calculate raw face bounds in preview coordinates based on landmarks
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        landmarks.forEach { landmark ->
            // Scale landmark coordinates from input image (e.g., 480x640) to preview dimensions (e.g., 1080x1624)
            var x = landmark.x() * resultBundle.inputImageWidth * scaleX
            val y = landmark.y() * resultBundle.inputImageHeight * scaleY

            // The FaceLandmarkerHelper already handles mirroring for the coordinates it returns.
            // If `isMirrored` was used to configure FaceLandmarkerHelper, the x-coordinates
            // will already be correctly oriented for the preview.
            // Therefore, no additional mirroring is needed here.
            /*
            if (isMirrored) {
                x = previewWidth - x // This would be if the raw coordinates were not mirrored by helper
            }
            */

            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
        }

        // Add padding around the landmark-defined face rectangle to account for
        // the full extent of the face and some breathing room for the capture.
        val faceWidth = maxX - minX
        val faceHeight = maxY - minY
        val paddingX = faceWidth * 0.15f // 15% padding on sides
        val paddingY = faceHeight * 0.2f  // 20% padding on top/bottom

        val actualMinX = minX - paddingX
        val actualMinY = minY - paddingY
        val actualMaxX = maxX + paddingX
        val actualMaxY = maxY + paddingY

        // Define screen boundaries with an additional margin
        // This margin ensures the face isn't too close to the absolute edges.
        val screenMargin = minOf(previewWidth, previewHeight) * BOUNDARY_MARGIN
        val leftBound = screenMargin
        val topBound = screenMargin
        val rightBound = previewWidth - screenMargin
        val bottomBound = previewHeight - screenMargin

        // Check if the padded face rectangle is completely within the screen bounds with margin.
        val isCompletelyInFrame = actualMinX >= leftBound &&
                actualMinY >= topBound &&
                actualMaxX <= rightBound &&
                actualMaxY <= bottomBound

        // Calculate the visibility ratio: how much of the padded face area is actually visible on screen.
        val totalFaceArea = (actualMaxX - actualMinX) * (actualMaxY - actualMinY)
        val visibleMinX = maxOf(actualMinX, 0f)
        val visibleMinY = maxOf(actualMinY, 0f)
        val visibleMaxX = minOf(actualMaxX, previewWidth)
        val visibleMaxY = minOf(actualMaxY, previewHeight)
        val visibleArea = maxOf(0f, (visibleMaxX - visibleMinX) * (visibleMaxY - visibleMinY))
        val visibilityRatio = if (totalFaceArea > 0) visibleArea / totalFaceArea else 0f

        Log.d(
            TAG, """
            Face bounds (padded preview coords): (${actualMinX.toInt()}, ${actualMinY.toInt()}) to (${actualMaxX.toInt()}, ${actualMaxY.toInt()})
            Screen bounds (with margin): (${leftBound.toInt()}, ${topBound.toInt()}) to (${rightBound.toInt()}, ${bottomBound.toInt()})
            Complete in frame: $isCompletelyInFrame
            Visibility ratio: ${String.format("%.2f", visibilityRatio)}
        """.trimIndent()
        )

        return FaceBoundary(
            minX = actualMinX,
            minY = actualMinY,
            maxX = actualMaxX,
            maxY = actualMaxY,
            isCompletelyInFrame = isCompletelyInFrame,
            visibilityRatio = visibilityRatio
        )
    }

    /**
     * Performs an enhanced validation of the face based on quality, position, movement, and
     * crucially, **boundary compliance**. This determines the overall `LivenessStatus`.
     *
     * @param landmarks The current face landmarks.
     * @param quality The current face quality score.
     * @param position The current face position information.
     * @param positionScore The score for face positioning.
     * @param faceBoundary The boundary validation result.
     * @return The determined `LivenessStatus`.
     */
    private fun performEnhancedValidationWithBoundaries(
        landmarks: List<NormalizedLandmark>,
        quality: Float,
        position: FacePosition,
        positionScore: Float,
        faceBoundary: FaceBoundary
    ): LivenessStatus {

        // Use average quality from history for more stability
        val avgQuality = if (faceQualityHistory.size >= 3) {
            faceQualityHistory.average().toFloat()
        } else {
            quality
        }

        Log.d(
            TAG,
            "Enhanced validation - Quality: $avgQuality, Position: $positionScore, Boundary: ${faceBoundary.isCompletelyInFrame}, Visibility: ${faceBoundary.visibilityRatio}"
        )

        // 1. PRIORITY: Check if face is completely in frame and sufficiently visible.
        // If not, immediately report FACE_TOO_CLOSE_TO_EDGE.
        if (!faceBoundary.isCompletelyInFrame || faceBoundary.visibilityRatio < FACE_COMPLETENESS_THRESHOLD) {
            Log.d(TAG, "Face not completely in frame - visibility: ${faceBoundary.visibilityRatio}")
            return LivenessStatus.FACE_TOO_CLOSE_TO_EDGE
        }

        // 2. Check boundary stability: Face must be within bounds for a consistent number of frames.
        val recentBoundaryCompliance = boundaryHistory.takeLast(BOUNDARY_STABILITY_FRAMES)
        if (recentBoundaryCompliance.size >= BOUNDARY_STABILITY_FRAMES) {
            val stableBoundaryFrames = recentBoundaryCompliance.count { it } // Count 'true' values
            if (stableBoundaryFrames < BOUNDARY_STABILITY_FRAMES) {
                // If not enough recent frames are compliant, it's still considered too close.
                Log.d(
                    TAG,
                    "Boundary not stable - compliant frames: $stableBoundaryFrames/$BOUNDARY_STABILITY_FRAMES"
                )
                return LivenessStatus.FACE_TOO_CLOSE_TO_EDGE
            }
        } else if (boundaryHistory.isNotEmpty()) {
            // If not enough history yet, but some data exists, check if the *very* recent frames
            // are non-compliant. This avoids situations where a face quickly moves out of bounds
            // before enough history is built.
            val recentCompliance = boundaryHistory.takeLast(2).count { it }
            if (recentCompliance == 0) { // If last two frames were out of bounds
                return LivenessStatus.FACE_TOO_CLOSE_TO_EDGE
            }
        }

        // 3. Basic quality check: If average quality is too low, report POOR_QUALITY.
        if (avgQuality < QUALITY_THRESHOLD && faceQualityHistory.size >= 3) {
            Log.d(TAG, "Poor quality detected: $avgQuality < $QUALITY_THRESHOLD")
            return LivenessStatus.POOR_QUALITY
        }

        // 4. Position check: If position score is too low, report POOR_POSITION.
        if (positionScore < 0.5f && positionHistory.size >= 3) {
            Log.d(TAG, "Poor position detected: $positionScore < 0.5")
            return LivenessStatus.POOR_POSITION
        }

        // 5. Ensure enough history has been built for reliable analysis.
        if (landmarkHistory.size < 3) {
            Log.d(TAG, "Building history: ${landmarkHistory.size}/3")
            return LivenessStatus.CHECKING
        }

        // 6. Perform movement and temporal consistency analysis.
        val movementScore = analyzeMovementPatterns()
        val consistencyScore = analyzeTemporalConsistency()

        Log.d(
            TAG,
            "Movement: ${String.format("%.4f", movementScore)}, Consistency: ${String.format("%.2f", consistencyScore)}"
        )

        // Determine the final status based on all criteria.
        return when {
            // ALL criteria must be met for LIVE_FACE: movement, consistency, quality, position, AND boundary compliance.
            movementScore >= MOVEMENT_THRESHOLD_LOW &&
                    movementScore <= MOVEMENT_THRESHOLD_HIGH &&
                    consistencyScore > 0.6f &&
                    avgQuality > QUALITY_THRESHOLD * 0.8f && // Slightly more lenient quality for live
                    positionScore > 0.5f &&
                    faceBoundary.isCompletelyInFrame && // Must be in frame
                    faceBoundary.visibilityRatio >= FACE_COMPLETENESS_THRESHOLD -> { // Must be sufficiently visible
                LivenessStatus.LIVE_FACE
            }

            // Spoof detection: Very little movement and low consistency suggests a static image/spoof.
            movementScore < MOVEMENT_THRESHOLD_LOW * 0.5f &&
                    consistencyScore < 0.3f &&
                    landmarkHistory.size >= 5 -> {
                LivenessStatus.SPOOF_DETECTED
            }

            // Otherwise, keep checking.
            else -> LivenessStatus.CHECKING
        }
    }

    /**
     * Adds current frame's face data to respective history lists.
     * Keeps the history lists within a defined size by removing the oldest entries.
     *
     * @param landmarks The current face landmarks.
     * @param quality The current face quality.
     * @param position The current face position.
     * @param isInBounds Boolean indicating if the face is currently within defined boundaries.
     */
    private fun addToHistories(
        landmarks: List<NormalizedLandmark>,
        quality: Float,
        position: FacePosition,
        isInBounds: Boolean // NEW parameter for boundary compliance
    ) {
        landmarkHistory.add(LandmarkFrame(landmarks, System.currentTimeMillis(), quality))
        if (landmarkHistory.size > LANDMARK_HISTORY_SIZE) {
            landmarkHistory.removeAt(0)
        }

        faceQualityHistory.add(quality)
        if (faceQualityHistory.size > QUALITY_HISTORY_SIZE) {
            faceQualityHistory.removeAt(0)
        }

        positionHistory.add(position)
        if (positionHistory.size > POSITION_HISTORY_SIZE) {
            positionHistory.removeAt(0)
        }

        // NEW: Add boundary compliance to history
        boundaryHistory.add(isInBounds)
        if (boundaryHistory.size > BOUNDARY_HISTORY_SIZE) {
            boundaryHistory.removeAt(0)
        }
    }

    /**
     * Manages the `currentLivenessStatus` with a balanced approach,
     * requiring consistent observations before confirming a new status.
     * It's more responsive for critical issues (NO_FACE, boundary, quality, position)
     * and requires more sustained evidence for `LIVE_FACE`.
     *
     * @param newStatus The proposed new liveness status for the current frame.
     */
    private fun updateLivenessStatusBalanced(newStatus: LivenessStatus) {
        Log.d(
            TAG,
            "Status update: $currentLivenessStatus -> $newStatus (stable: $stableStatusCount, live: $liveFrameCount)"
        )

        // Handle immediate transition to NO_FACE (highest priority).
        if (newStatus == LivenessStatus.NO_FACE) {
            if (currentLivenessStatus != newStatus) {
                currentLivenessStatus = newStatus
                liveFrameCount = 0
                stableStatusCount = 0
                lastStableStatus = newStatus
                Log.d(TAG, "Immediate reset to NO_FACE")
            }
            return
        }

        // For critical issues (boundary, poor quality, poor position), be more responsive.
        // These can often be user-correctable errors, so feedback should be quick.
        if (newStatus == LivenessStatus.FACE_TOO_CLOSE_TO_EDGE ||
            newStatus == LivenessStatus.POOR_QUALITY ||
            newStatus == LivenessStatus.POOR_POSITION
        ) {
            if (lastStableStatus == newStatus) {
                stableStatusCount++
            } else {
                stableStatusCount = 1
                lastStableStatus = newStatus
                liveFrameCount = 0 // Reset live frame count for non-live statuses
            }

            // Update after fewer consistent frames for these issues (e.g., 2 frames).
            if (stableStatusCount >= 2) {
                if (currentLivenessStatus != newStatus) {
                    Log.d(TAG, "Quick status change to: $newStatus")
                    currentLivenessStatus = newStatus
                }
            }
            return
        }

        // For LIVE_FACE status, require sustained detection.
        // This prevents flickering between CHECKING and LIVE_FACE.
        if (newStatus == LivenessStatus.LIVE_FACE) {
            if (lastStableStatus == LivenessStatus.LIVE_FACE) {
                liveFrameCount++
                stableStatusCount++
            } else {
                liveFrameCount = 1
                stableStatusCount = 1
                lastStableStatus = newStatus
            }

            // Confirm LIVE_FACE only after meeting both live frame and general stable frame requirements.
            if (liveFrameCount >= LIVE_FRAMES_REQUIRED &&
                stableStatusCount >= STABLE_FRAMES_REQUIRED
            ) {
                if (currentLivenessStatus != LivenessStatus.LIVE_FACE) {
                    Log.d(
                        TAG,
                        "✅ LIVE FACE CONFIRMED after $liveFrameCount frames with full boundary compliance"
                    )
                    currentLivenessStatus = LivenessStatus.LIVE_FACE
                }
            } else {
                // If not enough live frames, revert to CHECKING.
                if (currentLivenessStatus != LivenessStatus.CHECKING) {
                    currentLivenessStatus = LivenessStatus.CHECKING
                }
            }
        } else {
            // For CHECKING and other intermediate statuses, use general stability logic.
            if (newStatus == lastStableStatus) {
                stableStatusCount++
            } else {
                stableStatusCount = 1
                lastStableStatus = newStatus
                if (newStatus != LivenessStatus.LIVE_FACE) {
                    liveFrameCount = 0 // Reset live frame count if not aiming for LIVE_FACE
                }
            }

            // Update to the new stable status if enough consistent frames are observed.
            if (stableStatusCount >= STABLE_FRAMES_REQUIRED) {
                if (currentLivenessStatus != newStatus) {
                    Log.d(TAG, "Status stabilized: $currentLivenessStatus -> $newStatus")
                    currentLivenessStatus = newStatus
                }
            }
        }

        handleCheckingTimeoutBalanced() // Handle timeout for CHECKING status
    }

    /**
     * Handles the timeout for the `CHECKING` status. If the status remains `CHECKING`
     * for too long, it attempts to make a definitive decision based on the available data,
     * preventing the system from being stuck in a perpetual "checking" state.
     */
    private fun handleCheckingTimeoutBalanced() {
        stabilityJob?.cancel() // Cancel any existing timeout job

        if (currentLivenessStatus == LivenessStatus.CHECKING) {
            // Launch a new coroutine for the timeout
            stabilityJob = coroutineScope.launch {
                delay(STABILITY_TIMEOUT_MS) // Wait for the timeout duration
                if (currentLivenessStatus == LivenessStatus.CHECKING) {
                    Log.d(TAG, "⏰ Checking timeout - making decision based on current state")

                    // Re-evaluate key metrics at timeout
                    val avgQuality = if (faceQualityHistory.isNotEmpty()) {
                        faceQualityHistory.average().toFloat()
                    } else 0f

                    val lastPosition = positionHistory.lastOrNull()
                    val positionScore = lastPosition?.let { validateFacePosition(it) } ?: 0f
                    val movementScore = analyzeMovementPatterns()
                    val boundaryCompliance = boundaryHistory.lastOrNull() ?: false // Check latest boundary status

                    Log.d(
                        TAG,
                        "Timeout decision - Quality: $avgQuality, Position: $positionScore, Movement: $movementScore, Boundary: $boundaryCompliance"
                    )

                    // Make a decision:
                    currentLivenessStatus = when {
                        !boundaryCompliance -> LivenessStatus.FACE_TOO_CLOSE_TO_EDGE // Prioritize boundary
                        // If quality, position, and movement are "good enough" and boundary is compliant, switch to LIVE_FACE
                        avgQuality >= QUALITY_THRESHOLD * 0.7f &&
                                positionScore >= 0.4f &&
                                movementScore >= MOVEMENT_THRESHOLD_LOW * 0.5f &&
                                boundaryCompliance -> {
                            Log.d(
                                TAG,
                                "Timeout -> LIVE_FACE (good enough with boundary compliance)"
                            )
                            LivenessStatus.LIVE_FACE
                        }
                        // Otherwise, assign the specific issue that is failing
                        avgQuality < QUALITY_THRESHOLD * 0.7f -> LivenessStatus.POOR_QUALITY
                        positionScore < 0.4f -> LivenessStatus.POOR_POSITION
                        else -> LivenessStatus.CHECKING // If still none of the above, remain checking (should be rare)
                    }
                }
            }
        }
    }

    /**
     * Determines whether face bounding boxes should be drawn on the preview.
     * Boxes are generally shown for any status where user feedback for positioning/quality is helpful.
     *
     * @return True if face boxes should be shown, false otherwise.
     */
    private fun shouldShowFaceBox(): Boolean {
        return when (currentLivenessStatus) {
            LivenessStatus.LIVE_FACE -> true // Show box when live
            LivenessStatus.CHECKING -> true // Show box during checking
            LivenessStatus.POOR_POSITION -> true // Show box to help reposition
            LivenessStatus.POOR_QUALITY -> true // Show box for quality feedback
            LivenessStatus.FACE_TOO_CLOSE_TO_EDGE -> true // Show box to help user move away from edge
            else -> false // Don't show for NO_FACE, SPOOF_DETECTED etc.
        }
    }

    /**
     * Resets all internal state variables to their initial values, effectively
     * restarting the liveness detection process. This is called when no face is detected
     * or on an error.
     */
    private fun resetToNoFace() {
        currentLivenessStatus = LivenessStatus.NO_FACE
        liveFrameCount = 0
        stableStatusCount = 0
        lastStableStatus = LivenessStatus.NO_FACE
        landmarkHistory.clear()
        faceQualityHistory.clear()
        positionHistory.clear()
        boundaryHistory.clear() // NEW: Clear boundary history
        stabilityJob?.cancel() // Cancel any active timeout job
        Log.d(TAG, "🔄 Reset to NO_FACE")
    }

    /**
     * Calculates the center position and size of the face based on its landmarks.
     * @param landmarks The list of normalized landmarks for the face.
     * @param resultBundle The result bundle containing image dimensions.
     * @return A `FacePosition` object.
     */
    private fun calculateFacePosition(
        landmarks: List<NormalizedLandmark>,
        resultBundle: ResultBundle
    ): FacePosition {
        val centerX = landmarks.map { it.x() }.average().toFloat()
        val centerY = landmarks.map { it.y() }.average().toFloat()
        val minX = landmarks.minOf { it.x() }
        val maxX = landmarks.maxOf { it.x() }
        val minY = landmarks.minOf { it.y() }
        val maxY = landmarks.maxOf { it.y() }
        val faceWidth = (maxX - minX) * resultBundle.inputImageWidth
        val faceHeight = (maxY - minY) * resultBundle.inputImageHeight
        val faceSize = kotlin.math.sqrt((faceWidth * faceHeight).toDouble()).toFloat()
        return FacePosition(centerX, centerY, faceSize, System.currentTimeMillis())
    }

    /**
     * Validates the face's position within the frame (size, centrality) and assigns a score.
     * @param position The `FacePosition` object.
     * @return A float score between 0.0 and 1.0, where higher is better.
     */
    private fun validateFacePosition(position: FacePosition): Float {
        var score = 1.0f
        val imageArea = kotlin.math.sqrt((previewWidth * previewHeight).toDouble()).toFloat()
        val faceSizeRatio = position.faceSize / imageArea // Ratio of face size to overall image size

        when {
            faceSizeRatio < MIN_FACE_SIZE_RATIO -> {
                score = 0.3f // Too small, low score
                Log.d(TAG, "Face too small: $faceSizeRatio")
            }

            faceSizeRatio > MAX_FACE_SIZE_RATIO -> {
                score = 0.4f // Too large, low score
                Log.d(TAG, "Face too large: $faceSizeRatio")
            }

            faceSizeRatio < MIN_FACE_SIZE_RATIO * 2 -> { // Still small but better than min
                score = 0.7f
            }
        }

        // Calculate distance from the center of the image (normalized 0-1)
        val distanceFromCenter = kotlin.math.sqrt(
            ((position.centerX - 0.5f) * (position.centerX - 0.5f) +
                    (position.centerY - 0.5f) * (position.centerY - 0.5f)).toDouble()
        ).toFloat()

        if (distanceFromCenter > CENTER_TOLERANCE) {
            score *= 0.7f // Penalize if too far off-center
            Log.d(TAG, "Face off-center: distance = $distanceFromCenter")
        }

        return score.coerceIn(0.0f, 1.0f) // Ensure score is between 0 and 1
    }

    /**
     * Calculates a general "quality" score for the detected face based on its size and centrality.
     * This is distinct from `validateFacePosition` but uses similar metrics.
     * @param result The `FaceLandmarkerResult`.
     * @param resultBundle The result bundle containing image dimensions.
     * @return A float quality score between 0.0 and 1.0.
     */
    private fun calculateFaceQuality(
        result: FaceLandmarkerResult,
        resultBundle: ResultBundle
    ): Float {
        val landmarks = result.faceLandmarks()[0]
        var qualityScore = 1.0f
        val faceArea = calculateFaceArea(landmarks, resultBundle)
        val imageArea = resultBundle.inputImageWidth * resultBundle.inputImageHeight
        val faceRatio = faceArea / imageArea // Ratio of face area to total image area

        // Penalize based on face size ratio
        when {
            faceRatio < 0.03f -> qualityScore *= 0.4f
            faceRatio < 0.06f -> qualityScore *= 0.7f
            faceRatio > 0.8f -> qualityScore *= 0.6f
            faceRatio > 0.6f -> qualityScore *= 0.8f
        }

        // Penalize if face is too far from center
        val centerX = landmarks.map { it.x() }.average().toFloat()
        val centerY = landmarks.map { it.y() }.average().toFloat()
        val distanceFromCenter = kotlin.math.sqrt(
            ((centerX - 0.5f) * (centerX - 0.5f) + (centerY - 0.5f) * (centerY - 0.5f)).toDouble()
        ).toFloat()

        if (distanceFromCenter > 0.4f) qualityScore *= 0.8f
        return qualityScore.coerceIn(0.0f, 1.0f)
    }

    /**
     * Calculates the bounding area of the face based on its landmarks in input image pixels.
     * @param landmarks The list of normalized landmarks.
     * @param resultBundle The result bundle with input image dimensions.
     * @return The area of the face bounding box in pixels.
     */
    private fun calculateFaceArea(
        landmarks: List<NormalizedLandmark>,
        resultBundle: ResultBundle
    ): Float {
        val minX = landmarks.minOf { it.x() } * resultBundle.inputImageWidth
        val maxX = landmarks.maxOf { it.x() } * resultBundle.inputImageWidth
        val minY = landmarks.minOf { it.y() } * resultBundle.inputImageHeight
        val maxY = landmarks.maxOf { it.y() } * resultBundle.inputImageHeight
        return (maxX - minX) * (maxY - minY)
    }

    /**
     * Analyzes historical landmark data to detect movement patterns,
     * which is a key indicator for liveness.
     * @return A float representing the average movement score.
     */
    private fun analyzeMovementPatterns(): Float {
        if (landmarkHistory.size < 2) return 0f // Need at least two frames to compare
        var totalMovement = 0f
        var frameComparisons = 0
        val recentFrames = landmarkHistory.takeLast(4) // Analyze recent 4 frames

        for (i in 1 until recentFrames.size) {
            val current = recentFrames[i].landmarks
            val previous = recentFrames[i - 1].landmarks
            var frameMovement = 0f
            val landmarksToCheck = minOf(current.size, previous.size)
            val step = maxOf(1, landmarksToCheck / 50) // Sample a subset of landmarks for efficiency

            for (j in 0 until landmarksToCheck step step) {
                // Calculate Euclidean distance between corresponding landmarks in two frames
                val dx = current[j].x() - previous[j].x()
                val dy = current[j].y() - previous[j].y()
                frameMovement += kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            }

            if (landmarksToCheck > 0) {
                totalMovement += frameMovement / (landmarksToCheck / step) // Average movement per landmark for this pair of frames
                frameComparisons++
            }
        }

        return if (frameComparisons > 0) totalMovement / frameComparisons else 0f // Overall average movement
    }

    /**
     * Analyzes temporal consistency of landmark positions. Low consistency might indicate
     * sudden jumps (e.g., due to model instability) or a static image.
     * @return A float score between 0.0 and 1.0, where higher is more consistent.
     */
    private fun analyzeTemporalConsistency(): Float {
        if (landmarkHistory.size < 2) return 0f
        val recentFrames = landmarkHistory.takeLast(3) // Analyze recent 3 frames
        if (recentFrames.size < 2) return 0f

        var consistencyScore = 1.0f
        val landmarkCount = recentFrames.first().landmarks.size
        val step = maxOf(1, landmarkCount / 20) // Sample a subset of landmarks

        for (landmarkIdx in 0 until landmarkCount step step) {
            val xPositions = recentFrames.map { it.landmarks[landmarkIdx].x() }
            val yPositions = recentFrames.map { it.landmarks[landmarkIdx].y() }
            val xVariance = calculateVariance(xPositions) // Variance of X positions over time
            val yVariance = calculateVariance(yPositions) // Variance of Y positions over time

            val positionVariance = (xVariance + yVariance) / 2 // Average positional variance
            if (positionVariance > 0.02f) { // If variance is too high, reduce consistency score
                consistencyScore *= 0.98f
            }
        }

        return consistencyScore.coerceIn(0f, 1f) // Ensure score is between 0 and 1
    }

    /**
     * Calculates the variance of a list of float values.
     * @param values The list of float values.
     * @return The variance.
     */
    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    /**
     * Converts MediaPipe normalized landmarks into screen-space `FaceBox` objects.
     * Applies scaling and padding to make the bounding box suitable for UI drawing.
     *
     * @param result The `FaceLandmarkerResult`.
     * @param resultBundle The result bundle containing input image dimensions.
     * @return A list of `FaceBox` objects.
     */
    private fun convertLandmarksToFaceBoxes(
        result: FaceLandmarkerResult,
        resultBundle: ResultBundle
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
                // Scale landmark coordinates from input image to preview dimensions.
                var x = landmark.x() * resultBundle.inputImageWidth * scaleX
                val y = landmark.y() * resultBundle.inputImageHeight * scaleY

                // Again, assuming FaceLandmarkerHelper already mirrored X if `isMirrored` was true.
                /*
                if (isMirrored) {
                    x = previewWidth - x
                }
                */

                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }

            val horizontalPadding = 0.2f  // 20% padding
            val verticalPadding = 0.2f    // 20% padding

            val width = maxX - minX
            val height = maxY - minY
            val padX = width * horizontalPadding
            val padY = height * verticalPadding

            // Create FaceBox with padding and clamp to preview dimensions.
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

    // --- Public Methods for ViewModel Interaction ---

    /**
     * @return The current stable liveness status.
     */
    fun getCurrentLivenessStatus(): LivenessStatus = currentLivenessStatus

    /**
     * Checks if the face is suitable for a final capture. This requires the `LIVE_FACE` status
     * and sustained `LIVE_FACE` frames, along with consistent boundary compliance.
     *
     * @return True if the face is ready for capture, false otherwise.
     */
    fun isFaceSuitableForCapture(): Boolean {
        return currentLivenessStatus == LivenessStatus.LIVE_FACE && // Must be live
                liveFrameCount >= LIVE_FRAMES_REQUIRED && // Must have enough consecutive live frames
                boundaryHistory.takeLast(BOUNDARY_STABILITY_FRAMES)
                    .all { it } // NEW: All recent frames must be within boundaries
    }

    /**
     * @return The average face quality score from the recent history.
     */
    fun getFaceQualityScore(): Float {
        return if (faceQualityHistory.isNotEmpty()) {
            faceQualityHistory.average().toFloat()
        } else 0f
    }

    /**
     * Cleans up resources held by the `FaceAnalyzer`, particularly the MediaPipe helper.
     */
    fun close() {
        stabilityJob?.cancel() // Cancel any pending stability job
        faceLandmarkerHelper?.clearFaceLandmarker() // Release MediaPipe resources
        faceLandmarkerHelper = null
        landmarkHistory.clear()
        faceQualityHistory.clear()
        positionHistory.clear()
        boundaryHistory.clear()
        isInitialized = false
        Log.d(TAG, "FaceAnalyzer closed")
    }
}