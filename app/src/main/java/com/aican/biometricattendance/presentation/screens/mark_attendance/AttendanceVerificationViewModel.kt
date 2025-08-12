package com.aican.biometricattendance.presentation.screens.mark_attendance

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MirrorMode.MIRROR_MODE_OFF
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aican.biometricattendance.data.db.converters.Converters
import com.aican.biometricattendance.data.db.entity.AttendanceEntity
import com.aican.biometricattendance.data.db.entity.AttendanceEventType
import com.aican.biometricattendance.data.db.entity.FaceEmbeddingEntity
import com.aican.biometricattendance.data.db.repository.AttendanceRepository
import com.aican.biometricattendance.data.db.repository.FaceEmbeddingRepository
import com.aican.biometricattendance.data.models.camera.FaceBox
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus
import com.aican.biometricattendance.ml.camera.FaceAnalyzer
import com.aican.biometricattendance.ml.facenet.UnifiedFaceEmbeddingProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AttendanceVerificationViewModel(
    private val faceEmbeddingRepository: FaceEmbeddingRepository,
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    var faceEmbeddingProcessor: UnifiedFaceEmbeddingProcessor? = null

    private val _faceEmbeddedDataFromDatabase = MutableStateFlow<FaceEmbeddingEntity?>(null)
    val faceEmbeddedDataFromDatabase: StateFlow<FaceEmbeddingEntity?> =
        _faceEmbeddedDataFromDatabase

    // --- MediaPipe Face Detection States ---
    private val _faceBoxes =
        MutableStateFlow<List<FaceBox>>(emptyList())
    val faceBoxes: StateFlow<List<FaceBox>> = _faceBoxes
    private var currentAnalyzer: FaceAnalyzer? = null

    private var previewWidth: Float = 0.0F
    private var previewHeight: Float = 0.0F
    private val _faceQualityScore =
        MutableStateFlow(0.0f)

    private val _livenessStatus =
        MutableStateFlow(LivenessStatus.NO_FACE)
    val livenessStatus: StateFlow<LivenessStatus> =
        _livenessStatus

    private var lastDetectedFaceBox: FaceBox? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var flashEnabled = false

    // --- Auto Capture and Comparison States ---
    private val _isAutoProcessing = MutableStateFlow(false)
    val isAutoProcessing: StateFlow<Boolean> = _isAutoProcessing.asStateFlow()

    private val _similarityScore = MutableStateFlow(0.0f)
    val similarityScore: StateFlow<Float> = _similarityScore.asStateFlow()

    private val _attendanceResult = MutableStateFlow<AttendanceResult?>(null)
    val attendanceResult: StateFlow<AttendanceResult?> = _attendanceResult.asStateFlow()

    private val _captureStatus = MutableStateFlow<CaptureStatus>(CaptureStatus.IDLE)
    val captureStatus: StateFlow<CaptureStatus> = _captureStatus.asStateFlow()

    // Auto capture configuration
    private val SIMILARITY_THRESHOLD = 0.60f // Adjust based on your requirements
    private val QUALITY_THRESHOLD = 0.6f
    private val AUTO_CAPTURE_DELAY = 2000L // 2 seconds delay for stable face
    private var lastCaptureTime = 0L
    private val MIN_CAPTURE_INTERVAL = 3000L // 3 seconds between captures

    // Stability tracking
    private var stableFaceCount = 0
    private val REQUIRED_STABLE_FRAMES = 30 // ~1 second at 30fps
    private var isStabilityCheckActive = false

    // --- CameraX and Core Camera States ---
    private val _surfaceRequest =
        MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> =
        _surfaceRequest

    private var imageAnalyzer: ImageAnalysis? = null
    var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var isCameraBound = false

    val isProcessingPhoto = MutableStateFlow(false)
    val shutterFlash = MutableStateFlow(false)

    init {
        // Initialize face embedding processor
        initializeFaceProcessor()
    }

    private fun initializeFaceProcessor() {
        viewModelScope.launch {
            try {
                // We'll initialize this when context is available in bindToCamera
                Log.d(TAG, "Face processor will be initialized when camera binds")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing face processor", e)
            }
        }
    }

    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        try {
            // Initialize face embedding processor if not already done
            if (faceEmbeddingProcessor == null) {
                faceEmbeddingProcessor = UnifiedFaceEmbeddingProcessor(appContext)
                Log.d(TAG, "Face embedding processor initialized")
            }

            cameraProvider?.unbindAll()

            if (cameraProvider == null) {
                cameraProvider = ProcessCameraProvider.awaitInstance(appContext)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(
                    if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                )
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()

            val newPreviewUseCase = Preview.Builder()
                .setMirrorMode(MIRROR_MODE_OFF)
                .build()
                .apply {
                    setSurfaceProvider { newSurfaceRequest ->
                        _surfaceRequest.value = newSurfaceRequest
                    }
                }

            Log.d(TAG, "updatePreviewSize: $previewWidth x $previewHeight")
            imageAnalyzer = if (previewWidth > 0 && previewHeight > 0) {
                Log.d(TAG, "Creating new analyzer")
                createImageAnalyzer(appContext)
            } else {
                null
            }

            val useCases = mutableListOf<UseCase>().apply {
                add(newPreviewUseCase)
                add(imageCapture!!)
                imageAnalyzer?.let { add(it) }
            }

            Log.d(TAG, "Binding to camera with ${useCases.size} use cases")

            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )

            isCameraBound = true
            Log.d(TAG, "✅ Camera bound successfully with ${useCases.size} use cases")

            awaitCancellation()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Binding failed", e)
            isCameraBound = false
            throw e
        }
    }

    private fun createImageAnalyzer(context: Context): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also { analyzer ->
                val isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA

                val faceAnalyzer = FaceAnalyzer(
                    context = context,
                    previewWidth = previewWidth,
                    previewHeight = previewHeight,
                    isMirrored = isFrontCamera
                ) { boxes ->
                    updateFaceBoxes(boxes)
                    // Trigger auto capture logic when faces are detected
                    checkForAutoCapture(context)
                }

                currentAnalyzer = faceAnalyzer
                Log.d(TAG, "Face analyzer created")

                analyzer.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    faceAnalyzer
                )
            }
    }

    fun getFaceEmbeddingFromDatabase(id: String) {
        viewModelScope.launch {
            val result = faceEmbeddingRepository.findByEmployeeId(id)
            Log.d(TAG, "Face embedding from database: $result")
            _faceEmbeddedDataFromDatabase.value = result
        }
    }

    fun updatePreviewSize(width: Float, height: Float) {
        if (previewWidth != width || previewHeight != height) {
            previewWidth = width
            previewHeight = height
            updateImageAnalyzer()
        }
    }

    fun updateFaceBoxes(boxes: List<FaceBox>) {
        viewModelScope.launch {
            _faceBoxes.emit(boxes)

            currentAnalyzer?.let { analyzer ->
                val newStatus = analyzer.getCurrentLivenessStatus()
                _livenessStatus.emit(newStatus)
                _faceQualityScore.emit(analyzer.getFaceQualityScore())

                if (newStatus == LivenessStatus.FACE_TOO_CLOSE_TO_EDGE) {
                    Log.d(TAG, "Face too close to edge detected")
                }
            }

            if (boxes.isNotEmpty()) {
                lastDetectedFaceBox = boxes.first()
            }
        }
    }

    private fun checkForAutoCapture(context: Context) {
        val currentTime = System.currentTimeMillis()

        // Check if enough time has passed since last capture
        if (currentTime - lastCaptureTime < MIN_CAPTURE_INTERVAL) {
            return
        }

        // Check if we're already processing
        if (_isAutoProcessing.value || _captureStatus.value == CaptureStatus.PROCESSING) {
            return
        }

        // Check face conditions
        val currentStatus = _livenessStatus.value
        val qualityScore = getFaceQualityScore()
        val hasValidFace = _faceBoxes.value.isNotEmpty()

        if (hasValidFace &&
            currentStatus == LivenessStatus.LIVE_FACE &&
            qualityScore >= QUALITY_THRESHOLD
        ) {

            // Increment stable face count
            stableFaceCount++

            // Check if face has been stable for required frames
            if (stableFaceCount >= REQUIRED_STABLE_FRAMES && !isStabilityCheckActive) {
                isStabilityCheckActive = true

                viewModelScope.launch {
                    delay(AUTO_CAPTURE_DELAY)

                    // Double check conditions after delay
                    if (_livenessStatus.value == LivenessStatus.LIVE_FACE &&
                        getFaceQualityScore() >= QUALITY_THRESHOLD &&
                        _faceBoxes.value.isNotEmpty()
                    ) {

                        performAutoCapture(context)
                    }

                    isStabilityCheckActive = false
                }
            }
        } else {
            // Reset stability counter if conditions are not met
            stableFaceCount = 0
            isStabilityCheckActive = false
        }
    }

    private fun performAutoCapture(context: Context) {
        lastCaptureTime = System.currentTimeMillis()

        viewModelScope.launch {
            _captureStatus.emit(CaptureStatus.CAPTURING)
            _isAutoProcessing.emit(true)

            try {
                // Trigger shutter flash effect
                shutterFlash.value = true
                delay(100)
                shutterFlash.value = false

                // Capture image and process
                captureAndProcessFace(context)

            } catch (e: Exception) {
                Log.e(TAG, "Error in auto capture", e)
                _captureStatus.emit(CaptureStatus.ERROR)
                _attendanceResult.emit(
                    AttendanceResult(
                        success = false,
                        similarity = 0.0f,
                        message = "Capture failed: ${e.message}"
                    )
                )
            } finally {
                _isAutoProcessing.emit(false)
            }
        }
    }

    private suspend fun captureAndProcessFace(context: Context) {
        val imageCapture = this.imageCapture ?: run {
            Log.e(TAG, "ImageCapture not initialized")
            _captureStatus.emit(CaptureStatus.ERROR)
            return
        }

        val faceBox = lastDetectedFaceBox ?: run {
            Log.e(TAG, "No face box available for cropping")
            _captureStatus.emit(CaptureStatus.ERROR)
            return
        }

        try {
            _captureStatus.emit(CaptureStatus.PROCESSING)

            withContext(Dispatchers.IO) {
                // Capture image using ImageCapture.OnImageCapturedCallback
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            viewModelScope.launch {
                                try {
                                    // Convert ImageProxy to Bitmap
                                    val bitmap = imageProxyToBitmap(image)

                                    if (bitmap != null) {
                                        processCaptureBitmap(bitmap, faceBox)
                                    } else {
                                        _captureStatus.emit(CaptureStatus.ERROR)
                                        _attendanceResult.emit(
                                            AttendanceResult(
                                                success = false,
                                                similarity = 0.0f,
                                                message = "Failed to convert captured image"
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing captured image", e)
                                    _captureStatus.emit(CaptureStatus.ERROR)
                                } finally {
                                    image.close()
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Image capture failed", exception)
                            viewModelScope.launch {
                                _captureStatus.emit(CaptureStatus.ERROR)
                                _attendanceResult.emit(
                                    AttendanceResult(
                                        success = false,
                                        similarity = 0.0f,
                                        message = "Image capture failed"
                                    )
                                )
                            }
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in capture process", e)
            _captureStatus.emit(CaptureStatus.ERROR)
            _attendanceResult.emit(
                AttendanceResult(
                    success = false,
                    similarity = 0.0f,
                    message = "Capture error: ${e.message}"
                )
            )
        }
    }

    private suspend fun processCaptureBitmap(bitmap: Bitmap, faceBox: FaceBox) {
        withContext(Dispatchers.IO) {
            try {
                // Crop face from the captured bitmap
                val croppedFace = cropFaceFromBitmap(bitmap, faceBox)

                if (croppedFace == null) {
                    _captureStatus.emit(CaptureStatus.ERROR)
                    _attendanceResult.emit(
                        AttendanceResult(
                            success = false,
                            similarity = 0.0f,
                            message = "Failed to crop face from image"
                        )
                    )
                    return@withContext
                }

                // Generate embedding using UnifiedFaceEmbeddingProcessor
                val processor = faceEmbeddingProcessor
                if (processor == null) {
                    _captureStatus.emit(CaptureStatus.ERROR)
                    _attendanceResult.emit(
                        AttendanceResult(
                            success = false,
                            similarity = 0.0f,
                            message = "Face processor not initialized"
                        )
                    )
                    return@withContext
                }

                val embeddingResult = processor.generateEmbedding(croppedFace)

                if (!embeddingResult.success || embeddingResult.embedding == null) {
                    _captureStatus.emit(CaptureStatus.ERROR)
                    _attendanceResult.emit(
                        AttendanceResult(
                            success = false,
                            similarity = 0.0f,
                            message = embeddingResult.error ?: "Failed to generate embedding"
                        )
                    )
                    return@withContext
                }

                // Compare with database embedding
                val databaseEmbedding = _faceEmbeddedDataFromDatabase.value
                if (databaseEmbedding?.embedding == null) {
                    _captureStatus.emit(CaptureStatus.ERROR)
                    _attendanceResult.emit(
                        AttendanceResult(
                            success = false,
                            similarity = 0.0f,
                            message = "No registered face found for comparison"
                        )
                    )
                    return@withContext
                }

                // Convert stored embedding back to FloatArray
                val storedEmbedding = Converters().toFloatArray(databaseEmbedding.embedding!!)

                // Calculate similarity
                val similarity = processor.calculateSimilarity(
                    embeddingResult.embedding,
                    storedEmbedding
                )

                _similarityScore.emit(similarity)
                _captureStatus.emit(CaptureStatus.COMPLETED)

                // Determine if attendance is successful
                val isMatch = similarity >= SIMILARITY_THRESHOLD

                _attendanceResult.emit(
                    AttendanceResult(
                        success = isMatch,
                        similarity = similarity,
                        message = if (isMatch) {
                            "Face verified successfully! Similarity: ${(similarity * 100).toInt()}%"
                        } else {
                            "Face verification failed. Similarity: ${(similarity * 100).toInt()}%"
                        }
                    )
                )

                Log.d(TAG, "Face comparison completed. Similarity: $similarity, Match: $isMatch")

            } catch (e: Exception) {
                Log.e(TAG, "Error processing face", e)
                _captureStatus.emit(CaptureStatus.ERROR)
                _attendanceResult.emit(
                    AttendanceResult(
                        success = false,
                        similarity = 0.0f,
                        message = "Processing error: ${e.message}"
                    )
                )
            }
        }
    }

    private fun cropFaceFromBitmap(bitmap: Bitmap, faceBox: FaceBox): Bitmap? {
        return try {
            val left = maxOf(0, faceBox.left.toInt())
            val top = maxOf(0, faceBox.top.toInt())
            val right = minOf(bitmap.width, faceBox.right.toInt())
            val bottom = minOf(bitmap.height, faceBox.bottom.toInt())

            val width = right - left
            val height = bottom - top

            if (width <= 0 || height <= 0) {
                Log.e(TAG, "Invalid face bounds: width=$width, height=$height")
                return null
            }

            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping face", e)
            null
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            // Handle different image formats
            when (image.format) {
                android.graphics.ImageFormat.YUV_420_888 -> {
                    // Convert YUV to RGB
                    yuvToRgbBitmap(image)
                }

                android.graphics.ImageFormat.JPEG -> {
                    // Direct JPEG decoding
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }

                else -> {
                    Log.w(TAG, "Unsupported image format: ${image.format}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e)
            null
        }
    }

    private fun yuvToRgbBitmap(image: ImageProxy): Bitmap? {
        return try {
            val yBuffer = image.planes[0].buffer // Y
            val uBuffer = image.planes[1].buffer // U
            val vBuffer = image.planes[2].buffer // V

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                image.width,
                image.height,
                null
            )

            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, image.width, image.height),
                100,
                out
            )
            val imageBytes = out.toByteArray()
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting YUV to RGB", e)
            null
        }
    }

    fun getFaceQualityScore(): Float {
        return currentAnalyzer?.getFaceQualityScore() ?: 0.0f
    }

    private fun updateImageAnalyzer() {
        if (previewWidth > 0 && previewHeight > 0) {
            currentAnalyzer?.close()
        }
    }

    fun unbindCamera() {
        try {
            cameraProvider?.unbindAll()
            currentAnalyzer?.close()
            currentAnalyzer = null
            imageAnalyzer = null
            _surfaceRequest.value = null
            isCameraBound = false
            Log.d(TAG, "📱 Camera unbound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error unbinding camera", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        faceEmbeddingProcessor?.close()
    }

    companion object {
        private const val TAG = "AttendanceVerificationVM"
    }

    fun resetVerificationState() {
        _similarityScore.value = 0f
        _isAutoProcessing.value = false
        _attendanceResult.value = null
        _captureStatus.value = CaptureStatus.IDLE
        _faceBoxes.value = emptyList()
        _livenessStatus.value = LivenessStatus.NO_FACE
        shutterFlash.value = false
        stableFaceCount = 0
        isStabilityCheckActive = false
        lastCaptureTime = 0L
    }


    /// marking status

    val lastEvent = MutableStateFlow<AttendanceEntity?>(null)


    fun fetchLastEvent(employeeId: String) {
        viewModelScope.launch {
            lastEvent.value = attendanceRepository.getLastEvent(employeeId)
        }
    }

    fun markAttendance(
        employeeId: String,
        eventType: AttendanceEventType,
        matchPercent: Float
    ) {
        viewModelScope.launch {
            val newEntry = AttendanceEntity(
                employeeId = employeeId,
                timestamp = System.currentTimeMillis(),
                eventType = eventType,
                matchPercent = matchPercent
            )
            attendanceRepository.insert(newEntry)
            lastEvent.value = newEntry
        }
    }


}

// Data classes for attendance results
data class AttendanceResult(
    val success: Boolean,
    val similarity: Float,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class CaptureStatus {
    IDLE,
    CAPTURING,
    PROCESSING,
    COMPLETED,
    ERROR
}