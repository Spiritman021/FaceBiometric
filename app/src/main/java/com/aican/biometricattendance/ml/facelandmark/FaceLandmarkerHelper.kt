package com.aican.biometricattendance.ml.facelandmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.aican.biometricattendance.data.interfaces.LandmarkerListener
import com.aican.biometricattendance.data.models.facelandmark.ResultBundle
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class FaceLandmarkerHelper(
    var minFaceDetectionConfidence: Float = DEFAULT_FACE_DETECTION_CONFIDENCE,
    var minFaceTrackingConfidence: Float = DEFAULT_FACE_TRACKING_CONFIDENCE,
    var minFacePresenceConfidence: Float = DEFAULT_FACE_PRESENCE_CONFIDENCE,
    var maxNumFaces: Int = DEFAULT_NUM_FACES,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val faceLandmarkerHelperListener: LandmarkerListener? = null
) {


    companion object {
        const val TAG = "FaceLandmarkerHelper"
        private const val MP_FACE_LANDMARKER_TASK = "face_landmarker.task"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_FACE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_FACE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_FACE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_FACES = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }


    private var faceLandmarker: FaceLandmarker? = null

    init {
        setupFaceLandmarker()
    }

    fun clearFaceLandmarker() {
        faceLandmarker?.close()
        faceLandmarker = null
    }


    fun setupFaceLandmarker() {
        Log.d(TAG, "🔧 Starting MediaPipe Face Landmarker setup...")

        // Set general detection Options
        val baseOptionsBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
                Log.d(TAG, "✅ Using CPU delegate")
            }

            DELEGATE_GPU -> {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
                Log.d(TAG, "✅ Using GPU delegate")
            }
        }

        // CHECK: Model file exists
        try {
            val assetManager = context.assets
            val modelStream = assetManager.open(MP_FACE_LANDMARKER_TASK)
            val modelSize = modelStream.available()
            modelStream.close()
            Log.d(TAG, "✅ Model file found: $MP_FACE_LANDMARKER_TASK, Size: $modelSize bytes")
        } catch (e: Exception) {
            Log.e(TAG, "❌ MODEL FILE NOT FOUND: $MP_FACE_LANDMARKER_TASK", e)
            faceLandmarkerHelperListener?.onError("Model file not found: ${e.message}")
            return
        }

        baseOptionsBuilder.setModelAssetPath(MP_FACE_LANDMARKER_TASK)

        // Check if runningMode is consistent with faceLandmarkerHelperListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (faceLandmarkerHelperListener == null) {
                    Log.e(TAG, "❌ Listener is null for LIVE_STREAM mode")
                    throw IllegalStateException(
                        "faceLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    )
                }
                Log.d(TAG, "✅ LIVE_STREAM mode with listener")
            }

            else -> {
                Log.d(TAG, "✅ Using mode: $runningMode")
            }
        }

        try {
            val baseOptions = baseOptionsBuilder.build()
            Log.d(TAG, "✅ Base options built successfully")

            // Create an option builder with base options and specific
            // options only use for Face Landmarker.
            val optionsBuilder =
                FaceLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinFaceDetectionConfidence(minFaceDetectionConfidence)
                    .setMinTrackingConfidence(minFaceTrackingConfidence)
                    .setMinFacePresenceConfidence(minFacePresenceConfidence)
                    .setNumFaces(maxNumFaces)
                    .setRunningMode(runningMode)
                    .setOutputFaceBlendshapes(true)  // Enable for liveness detection
                    .setOutputFacialTransformationMatrixes(true)

            Log.d(TAG, "🎛️ Face Landmarker Options:")
            Log.d(TAG, "   - Min Face Detection Confidence: $minFaceDetectionConfidence")
            Log.d(TAG, "   - Min Face Tracking Confidence: $minFaceTrackingConfidence")
            Log.d(TAG, "   - Min Face Presence Confidence: $minFacePresenceConfidence")
            Log.d(TAG, "   - Max Num Faces: $maxNumFaces")

            // The ResultListener and ErrorListener only use for LIVE_STREAM mode.
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
                Log.d(TAG, "✅ Result and Error listeners set")
            }

            val options = optionsBuilder.build()
            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d(TAG, "🎉 MediaPipe Face Landmarker created successfully!")

        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ IllegalStateException during Face Landmarker initialization", e)
            faceLandmarkerHelperListener?.onError(
                "Face Landmarker failed to initialize. See error logs for details"
            )
        } catch (e: RuntimeException) {
            Log.e(TAG, "❌ RuntimeException during Face Landmarker initialization", e)
            // This occurs if the model being used does not support GPU
            faceLandmarkerHelperListener?.onError(
                "Face Landmarker failed to initialize. See error logs for details", GPU_ERROR
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected exception during Face Landmarker initialization", e)
            faceLandmarkerHelperListener?.onError(
                "Face Landmarker failed to initialize: ${e.message}"
            )
        }
    }

    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        Log.d(
            TAG,
            "🎥 Processing frame: ${imageProxy.width}x${imageProxy.height}, Format: ${imageProxy.format}"
        )

        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            Log.d(TAG, "✅ Converted ImageProxy to Bitmap: ${bitmap.width}x${bitmap.height}")

            val processedBitmap = bitmap

            // Convert the processed Bitmap to an MPImage object to run inference
            val mpImage = BitmapImageBuilder(processedBitmap).build()
            Log.d(TAG, "🔄 MPImage created, calling detectAsync...")

            detectAsync(mpImage, frameTime)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in detectLiveStream: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        return when (imageProxy.format) {
            ImageFormat.YUV_420_888 -> {
                Log.d(TAG, "🎨 Converting YUV_420_888 to RGB bitmap")
                yuvToBitmap(imageProxy)
            }

            ImageFormat.NV21 -> {
                Log.d(TAG, "🎨 Converting NV21 to RGB bitmap")
                nv21ToBitmap(imageProxy)
            }

            else -> {
                Log.w(
                    TAG,
                    "⚠️ Unsupported format: ${imageProxy.format}, attempting direct conversion"
                )
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw RuntimeException("Failed to decode image")
            }
        }
    }

    private fun yuvToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer // Y
        val uBuffer = imageProxy.planes[1].buffer // U
        val vBuffer = imageProxy.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun nv21ToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val yuvImage = YuvImage(bytes, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        faceLandmarker?.detectAsync(mpImage, frameTime)
    }

    fun detectVideoFile(
        videoUri: android.net.Uri,
        inferenceIntervalMs: Long
    ): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile while not using RunningMode.VIDEO"
            )
        }

        val startTime = SystemClock.uptimeMillis()

        var didErrorOccurred = false

        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs =
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong()

        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height

        if ((videoLengthMs == null) || (width == null) || (height == null)) return null

        val resultList = mutableListOf<FaceLandmarkerResult>()
        val numberofFrameToRead = videoLengthMs.div(inferenceIntervalMs)

        for (i in 0..numberofFrameToRead) {
            val timestampMs = i * inferenceIntervalMs // ms

            retriever
                .getFrameAtTime(
                    timestampMs * 1000, // convert from ms to micro-s2
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                ?.let { frame ->
                    // Convert the video frame to ARGB_8888 which is required by the MediaPipe
                    val argb8888Frame =
                        if (frame.config == Bitmap.Config.ARGB_8888) frame
                        else frame.copy(Bitmap.Config.ARGB_8888, false)

                    val mpImage = BitmapImageBuilder(argb8888Frame).build()

                    faceLandmarker?.detectForVideo(mpImage, timestampMs)?.let { detectionResult ->
                        resultList.add(detectionResult)
                    }
                        ?: {
                            didErrorOccurred = true
                            faceLandmarkerHelperListener?.onError(
                                "ResultBundle could not be returned in detectVideoFile"
                            )
                        }
                }
                ?: run {
                    didErrorOccurred = true
                    faceLandmarkerHelperListener?.onError(
                        "Frame at specified time could not be retrieved when detecting in video."
                    )
                }
        }

        retriever.release()

        val inferenceTimePerFrameMs =
            (SystemClock.uptimeMillis() - startTime).div(numberofFrameToRead)

        return if (didErrorOccurred) {
            null
        } else {
            ResultBundle(resultList, inferenceTimePerFrameMs, height, width)
        }
    }

    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage while not using RunningMode.IMAGE"
            )
        }

        val startTime = SystemClock.uptimeMillis()

        val mpImage = BitmapImageBuilder(image).build()

        faceLandmarker?.detect(mpImage)?.also { landmarkerResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            return ResultBundle(
                listOf(landmarkerResult),
                inferenceTimeMs,
                image.height,
                image.width
            )
        }

        faceLandmarkerHelperListener?.onError(
            "Face Landmarker failed to detect."
        )
        return null
    }

    private fun returnLivestreamResult(
        result: FaceLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        Log.d(TAG, "🎯 Face detection result received:")
        Log.d(TAG, "   - Faces detected: ${result.faceLandmarks().size}")
        Log.d(TAG, "   - Inference time: ${inferenceTime}ms")

        faceLandmarkerHelperListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        Log.e(TAG, "❌ MediaPipe livestream error: ${error.message}", error)
        faceLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

}