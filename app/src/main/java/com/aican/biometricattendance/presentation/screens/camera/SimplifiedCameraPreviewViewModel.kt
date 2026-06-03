package com.aican.biometricattendance.presentation.screens.camera

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.camera.core.*
import androidx.camera.core.MirrorMode.MIRROR_MODE_OFF
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aican.biometricattendance.data.models.camera.FaceBox
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus
import com.aican.biometricattendance.data.models.camera.enums.SimpleFaceStatus
import com.aican.biometricattendance.data.models.enums.CameraType
import com.aican.biometricattendance.data.models.faceembedding.EmbeddingQuality
import com.aican.biometricattendance.data.models.faceembedding.EmbeddingStats
import com.aican.biometricattendance.ml.camera.SimpleFaceAnalyzer
import com.aican.biometricattendance.ml.facenet.FaceProcessingUtils.decodeUprightBitmapFromFile
import com.aican.biometricattendance.ml.facenet.UnifiedFaceEmbeddingProcessor
import com.aican.biometricattendance.presentation.screens.camera.components.CoordinateDebugger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

/**
 * ViewModel for managing the camera preview and face capture logic.
 * Uses a single analyzer (SimpleFaceAnalyzer) for both modes.
 */
class SimplifiedCameraPreviewViewModel : ViewModel() {

    // --- Face Registration Dialog States ---
    val showCaptureDialog = MutableStateFlow(false)
    private val _capturedFaceUri = MutableStateFlow<Uri?>(null)
    val capturedFaceUri: StateFlow<Uri?> = _capturedFaceUri

    val userName = MutableStateFlow("")
    val userEmail = MutableStateFlow("")
    val isSubmitting = MutableStateFlow(false)

    // --- CameraX and Core Camera States ---
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private var cameraProvider: ProcessCameraProvider? = null
    var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var imageCapture: ImageCapture? = null
    private var flashEnabled = false

    // --- UI Visual States ---
    val shutterFlash = MutableStateFlow(false)
    val capturedPhotoUri = MutableStateFlow<Uri?>(null)
    val showPreviewDialog = MutableStateFlow(false)
    val capturedPhotoFile = MutableStateFlow<File?>(null)
    val isUploading = MutableStateFlow<Boolean>(false)
    val isProcessingPhoto = MutableStateFlow(false)

    // --- Face boxes (common) ---
    private val _faceBoxes = MutableStateFlow<List<FaceBox>>(emptyList())
    val faceBoxes: StateFlow<List<FaceBox>> = _faceBoxes

    // --- Liveness states (for ATTENDANCE mode UI) ---
    private val _livenessStatus = MutableStateFlow(LivenessStatus.NO_FACE)
    val livenessStatus: StateFlow<LivenessStatus> = _livenessStatus
    private val _faceQualityScore = MutableStateFlow(0.0f)

    // --- Simple face detection states (for REGISTRATION UI) ---
    private val _simpleFaceStatus = MutableStateFlow(SimpleFaceStatus.NO_FACE)
    val simpleFaceStatus: StateFlow<SimpleFaceStatus> = _simpleFaceStatus

    // --- Analyzer ---
    private var currentSimpleAnalyzer: SimpleFaceAnalyzer? = null

    // --- Common States ---
    private var lastDetectedFaceBox: FaceBox? = null
    private var previewWidth: Float = 0.0F
    private var previewHeight: Float = 0.0F
    private var isCameraBound = false
    private var imageAnalyzer: ImageAnalysis? = null
    private var unifiedFaceEmbeddingProcessor: UnifiedFaceEmbeddingProcessor? = null
    private var _cameraType = CameraType.ATTENDANCE

    fun updateCameraType(type: CameraType) {
        _cameraType = type
        Log.d("CameraViewModel", "Camera type updated to: $type")
    }

    /**
     * Returns the current face quality score based on camera type.
     */
    fun getFaceQualityScore(): Float {
        return when (_cameraType) {
            CameraType.REGISTRATION -> {
                when (_simpleFaceStatus.value) {
                    SimpleFaceStatus.FACE_DETECTED -> 0.8f
                    SimpleFaceStatus.POOR_QUALITY -> 0.4f
                    SimpleFaceStatus.NO_FACE -> 0.0f
                }
            }
            CameraType.ATTENDANCE -> {
                currentSimpleAnalyzer?.getFaceQualityScore() ?: 0.0f
            }
        }
    }

