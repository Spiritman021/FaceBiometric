package com.aican.biometricattendance.presentation.screens.camera

import android.content.Context // Android context
import android.graphics.Bitmap // Bitmap image class
import android.graphics.BitmapFactory // Utility for creating Bitmaps from various sources
import android.media.MediaScannerConnection // For scanning media files into the gallery
import android.net.Uri // URI for content identification
import android.os.Environment // Access to public storage directories
import android.util.Log // For logging messages
import androidx.camera.core.* // CameraX core APIs (Preview, ImageCapture, ImageAnalysis, etc.)
import androidx.camera.core.MirrorMode.MIRROR_MODE_OFF // Mirror mode for camera preview
import androidx.camera.lifecycle.ProcessCameraProvider // Provides camera instances linked to lifecycle
import androidx.camera.lifecycle.awaitInstance // Coroutine extension for ProcessCameraProvider
import androidx.core.content.ContextCompat // Utility for context-compat operations
import androidx.core.content.FileProvider // For generating content URIs for files
import androidx.lifecycle.LifecycleOwner // Android LifecycleOwner
import androidx.lifecycle.ViewModel // Android ViewModel class
import androidx.lifecycle.viewModelScope // Coroutine scope tied to ViewModel lifecycle
import com.aican.biometricattendance.data.models.camera.FaceBox // Data class for detected face bounding box
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus // Enum for face liveness status
import com.aican.biometricattendance.data.models.faceembedding.EmbeddingQuality // Data class for embedding quality metrics
import com.aican.biometricattendance.data.models.faceembedding.EmbeddingStats // Data class for embedding statistics
import com.aican.biometricattendance.ml.camera.FaceAnalyzer // Custom ImageAnalysis.Analyzer for face detection and liveness
import com.aican.biometricattendance.ml.facenet.UnifiedFaceEmbeddingProcessor
import com.aican.biometricattendance.services.camera.CameraController
import com.aican.biometricattendance.services.face_recognition.FaceRegistrationService
import com.aican.biometricattendance.services.face_recognition.LivenessDetector
import kotlinx.coroutines.CoroutineScope // Kotlin Coroutine Scope
import kotlinx.coroutines.Dispatchers // Coroutine Dispatchers for different threads (Main, IO, Default)
import kotlinx.coroutines.awaitCancellation // Suspends until the coroutine is cancelled
import kotlinx.coroutines.delay // Suspends a coroutine for a given time
import kotlinx.coroutines.flow.MutableStateFlow // Mutable hot Flow that holds a single value
import kotlinx.coroutines.flow.StateFlow // Read-only StateFlow
import kotlinx.coroutines.launch // Launches a new coroutine
import kotlinx.coroutines.withContext // Changes the context for a block of code
import java.io.File // File operations
import java.text.SimpleDateFormat // For date formatting
import java.util.* // Utility classes (Date, Locale)

/**
 * ViewModel for managing the camera preview and face capture logic.
 * It integrates with CameraX, MediaPipe for face detection/liveness,
 * and a FaceEmbeddingExtractor for processing captured faces.
 */
class CameraPreviewViewModel : ViewModel() {


    private lateinit var livenessDetector: LivenessDetector
    private lateinit var cameraController: CameraController
    private lateinit var faceRegistrationService: FaceRegistrationService



    // --- Face Registration Dialog States ---
    // States related to the dialog shown after a face is captured for registration.
    val showCaptureDialog =
        MutableStateFlow(false) // Controls visibility of the registration dialog
    private val _capturedFaceUri =
        MutableStateFlow<Uri?>(null) // URI of the captured and cropped face
    val capturedFaceUri: StateFlow<Uri?> =
        _capturedFaceUri // Exposes capturedFaceUri as a read-only StateFlow

    val userName = MutableStateFlow("") // User input for name
    val userEmail = MutableStateFlow("") // User input for email
    val isSubmitting = MutableStateFlow(false) // Indicates if face data submission is in progress

    // --- CameraX and Core Camera States ---
    private val _surfaceRequest =
        MutableStateFlow<SurfaceRequest?>(null) // The SurfaceRequest for CameraXViewfinder
    val surfaceRequest: StateFlow<SurfaceRequest?> =
        _surfaceRequest // Exposed as read-only StateFlow

