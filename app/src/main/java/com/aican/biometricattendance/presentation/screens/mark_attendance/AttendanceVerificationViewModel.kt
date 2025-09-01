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
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

class AttendanceVerificationViewModel(
    private val faceEmbeddingRepository: FaceEmbeddingRepository,
    private val attendanceRepository: AttendanceRepository,
) : ViewModel() {

    var faceEmbeddingProcessor: UnifiedFaceEmbeddingProcessor? = null

    // StateFlow to hold the list of ALL registered embeddings in memory
    private val _allRegisteredEmbeddings = MutableStateFlow<List<FaceEmbeddingEntity>>(emptyList())
    val allRegisteredEmbeddings: StateFlow<List<FaceEmbeddingEntity>> = _allRegisteredEmbeddings

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
    private val SIMILARITY_THRESHOLD = 0.80f
    private val QUALITY_THRESHOLD = 0.6f
    private val AUTO_CAPTURE_DELAY = 1500L // Reduced delay for faster capture
    private var lastCaptureTime = 0L
    private val MIN_CAPTURE_INTERVAL = 3000L

    // Stability tracking
    private var stableFaceCount = 0
    private val REQUIRED_STABLE_FRAMES = 20 // Reduced for faster stability check
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
        // Load all registered faces from the database into memory
        loadAllRegisteredEmbeddings()
        initializeFaceProcessor()
    }

    private fun initializeFaceProcessor() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Face processor will be initialized when camera binds")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing face processor", e)
            }
        }
    }

    private fun loadAllRegisteredEmbeddings() {
        viewModelScope.launch {
            // Assumes you have a getAll() method in your repository/DAO
            val allUsers = faceEmbeddingRepository.getAll()
            _allRegisteredEmbeddings.value = allUsers
            Log.d(TAG, "Loaded ${allUsers.size} registered faces into memory.")
        }
    }

    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        try {
            if (faceEmbeddingProcessor == null) {
                faceEmbeddingProcessor = UnifiedFaceEmbeddingProcessor(appContext)
                Log.d(TAG, "Face embedding processor initialized")
            }

            cameraProvider?.unbindAll()

            if (cameraProvider == null) {
                cameraProvider = ProcessCameraProvider.awaitInstance(appContext)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
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

            imageAnalyzer = if (previewWidth > 0 && previewHeight > 0) {
                createImageAnalyzer(appContext)
            } else {
                null
            }

            val useCases = mutableListOf<UseCase>().apply {
                add(newPreviewUseCase)
                add(imageCapture!!)
                imageAnalyzer?.let { add(it) }
            }

            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )

            isCameraBound = true
            Log.d(TAG, "✅ Camera bound successfully")

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
                    checkForAutoCapture(context)
                }
                currentAnalyzer = faceAnalyzer
                analyzer.setAnalyzer(ContextCompat.getMainExecutor(context), faceAnalyzer)
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
                _livenessStatus.emit(analyzer.getCurrentLivenessStatus())
                _faceQualityScore.emit(analyzer.getFaceQualityScore())
            }
            if (boxes.isNotEmpty()) {
                lastDetectedFaceBox = boxes.first()
            }
        }
    }

    private fun checkForAutoCapture(context: Context) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCaptureTime < MIN_CAPTURE_INTERVAL || _isAutoProcessing.value || _captureStatus.value == CaptureStatus.PROCESSING) {
            return
        }

        val currentStatus = _livenessStatus.value
        val qualityScore = getFaceQualityScore()
        val hasValidFace = _faceBoxes.value.isNotEmpty()

        if (hasValidFace && currentStatus == LivenessStatus.LIVE_FACE && qualityScore >= QUALITY_THRESHOLD) {
            stableFaceCount++
            if (stableFaceCount >= REQUIRED_STABLE_FRAMES && !isStabilityCheckActive) {
                isStabilityCheckActive = true
                viewModelScope.launch {
                    delay(AUTO_CAPTURE_DELAY)
                    if (_livenessStatus.value == LivenessStatus.LIVE_FACE && getFaceQualityScore() >= QUALITY_THRESHOLD && _faceBoxes.value.isNotEmpty()) {
                        performAutoCapture(context)
                    }
                    isStabilityCheckActive = false
                }
            }
        } else {
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
                shutterFlash.value = true
                delay(100)
                shutterFlash.value = false
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
        val imageCapture = this.imageCapture ?: return
        val faceBox = lastDetectedFaceBox ?: return

        _captureStatus.emit(CaptureStatus.PROCESSING)
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    viewModelScope.launch {
                        try {
                            val bitmap = imageProxyToBitmap(image)
                            if (bitmap != null) {
                                processCaptureBitmap(bitmap, faceBox)
                            } else {
                                _captureStatus.emit(CaptureStatus.ERROR)
                                _attendanceResult.emit(
                                    AttendanceResult(
                                        success = false,
                                        similarity = 0.0f,
                                        message = "Failed to convert image"
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

    // #################################################################
    // ## CORE 1:N IDENTIFICATION LOGIC IS IMPLEMENTED HERE           ##
    // #################################################################
    private suspend fun processCaptureBitmap(bitmap: Bitmap, faceBox: FaceBox) {
        withContext(Dispatchers.IO) {
            try {
                // Step 1: Crop face from the full image
                val croppedFace = cropFaceFromBitmap(bitmap, faceBox)
                if (croppedFace == null) {
                    _captureStatus.emit(CaptureStatus.ERROR)
                    _attendanceResult.emit(
                        AttendanceResult(
                            success = false,
                            similarity = 0.0f,
                            message = "Failed to crop face"
                        )
                    )
                    return@withContext
                }

                // Step 2: Generate embedding for the live face
                val processor = faceEmbeddingProcessor!!
                val embeddingResult = processor.generateEmbedding(croppedFace)
                if (!embeddingResult.success || embeddingResult.embedding == null) {
                    _captureStatus.emit(CaptureStatus.ERROR)
                    _attendanceResult.emit(
                        AttendanceResult(
                            success = false,
                            similarity = 0.0f,
                            message = embeddingResult.error ?: "Embedding failed"
                        )
                    )
                    return@withContext
                }

                // Step 3: Check if any faces are registered in the database
                val registeredFaces = _allRegisteredEmbeddings.value
                if (registeredFaces.isEmpty()) {
                    _captureStatus.emit(CaptureStatus.ERROR)
                    _attendanceResult.emit(
                        AttendanceResult(
                            success = false,
                            similarity = 0.0f,
                            message = "No faces registered in system"
                        )
                    )
                    return@withContext
                }

                // Step 4: Perform the 1:N Search
                var bestMatch: FaceEmbeddingEntity? = null
                var highestSimilarity = 0.0f
                val liveEmbedding = embeddingResult.embedding

                for (registeredFace in registeredFaces) {
                    if (registeredFace.embedding == null) continue
                    val storedEmbedding = Converters().toFloatArray(registeredFace.embedding)
                    val similarity = processor.calculateSimilarity(liveEmbedding, storedEmbedding)

                    if (similarity > highestSimilarity) {
                        highestSimilarity = similarity
                        bestMatch = registeredFace
                    }
                }

                // Step 5: Make a decision based on the best match found
                _similarityScore.emit(highestSimilarity)
                _captureStatus.emit(CaptureStatus.COMPLETED)

                val isMatch = highestSimilarity >= SIMILARITY_THRESHOLD && bestMatch != null

                if (isMatch) {
                    // SUCCESS: We identified someone
                    _attendanceResult.emit(
                        AttendanceResult(
                            success = true,
                            similarity = highestSimilarity,
                            message = "Welcome, ${bestMatch!!.name}! Match: ${(highestSimilarity * 100).toInt()}%",
                            matchedEmployeeId = bestMatch.employeeId
                        )
                    )
                    Log.d(
                        TAG,
                        "✅ Face identified as ${bestMatch.employeeId} with similarity $highestSimilarity"
                    )
                } else {
                    // FAILURE: The person is not recognized
                    _attendanceResult.emit(
                        AttendanceResult(
                            success = false,
                            similarity = highestSimilarity,
                            message = "Face not recognized. Best match: ${(highestSimilarity * 100).toInt()}%"
                        )
                    )
                    Log.d(TAG, "❌ Face not recognized. Best match score: $highestSimilarity")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing face", e)
                _captureStatus.emit(CaptureStatus.ERROR)
                _attendanceResult.emit(
                    AttendanceResult(
                        success = false,
                        similarity = 0.0f,
                        message = "Processing error"
                    )
                )
            }
        }
    }

    private fun cropFaceFromBitmap(bitmap: Bitmap, faceBox: FaceBox): Bitmap? {
        return try {
            val scaleX = bitmap.width.toFloat() / previewWidth
            val scaleY = bitmap.height.toFloat() / previewHeight

            val left = max(0, (faceBox.left * scaleX).toInt())
            val top = max(0, (faceBox.top * scaleY).toInt())
            val right = min(bitmap.width, (faceBox.right * scaleX).toInt())
            val bottom = min(bitmap.height, (faceBox.bottom * scaleY).toInt())

            val width = right - left
            val height = bottom - top

            if (width <= 0 || height <= 0) {
                Log.e(TAG, "Invalid face bounds after scaling: width=$width, height=$height")
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
            when (image.format) {
                android.graphics.ImageFormat.YUV_420_888 -> yuvToRgbBitmap(image)
                android.graphics.ImageFormat.JPEG -> {
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
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
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
            val out = ByteArrayOutputStream()
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

    fun getFaceQualityScore(): Float = currentAnalyzer?.getFaceQualityScore() ?: 0.0f

    private fun updateImageAnalyzer() {
        if (previewWidth > 0 && previewHeight > 0) {
            currentAnalyzer?.close()
        }
    }

    fun pauseAnalysis() {
        currentAnalyzer?.pause()
    }
    fun resumeAnalysis() {
        currentAnalyzer?.resume()
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

    val lastEvent = MutableStateFlow<AttendanceEntity?>(null)
    private val _lastEventReady = MutableStateFlow(false)
    val lastEventReady: StateFlow<Boolean> = _lastEventReady

    fun fetchLastEvent(employeeId: String) {
        viewModelScope.launch {
            lastEvent.value = attendanceRepository.getLastEvent(employeeId)
            _lastEventReady.value = true
        }
    }

    fun markAttendance(
        employeeId: String,
        eventType: AttendanceEventType,
        matchPercent: Float,
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
    val timestamp: Long = System.currentTimeMillis(),
    val matchedEmployeeId: String? = null,
)

enum class CaptureStatus {
    IDLE,
    CAPTURING,
    PROCESSING,
    COMPLETED,
    ERROR
}