    fun updatePreviewSize(width: Float, height: Float) {
        if (previewWidth != width || previewHeight != height) {
            previewWidth = width
            previewHeight = height
            updateImageAnalyzer()
        }
    }

    /**
     * For ATTENDANCE mode, boxes + liveness from analyzer getters.
     */
    fun updateFaceBoxes(boxes: List<FaceBox>) {
        viewModelScope.launch {
            _faceBoxes.emit(boxes)
            currentSimpleAnalyzer?.let { analyzer ->
                _livenessStatus.emit(analyzer.getCurrentLivenessStatus())
                _faceQualityScore.emit(analyzer.getFaceQualityScore())
            }
            if (boxes.isNotEmpty()) lastDetectedFaceBox = boxes.last()
        }
    }

    /**
     * For REGISTRATION mode, simple status path.
     */
    fun updateSimpleFaceBoxes(boxes: List<FaceBox>, status: SimpleFaceStatus) {
        viewModelScope.launch {
            _faceBoxes.emit(boxes)
            _simpleFaceStatus.emit(status)
            if (boxes.isNotEmpty()) lastDetectedFaceBox = boxes.first()
            Log.d("CameraViewModel", "Simple face status: $status, boxes: ${boxes.size}")
        }
    }

    private fun updateImageAnalyzer() {
        if (previewWidth > 0 && previewHeight > 0) {
            currentSimpleAnalyzer?.close()
        }
    }