    private var cameraProvider: ProcessCameraProvider? = null // CameraX provider instance
    var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA // Default to front camera
    private var imageCapture: ImageCapture? = null // CameraX ImageCapture use case
    private var flashEnabled = false // Current flash state

    // --- UI Visual States ---
    val shutterFlash = MutableStateFlow(false) // Controls the shutter flash animation
    val capturedPhotoUri =
        MutableStateFlow<Uri?>(null) // URI of the captured photo (might be full, not just cropped face)
    val showPreviewDialog =
        MutableStateFlow(false) // Controls visibility of a general preview dialog (currently unused in UI)
    val capturedPhotoFile = MutableStateFlow<File?>(null) // File object of the captured photo
    val isUploading =
        MutableStateFlow<Boolean>(false) // Indicates if a photo is being uploaded (currently unused in UI)
    val isProcessingPhoto =
        MutableStateFlow(false) // Indicates if a captured photo is being processed (cropped, etc.)

    // --- MediaPipe Face Detection States ---
    private val _faceBoxes =
        MutableStateFlow<List<FaceBox>>(emptyList()) // List of detected face bounding boxes
    val faceBoxes: StateFlow<List<FaceBox>> = _faceBoxes // Exposed as read-only StateFlow

    private val _livenessStatus =
        MutableStateFlow(LivenessStatus.NO_FACE) // Current liveness status detected by FaceAnalyzer
    val livenessStatus: StateFlow<LivenessStatus> =
        _livenessStatus // Exposed as read-only StateFlow

    private var lastDetectedFaceBox: FaceBox? =
        null // Stores the most recent detected face for cropping
    private var currentAnalyzer: FaceAnalyzer? = null // Reference to the active FaceAnalyzer
    private var previewWidth: Float = 0.0F // Width of the camera preview surface
    private var previewHeight: Float = 0.0F // Height of the camera preview surface

    private val _faceQualityScore =
        MutableStateFlow(0.0f) // Current face quality score from FaceAnalyzer
    private var isCameraBound = false // Flag to track if camera is currently bound

    private var imageAnalyzer: ImageAnalysis? =
        null // CameraX ImageAnalysis use case for real-time processing

    private var unifiedFaceEmbeddingProcessor: UnifiedFaceEmbeddingProcessor? = null


    /**
     * Returns the current face quality score from the FaceAnalyzer.
     */
    fun getFaceQualityScore(): Float {
        return currentAnalyzer?.getFaceQualityScore() ?: 0.0f
    }

    /**
     * Updates the ViewModel with the camera preview surface dimensions.
     * This is crucial for correctly scaling face detection results.
     * @param width The width of the preview surface.
     * @param height The height of the preview surface.
     */
    fun updatePreviewSize(width: Float, height: Float) {
        if (previewWidth != width || previewHeight != height) {
            previewWidth = width
            previewHeight = height
            // When preview size changes, the ImageAnalyzer might need to be reconfigured.
            updateImageAnalyzer()
        }
    }

    /**
     * Updates the list of detected face boxes and the liveness status.
     * This is called by the `FaceAnalyzer` when new detection results are available.
     * @param boxes The list of detected face boxes.
     */
    fun updateFaceBoxes(boxes: List<FaceBox>) {
        viewModelScope.launch {
            _faceBoxes.emit(boxes) // Update the StateFlow for UI consumption

            // Update liveness status from analyzer
            currentAnalyzer?.let { analyzer ->
                val newStatus = analyzer.getCurrentLivenessStatus()
                _livenessStatus.emit(newStatus) // Update the liveness status StateFlow
                _faceQualityScore.emit(analyzer.getFaceQualityScore()) // Update the quality score

                if (newStatus == LivenessStatus.FACE_TOO_CLOSE_TO_EDGE) {
                    Log.d("CameraViewModel", "Face too close to edge detected")
                }
            }

            // Store the first detected face box for later cropping
            if (boxes.isNotEmpty()) {
                lastDetectedFaceBox = boxes.first()
            }
        }
    }

    /**
     * Helper to close the old analyzer if it exists when preview size changes.
     * The analyzer is then recreated in `bindToCamera`.
     */
    private fun updateImageAnalyzer() {
        if (previewWidth > 0 && previewHeight > 0) {
            currentAnalyzer?.close() // Close the existing analyzer
        }
    }