    /**
     * Creates the ImageAnalysis use case (single analyzer for both modes).
     */
    private fun createImageAnalyzer(context: Context): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also { analyzer ->
                val isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
                val enableLiveness = (_cameraType == CameraType.ATTENDANCE)

                val simple = SimpleFaceAnalyzer(
                    context = context,
                    previewWidth = previewWidth,
                    previewHeight = previewHeight,
                    isMirrored = isFrontCamera,
                    enableLiveness = enableLiveness
                ) { boxes, status ->
                    if (enableLiveness) {
                        updateFaceBoxes(boxes)
                    } else {
                        updateSimpleFaceBoxes(boxes, status)
                    }
                }

                currentSimpleAnalyzer = simple
                analyzer.setAnalyzer(ContextCompat.getMainExecutor(context), simple)
            }
    }

    /**
     * Binds CameraX use cases to the lifecycle owner.
     */
    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        debugCameraState("BIND_START")

        try {
            cameraProvider?.unbindAll()
            debugCameraState("AFTER_UNBIND")

            if (cameraProvider == null) {
                cameraProvider = ProcessCameraProvider.awaitInstance(appContext)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()

            val newPreviewUseCase = Preview.Builder()
                .setMirrorMode(MIRROR_MODE_OFF)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .apply {
                    setSurfaceProvider { newSurfaceRequest ->
                        _surfaceRequest.value = newSurfaceRequest
                    }
                }

            Log.d("CameraViewModel", "Preview size: $previewWidth x $previewHeight")
            imageAnalyzer = if (previewWidth > 0 && previewHeight > 0) {
                Log.d("CameraViewModel", "Creating analyzer for ${_cameraType}")
                createImageAnalyzer(appContext)
            } else null

            val useCases = mutableListOf<UseCase>().apply {
                add(newPreviewUseCase)
                add(imageCapture!!)
                imageAnalyzer?.let { add(it) }
            }

            Log.d("CameraViewModel", "Binding to camera with ${useCases.size} use cases")

            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )

            isCameraBound = true
            Log.d("CameraViewModel", "Camera bound successfully for ${_cameraType}")
            debugCameraState("BIND_SUCCESS")

            awaitCancellation()
        } catch (e: Exception) {
            Log.e("Camera", "Binding failed", e)
            isCameraBound = false
            debugCameraState("BIND_FAILED")
            throw e
        }
    }

    fun isFaceSuitableForCapture(): Boolean {
        return when (_cameraType) {
            CameraType.REGISTRATION -> {
                val hasValidFace = _faceBoxes.value.isNotEmpty()
                val faceDetected = _simpleFaceStatus.value == SimpleFaceStatus.FACE_DETECTED
                Log.d("CameraViewModel", """
                    Simple face capture check:
                    - Has valid face: $hasValidFace
                    - Face detected: $faceDetected
                    - Suitable: ${hasValidFace && faceDetected}
                """.trimIndent())
                hasValidFace && faceDetected
            }
            CameraType.ATTENDANCE -> {
                val hasValidFace = _faceBoxes.value.isNotEmpty()
                val isLive = currentSimpleAnalyzer?.isFaceSuitableForCapture() ?: false
                val currentStatus = _livenessStatus.value
                Log.d("CameraViewModel", """
                    Complex face capture check:
                    - Has valid face: $hasValidFace
                    - Is live: $isLive
                    - Current status: $currentStatus
                    - Suitable: ${hasValidFace && isLive && currentStatus == LivenessStatus.LIVE_FACE}
                """.trimIndent())
                hasValidFace && isLive && currentStatus == LivenessStatus.LIVE_FACE
            }
        }
    }

    fun capturePhoto(
        context: Context,
        onSaved: (Uri) -> Unit = {},
    ) {
        if (!isFaceSuitableForCapture()) {
            Log.w("CameraViewModel", "Cannot capture: Face not suitable for ${_cameraType}")
            return
        }

        val currentFaceBox = lastDetectedFaceBox
        if (currentFaceBox == null) {
            Log.w("CameraViewModel", "Cannot capture: No face box detected")
            return
        }

        isProcessingPhoto.value = true

        val imageCapture = imageCapture ?: run {
            isProcessingPhoto.value = false
            return
        }

        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val disposableFolder = File(picturesDir, "Attendance Photos")
        if (!disposableFolder.exists()) disposableFolder.mkdirs()

        val photoFile = File(
            disposableFolder,
            "TEMP_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        shutterFlash.value = true

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    viewModelScope.launch {
                        try {
                            val croppedUri = withContext(Dispatchers.IO) {
                                cropAndSaveFaceImage(photoFile, currentFaceBox, context)
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

                                _capturedFaceUri.value = croppedUri
                                showCaptureDialog.value = true
                                onSaved(croppedUri)

                                Log.d("CameraViewModel", "Cropped face image saved successfully for ${_cameraType}")
                            } else {
                                Log.e("CameraViewModel", "Failed to crop face image")
                            }
                        } catch (e: Exception) {
                            Log.e("CameraViewModel", "Error processing captured image", e)
                        } finally {
                            isProcessingPhoto.value = false
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isProcessingPhoto.value = false
                    Log.e("Camera", "Photo capture failed: ${exception.message}", exception)
                }
            }
        )

        CoroutineScope(Dispatchers.Main).launch {
            delay(150)
            shutterFlash.value = false
        }
    }

    private fun mapPreviewBoxToImage(
        faceBox: FaceBox,
        previewW: Float,
        previewH: Float,
        imageW: Int,
        imageH: Int
    ): android.graphics.Rect {
        // How much the image was scaled to fill the preview (CENTER_CROP)
        val s = max(previewW / imageW, previewH / imageH)
        val dispW = imageW * s
        val dispH = imageH * s

        // Where the displayed image sits inside the preview (can be negative)
        val offX = (previewW - dispW) / 2f
        val offY = (previewH - dispH) / 2f

        // Inverse: (previewXY - offset) / s  -> image space
        val left   = ((faceBox.left   - offX) / s).toInt().coerceIn(0, imageW)
        val top    = ((faceBox.top    - offY) / s).toInt().coerceIn(0, imageH)
        val right  = ((faceBox.right  - offX) / s).toInt().coerceIn(0, imageW)
        val bottom = ((faceBox.bottom - offY) / s).toInt().coerceIn(0, imageH)

        return android.graphics.Rect(left, top, right, bottom)
    }

    /**
     * Crops the original captured photo to the detected face region and saves it as a new file.
     * Uses CENTER_CROP inverse mapping so the crop matches what you saw on the preview.
     */
    private suspend fun cropAndSaveFaceImage(
        originalPhotoFile: File,
        faceBox: FaceBox,
        context: Context,
    ): Uri? {
        return withContext(Dispatchers.IO) { // Perform on IO dispatcher for heavy operations
            try {
//                val originalBitmap = BitmapFactory.decodeFile(originalPhotoFile.absolutePath)
//                    ?: return@withContext null // Decode the full image
                val originalBitmap = decodeUprightBitmapFromFile(originalPhotoFile)

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

    // ---- Embedding helpers (unchanged) ----
    private fun calculateEmbeddingStats(embedding: FloatArray): EmbeddingStats {
        val min = embedding.minOrNull() ?: 0f
        val max = embedding.maxOrNull() ?: 0f
        val mean = embedding.average().toFloat()
        val variance = embedding.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance).toFloat()
        val l2Norm = kotlin.math.sqrt(embedding.map { it * it }.sum())
        return EmbeddingStats(min, max, mean, stdDev, l2Norm)
    }

    private fun verifyEmbeddingQuality(embedding: FloatArray): EmbeddingQuality {
        val l2Norm = kotlin.math.sqrt(embedding.map { it * it }.sum())
        val isNormalized = kotlin.math.abs(l2Norm - 1.0f) < 0.1f
        val min = embedding.minOrNull() ?: 0f
        val max = embedding.maxOrNull() ?: 0f
        val hasValidRange = min >= -2.0f && max <= 2.0f
        val nonZeroValues = embedding.count { kotlin.math.abs(it) > 0.001f }
        val nonZeroRatio = nonZeroValues.toFloat() / embedding.size

        var qualityScore = 0f
        qualityScore += if (isNormalized) 0.3f else 0f
        qualityScore += if (hasValidRange) 0.2f else 0f
        qualityScore += (nonZeroRatio * 0.3f)

        val stats = calculateEmbeddingStats(embedding)
        val stdDevScore = kotlin.math.min(stats.stdDev / 0.5f, 1f)
        qualityScore += (stdDevScore * 0.2f)

        return EmbeddingQuality(
            isNormalized = isNormalized,
            hasValidRange = hasValidRange,
            nonZeroValues = nonZeroValues,
            qualityScore = qualityScore.coerceIn(0f, 1f)
        )
    }
    // ---------------------------------------

    fun closeCaptureDialog() {
        showCaptureDialog.value = false
        userName.value = ""
        userEmail.value = ""
        _capturedFaceUri.value = null
    }

    override fun onCleared() {
        super.onCleared()
        currentSimpleAnalyzer?.close()
        Log.d("CameraViewModel", "ViewModel cleared and resources cleaned up")
    }

    fun clearCapturedFaceUri() {
        _capturedFaceUri.value = null
    }

    fun unbindCamera() {
        try {
            cameraProvider?.unbindAll()
            currentSimpleAnalyzer?.close()
            currentSimpleAnalyzer = null
            imageAnalyzer = null
            _surfaceRequest.value = null
            isCameraBound = false
            Log.d("CameraViewModel", "Camera unbound successfully for ${_cameraType}")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error unbinding camera", e)
        }
    }

    fun resetCameraState() {
        unbindCamera()
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
        _faceBoxes.value = emptyList()
        lastDetectedFaceBox = null
        previewWidth = 0.0f
        previewHeight = 0.0f

        when (_cameraType) {
            CameraType.REGISTRATION -> _simpleFaceStatus.value = SimpleFaceStatus.NO_FACE
            CameraType.ATTENDANCE -> {
                _livenessStatus.value = LivenessStatus.NO_FACE
                _faceQualityScore.value = 0.0f
            }
        }

        Log.d("CameraViewModel", "Camera state reset completely for ${_cameraType}")
    }

    fun toggleCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        updateImageAnalyzer()
        Log.d(
            "CameraViewModel",
            "Camera toggled - Now using: ${if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) "FRONT" else "BACK"}"
        )
    }

    fun toggleFlash() {
        flashEnabled = !flashEnabled
        imageCapture?.flashMode = if (flashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    fun debugCameraState(context: String) {
        Log.d("CameraViewModel", """
        DEBUG CAMERA STATE [$context]:
        - previewWidth: $previewWidth
        - previewHeight: $previewHeight  
        - isCameraBound: $isCameraBound
        - cameraProvider: ${cameraProvider != null}
        - imageCapture: ${imageCapture != null}
        - imageAnalyzer: ${imageAnalyzer != null}
        - currentSimpleAnalyzer: ${currentSimpleAnalyzer != null}
        - surfaceRequest: ${_surfaceRequest.value != null}
        - cameraSelector: ${if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) "FRONT" else "BACK"}
        - cameraType: ${_cameraType}
        """.trimIndent())
    }
}