    /**
     * Creates a new `ImageAnalysis` use case with a `FaceAnalyzer`.
     * @param context The application context.
     * @return An `ImageAnalysis` instance configured with the `FaceAnalyzer`.
     */
    private fun createImageAnalyzer(context: Context): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Only process the latest frame
            .setTargetAspectRatio(AspectRatio.RATIO_4_3) // Set target aspect ratio
            .build()
            .also { analyzer ->
                // Determine if the front camera is active for mirroring logic in FaceAnalyzer
                val isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA

                val faceAnalyzer = FaceAnalyzer(
                    context = context,
                    previewWidth = previewWidth,
                    previewHeight = previewHeight,
                    isMirrored = isFrontCamera // Pass mirroring info
                ) { boxes ->
                    updateFaceBoxes(boxes) // Callback to update ViewModel with detected faces
                }

                currentAnalyzer = faceAnalyzer // Store reference to the created analyzer
                Log.d("CameraViewModel", "Face analyzer created")

                analyzer.setAnalyzer(
                    ContextCompat.getMainExecutor(context), // Use main thread executor
                    faceAnalyzer // Set the custom analyzer
                )
            }
    }

    /**
     * Debugging function to log the current state of camera-related variables.
     * @param context A descriptive string for the log (e.g., "BIND_START").
     */
    fun debugCameraState(context: String) {
        Log.d(
            "CameraViewModel", """
        🔍 DEBUG CAMERA STATE [$context]:
        - previewWidth: $previewWidth
        - previewHeight: $previewHeight  
        - isCameraBound: $isCameraBound
        - cameraProvider: ${cameraProvider != null}
        - imageCapture: ${imageCapture != null}
        - imageAnalyzer: ${imageAnalyzer != null}
        - currentAnalyzer: ${currentAnalyzer != null}
        - surfaceRequest: ${_surfaceRequest.value != null}
        - cameraSelector: ${if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) "FRONT" else "BACK"}
    """.trimIndent()
        )
    }

    /**
     * Binds CameraX use cases (Preview, ImageCapture, ImageAnalysis) to the lifecycle owner.
     * This function is suspending as `awaitInstance` is a suspend function.
     * It ensures a clean bind by unbinding previous uses cases first.
     * @param appContext The application context.
     * @param lifecycleOwner The LifecycleOwner to which the camera is bound.
     */
    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        debugCameraState("BIND_START")

        try {
            // Always unbind first to ensure a clean state and avoid issues like surface abandonment.
            cameraProvider?.unbindAll()
            debugCameraState("AFTER_UNBIND")

            // Get or await the ProcessCameraProvider instance.
            if (cameraProvider == null) {
                cameraProvider = ProcessCameraProvider.awaitInstance(appContext)
            }

            // Initialize ImageCapture use case.
            imageCapture = ImageCapture.Builder()
                .setFlashMode(
                    if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                )
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()

            // Create a new Preview use case. Crucial to create a *new* one to avoid
            // 'surface abandonment' when rebinding or toggling camera.
            val newPreviewUseCase = Preview.Builder()
                .setMirrorMode(MIRROR_MODE_OFF) // Control mirroring if needed (CameraX handles it internally based on selector)
                .build()
                .apply {
                    // Set the SurfaceProvider, which delivers the camera frames to CameraXViewfinder.
                    setSurfaceProvider { newSurfaceRequest ->
                        _surfaceRequest.value =
                            newSurfaceRequest // Update the StateFlow for Composable
                    }
                }

            // Create or recreate ImageAnalyzer if preview size is available.
            Log.d("CameraViewModel", "updatePreviewSize: $previewWidth x $previewHeight")
            imageAnalyzer = if (previewWidth > 0 && previewHeight > 0) {
                Log.d("CameraViewModel", "Creating new analyzer")
                createImageAnalyzer(appContext)
            } else {
                null // If preview size isn't ready, don't create analyzer yet
            }

            if (imageAnalyzer == null) {
                Log.d("CameraViewModel", "No analyzer created null")
            }

            // Collect all use cases to bind.
            val useCases = mutableListOf<UseCase>().apply {
                add(newPreviewUseCase)
                add(imageCapture!!) // Image capture is essential
                imageAnalyzer?.let { add(it) } // Add analyzer if it's initialized
            }

            Log.d("CameraViewModel", "Binding to camera with ${useCases.size} use cases")

            // Bind all selected use cases to the lifecycle owner.
            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray() // Spread operator to pass list as vararg
            )

            isCameraBound = true // Mark camera as bound
            Log.d("CameraViewModel", "✅ Camera bound successfully with ${useCases.size} use cases")
            debugCameraState("BIND_SUCCESS")

            awaitCancellation() // Keep coroutine active until cancelled (e.g., ViewModel cleared or unbind called)
        } catch (e: Exception) {
            Log.e("Camera", "❌ Binding failed", e)
            isCameraBound = false
            debugCameraState("BIND_FAILED")
            throw e // Re-throw to propagate the error
        }
    }

    /**
     * Toggles between front and back camera.
     * This will trigger a re-binding of the camera in `LaunchedEffect` in the Composable.
     */
    fun toggleCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        updateImageAnalyzer() // Analyzer needs to be updated when camera direction changes (for mirroring)

        Log.d(
            "CameraViewModel", "Camera toggled - Now using: ${
                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) "FRONT" else "BACK"
            }"
        )
    }

    /**
     * Toggles the flash mode (On/Off).
     */
    fun toggleFlash() {
        flashEnabled = !flashEnabled
        imageCapture?.flashMode = if (flashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    /**
     * Checks if the detected face is suitable for capture based on liveness and face presence.
     * @return True if the face is suitable, false otherwise.
     */
    fun isFaceSuitableForCapture(): Boolean {
        val hasValidFace = _faceBoxes.value.isNotEmpty() // Check if any face is detected
        // Check liveness status from the analyzer; ensure it's LIVE_FACE
        val isLive = currentAnalyzer?.isFaceSuitableForCapture() ?: false
        val currentStatus = _livenessStatus.value

        Log.d(
            "CameraViewModel", """
            Face capture check:
            - Has valid face: $hasValidFace
            - Is live (from analyzer): $isLive  
            - Current status (from VM): $currentStatus
            - Suitable: ${hasValidFace && isLive && currentStatus == LivenessStatus.LIVE_FACE}
        """.trimIndent()
        )

        // All conditions must be met for a "suitable" capture
        return hasValidFace && isLive && currentStatus == LivenessStatus.LIVE_FACE
    }

    /**
     * Captures a photo, crops it to the detected face region, and saves only the cropped face image.
     * @param context The Android context.
     * @param onSaved Callback function invoked when the cropped image is successfully saved.
     */
    fun capturePhoto(
        context: Context,
        onSaved: (Uri) -> Unit = {}
    ) {
        // Pre-check for suitability
        if (!isFaceSuitableForCapture()) {
            Log.w("CameraViewModel", "Cannot capture: Face not suitable (liveness check failed)")
            return
        }

        // Ensure a face box was detected
        val currentFaceBox = lastDetectedFaceBox
        if (currentFaceBox == null) {
            Log.w("CameraViewModel", "Cannot capture: No face box detected")
            return
        }

        isProcessingPhoto.value = true // Show processing overlay

        val imageCapture = imageCapture ?: run {
            isProcessingPhoto.value = false
            return // ImageCapture not ready
        }

        // Create a temporary file for the full captured image. This will be deleted after cropping.
        val picturesDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val disposableFolder = File(picturesDir, "Attendance Photos")
        if (!disposableFolder.exists()) disposableFolder.mkdirs() // Create directory if it doesn't exist

        val photoFile = File(
            disposableFolder,
            "TEMP_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        shutterFlash.value = true // Trigger shutter flash UI effect

        // Take the picture using CameraX
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context), // Use main thread for callback
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Process the captured image in a background coroutine (IO Dispatcher)
                    viewModelScope.launch {
                        try {
                            // Crop and save the face image
                            val croppedUri = withContext(Dispatchers.IO) {
                                cropAndSaveFaceImage(photoFile, currentFaceBox, context)
                            }

                            if (croppedUri != null) {
                                photoFile.delete() // Delete the original full temporary image

                                capturedPhotoUri.value = croppedUri // Update state with cropped URI
                                capturedPhotoFile.value = File(croppedUri.path ?: "")

                                // Scan the newly saved cropped file so it appears in the device's media gallery
                                MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(croppedUri.path),
                                    arrayOf("image/jpeg"),
                                    null
                                )

                                _capturedFaceUri.value =
                                    croppedUri // Store the cropped face URI for registration
                                showCaptureDialog.value = true // Show the registration dialog
                                onSaved(croppedUri) // Invoke callback

                                Log.d("CameraViewModel", "✅ Cropped face image saved successfully")
                            } else {
                                Log.e("CameraViewModel", "❌ Failed to crop face image")
                            }
                        } catch (e: Exception) {
                            Log.e("CameraViewModel", "❌ Error processing captured image", e)
                        } finally {
                            isProcessingPhoto.value = false // Hide processing overlay
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isProcessingPhoto.value = false // Hide processing overlay on error
                    Log.e("Camera", "❌ Photo capture failed: ${exception.message}", exception)
                }
            }
        )

        // Hide shutter flash effect after a short delay
        CoroutineScope(Dispatchers.Main).launch {
            delay(150)
            shutterFlash.value = false
        }
    }

    /**
     * Crops the original captured photo to the detected face region and saves it as a new file.
     * @param originalPhotoFile The file of the full, original captured image.
     * @param faceBox The bounding box of the face in the original image's preview coordinates.
     * @param context The Android context.
     * @return The URI of the newly saved cropped face image, or null if cropping failed.
     */
    private suspend fun cropAndSaveFaceImage(
        originalPhotoFile: File,
        faceBox: FaceBox,
        context: Context
    ): Uri? {
        return withContext(Dispatchers.IO) { // Perform on IO dispatcher for heavy operations
            try {
                val originalBitmap = BitmapFactory.decodeFile(originalPhotoFile.absolutePath)
                    ?: return@withContext null // Decode the full image

                Log.d(
                    "CameraViewModel", """
                    📸 Image Processing Details:
                    - Original image: ${originalBitmap.width}x${originalBitmap.height}
                    - Face box (preview coords): left=${faceBox.left}, top=${faceBox.top}, right=${faceBox.right}, bottom=${faceBox.bottom}
                    - Preview size: ${previewWidth}x${previewHeight}
                """.trimIndent()
                )

                // Calculate scaling factors to convert face box coordinates from preview
                // dimensions to the actual captured image dimensions.
                val scaleX = originalBitmap.width.toFloat() / previewWidth
                val scaleY = originalBitmap.height.toFloat() / previewHeight

                // Convert face box coordinates to actual image pixel coordinates,
                // coercing them to stay within image bounds.
                var actualLeft = (faceBox.left * scaleX).toInt().coerceAtLeast(0)
                var actualTop = (faceBox.top * scaleY).toInt().coerceAtLeast(0)
                var actualRight =
                    (faceBox.right * scaleX).toInt().coerceAtMost(originalBitmap.width)
                var actualBottom =
                    (faceBox.bottom * scaleY).toInt().coerceAtMost(originalBitmap.height)

                // Note: The FaceAnalyzer handles internal mirroring for landmarks.
                // If FaceAnalyzer correctly provides non-mirrored face box coordinates relative to the preview,
                // then no additional mirroring logic is needed here for cropping.
                // The commented-out mirroring logic below is typically for raw image data if it's mirrored.
                /*
                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    // Mirror the X coordinates for front camera if the original image itself is mirrored
                    val mirroredLeft = originalBitmap.width - actualRight
                    val mirroredRight = originalBitmap.width - actualLeft
                    actualLeft = mirroredLeft.coerceAtLeast(0)
                    actualRight = mirroredRight.coerceAtMost(originalBitmap.width)
                    Log.d("CameraViewModel", "🔄 Applied front camera mirroring for cropping: left=$actualLeft, right=$actualRight")
                }
                */

                val cropWidth = actualRight - actualLeft
                val cropHeight = actualBottom - actualTop

                Log.d(
                    "CameraViewModel", """
                    ✂️ Crop Details:
                    - Scale factors: scaleX=${
                        String.format(
                            "%.2f",
                            scaleX
                        )
                    }, scaleY=${String.format("%.2f", scaleY)}
                    - Final crop area (image pixels): left=$actualLeft, top=$actualTop, width=$cropWidth, height=$cropHeight
                """.trimIndent()
                )

                if (cropWidth <= 0 || cropHeight <= 0) {
                    Log.e(
                        "CameraViewModel",
                        "❌ Invalid crop dimensions: ${cropWidth}x${cropHeight}"
                    )
                    originalBitmap.recycle()
                    return@withContext null
                }

                // Create the cropped bitmap
                val croppedBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    actualLeft,
                    actualTop,
                    cropWidth,
                    cropHeight
                )

                // Define filename and path for the cropped face image
                val croppedFileName =
                    "FACE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
                val croppedFile =
                    File(originalPhotoFile.parent, croppedFileName) // Save in the same folder

                // Save the cropped bitmap to file with high quality
                var saveSuccess = false
                croppedFile.outputStream().use { outputStream ->
                    saveSuccess =
                        croppedBitmap.compress(
                            Bitmap.CompressFormat.JPEG,
                            95,
                            outputStream
                        ) // 95% quality
                }

                // Recycle bitmaps to free up memory
                croppedBitmap.recycle()
                originalBitmap.recycle()

                if (!saveSuccess) {
                    Log.e("CameraViewModel", "❌ Failed to save cropped bitmap")
                    return@withContext null
                }

                // Get content URI for the cropped file using FileProvider (for secure access)
                val croppedUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider", // Must match the authority in AndroidManifest.xml
                    croppedFile
                )

                Log.d(
                    "CameraViewModel", """
                    ✅ Cropped face saved successfully:
                    - File: ${croppedFile.absolutePath}
                    - Size: ${croppedFile.length()} bytes
                    - Dimensions: ${cropWidth}x${cropHeight}
                """.trimIndent()
                )

                croppedUri // Return the URI
            } catch (e: Exception) {
                Log.e("CameraViewModel", "❌ Error cropping face image", e)
                null
            }
        }
    }

    /**
     * Alternative method to capture and save a face with additional padding around the detected face.
     * This is an alternative to `capturePhoto` and demonstrates flexibility.
     * @param context The Android context.
     * @param paddingRatio The ratio of padding to apply around the face (e.g., 0.15 for 15%).
     * @param onSaved Callback function invoked when the padded image is successfully saved.
     */
    fun captureFaceWithPadding(
        context: Context,
        paddingRatio: Float = 0.15f, // 15% padding around face
        onSaved: (Uri) -> Unit = {}
    ) {
        if (!isFaceSuitableForCapture()) {
            Log.w("CameraViewModel", "Cannot capture: Face not suitable")
            return
        }

        val currentFaceBox = lastDetectedFaceBox ?: run {
            Log.w("CameraViewModel", "Cannot capture: No face box detected")
            return
        }

        isProcessingPhoto.value = true
        val imageCapture = imageCapture ?: run {
            isProcessingPhoto.value = false
            return
        }

        val picturesDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val disposableFolder = File(picturesDir, "Attendance Photos")
        if (!disposableFolder.exists()) disposableFolder.mkdirs()

        val photoFile = File(
            disposableFolder,
            "TEMP_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )

        shutterFlash.value = true

        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(photoFile).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    viewModelScope.launch {
                        try {
                            val croppedUri = withContext(Dispatchers.IO) {
                                cropFaceWithPadding(
                                    photoFile,
                                    currentFaceBox,
                                    context,
                                    paddingRatio
                                )
                            }

                            if (croppedUri != null) {
                                photoFile.delete()
                                capturedPhotoUri.value = croppedUri
                                capturedPhotoFile.value = File(croppedUri.path ?: "")

                                MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(croppedUri.path),
                                    arrayOf("image/jpeg"),
                                    null
                                )

                                showPreviewDialog.value =
                                    true // This might be for a general preview, not registration
                                onSaved(croppedUri)
                                Log.d("CameraViewModel", "✅ Padded face image saved successfully")
                            }
                        } catch (e: Exception) {
                            Log.e("CameraViewModel", "❌ Error processing padded image", e)
                        } finally {
                            isProcessingPhoto.value = false
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isProcessingPhoto.value = false
                    Log.e("Camera", "❌ Padded photo capture failed", exception)
                }
            }
        )

        CoroutineScope(Dispatchers.Main).launch {
            delay(150)
            shutterFlash.value = false
        }
    }

    /**
     * Crops the face from an image with a specified padding ratio around the detected face.
     * @param originalPhotoFile The file of the full, original captured image.
     * @param faceBox The bounding box of the face in the original image's preview coordinates.
     * @param context The Android context.
     * @param paddingRatio The ratio of padding to add (e.g., 0.15 for 15%).
     * @return The URI of the newly saved padded face image, or null if cropping failed.
     */
    private suspend fun cropFaceWithPadding(
        originalPhotoFile: File,
        faceBox: FaceBox,
        context: Context,
        paddingRatio: Float
    ): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val originalBitmap = BitmapFactory.decodeFile(originalPhotoFile.absolutePath)
                    ?: return@withContext null

                val faceWidth = faceBox.right - faceBox.left
                val faceHeight = faceBox.bottom - faceBox.top
                val paddingX = faceWidth * paddingRatio
                val paddingY = faceHeight * paddingRatio

                val scaleX = originalBitmap.width.toFloat() / previewWidth
                val scaleY = originalBitmap.height.toFloat() / previewHeight

                // Apply padding and scale to actual image coordinates
                var actualLeft = ((faceBox.left - paddingX) * scaleX).toInt().coerceAtLeast(0)
                var actualTop = ((faceBox.top - paddingY) * scaleY).toInt().coerceAtLeast(0)
                var actualRight =
                    ((faceBox.right + paddingX) * scaleX).toInt().coerceAtMost(originalBitmap.width)
                var actualBottom = ((faceBox.bottom + paddingY) * scaleY).toInt()
                    .coerceAtMost(originalBitmap.height)

                // Similar mirroring considerations as in cropAndSaveFaceImage apply here.
                /*
                if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    val mirroredLeft = originalBitmap.width - actualRight
                    val mirroredRight = originalBitmap.width - actualLeft
                    actualLeft = mirroredLeft.coerceAtLeast(0)
                    actualRight = mirroredRight.coerceAtMost(originalBitmap.width)
                }
                */

                val cropWidth = actualRight - actualLeft
                val cropHeight = actualBottom - actualTop

                if (cropWidth <= 0 || cropHeight <= 0) {
                    originalBitmap.recycle()
                    return@withContext null
                }

                val croppedBitmap = Bitmap.createBitmap(
                    originalBitmap, actualLeft, actualTop, cropWidth, cropHeight
                )

                val croppedFileName = "FACE_PADDED_${
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                    ).format(Date())
                }.jpg"
                val croppedFile = File(originalPhotoFile.parent, croppedFileName)

                croppedFile.outputStream().use { outputStream ->
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }

                croppedBitmap.recycle()
                originalBitmap.recycle()

                Log.d(
                    "CameraViewModel",
                    "✅ Padded face saved: ${cropWidth}x${cropHeight} (${(paddingRatio * 100).toInt()}% padding)"
                )

                FileProvider.getUriForFile(context, "${context.packageName}.provider", croppedFile)

            } catch (e: Exception) {
                Log.e("CameraViewModel", "❌ Error cropping face with padding", e)
                null
            }
        }
    }


    /**
     * Calculates basic statistics (min, max, mean, std dev, L2 norm) for a given float array (embedding).
     * @param embedding The float array representing the face embedding.
     * @return An `EmbeddingStats` object containing the calculated statistics.
     */
    private fun calculateEmbeddingStats(embedding: FloatArray): EmbeddingStats {
        val min = embedding.minOrNull() ?: 0f
        val max = embedding.maxOrNull() ?: 0f
        val mean = embedding.average().toFloat()

        val variance = embedding.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance).toFloat()

        val l2Norm = kotlin.math.sqrt(embedding.map { it * it }.sum())

        return EmbeddingStats(min, max, mean, stdDev, l2Norm)
    }

    /**
     * Verifies the quality of a face embedding based on normalization, value range, and non-zero values.
     * Assigns a simple quality score.
     * @param embedding The float array representing the face embedding.
     * @return An `EmbeddingQuality` object containing quality checks and a score.
     */
    private fun verifyEmbeddingQuality(embedding: FloatArray): EmbeddingQuality {
        val l2Norm = kotlin.math.sqrt(embedding.map { it * it }.sum())
        // Check if L2 norm is close to 1.0 (typical for normalized embeddings like FaceNet's)
        val isNormalized = kotlin.math.abs(l2Norm - 1.0f) < 0.1f // Within 10% tolerance

        val min = embedding.minOrNull() ?: 0f
        val max = embedding.maxOrNull() ?: 0f
        // Check if values are within a reasonable range (e.g., -2.0 to 2.0 for FaceNet embeddings)
        val hasValidRange = min >= -2.0f && max <= 2.0f

        // Count non-zero values to ensure the embedding isn't mostly zeros (which would be useless)
        val nonZeroValues = embedding.count { kotlin.math.abs(it) > 0.001f }
        val nonZeroRatio = nonZeroValues.toFloat() / embedding.size

        var qualityScore = 0f

        // Contribution of each factor to the total quality score
        qualityScore += if (isNormalized) 0.3f else 0f // Normalization is important (30%)
        qualityScore += if (hasValidRange) 0.2f else 0f // Valid range (20%)
        qualityScore += (nonZeroRatio * 0.3f) // High ratio of non-zero values (30%)

        val stats = calculateEmbeddingStats(embedding)
        val stdDevScore = kotlin.math.min(stats.stdDev / 0.5f, 1f) // Standard deviation (20%)
        qualityScore += (stdDevScore * 0.2f)

        return EmbeddingQuality(
            isNormalized = isNormalized,
            hasValidRange = hasValidRange,
            nonZeroValues = nonZeroValues,
            qualityScore = qualityScore.coerceIn(0f, 1f) // Cap score between 0 and 1
        )
    }

    /**
     * Closes the face capture registration dialog and resets associated input fields.
     */
    fun closeCaptureDialog() {
        showCaptureDialog.value = false
        userName.value = ""
        userEmail.value = ""
        _capturedFaceUri.value = null // Clear the captured URI
    }

    /**
     * Called when the ViewModel is about to be destroyed.
     * Ensures all resources (FaceAnalyzer, FaceEmbeddingExtractor) are properly closed.
     */
    override fun onCleared() {
        super.onCleared()
        currentAnalyzer?.close()
        Log.d("CameraViewModel", "🧹 ViewModel cleared and resources cleaned up")
    }

    /**
     * Clears the `capturedFaceUri` StateFlow. This is called after navigation
     * to prevent re-triggering navigation if the Composable recomposes.
     */
    fun clearCapturedFaceUri() {
        _capturedFaceUri.value = null
    }

    /**
     * Unbinds all CameraX use cases from the `cameraProvider` and cleans up analyzer resources.
     * This is essential to release camera hardware resources.
     */
    fun unbindCamera() {
        try {
            cameraProvider?.unbindAll() // Unbind all use cases
            currentAnalyzer?.close() // Close the current analyzer
            currentAnalyzer = null // Clear reference
            imageAnalyzer = null // Clear reference
            _surfaceRequest.value = null // Clear surface request
            isCameraBound = false // Update binding flag
            Log.d("CameraViewModel", "📱 Camera unbound successfully")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "❌ Error unbinding camera", e)
        }
    }

    /**
     * Resets all camera and UI-related states to their initial values.
     * Useful for returning to a clean state, e.g., when navigating back to the camera screen.
     */
    fun resetCameraState() {
        unbindCamera() // First, unbind the camera

        // Reset all UI states
        showCaptureDialog.value = false
        userName.value = ""
        userEmail.value = ""
        _capturedFaceUri.value = null
        capturedPhotoUri.value = null
        isProcessingPhoto.value = false
        shutterFlash.value = false
        isSubmitting.value = false
        showPreviewDialog.value = false
        capturedPhotoFile.value = null
        isUploading.value = false

        // Reset face detection states
        _faceBoxes.value = emptyList()
        _livenessStatus.value = LivenessStatus.NO_FACE
        _faceQualityScore.value = 0.0f
        lastDetectedFaceBox = null

        // Reset preview dimensions
        previewWidth = 0.0f
        previewHeight = 0.0f

        Log.d("CameraViewModel", "🔄 Camera state reset completely")
    }
}