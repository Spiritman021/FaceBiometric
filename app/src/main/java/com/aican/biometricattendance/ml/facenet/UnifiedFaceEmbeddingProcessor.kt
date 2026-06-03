package com.aican.biometricattendance.ml.facenet

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.aican.biometricattendance.data.models.facenet.FaceProcessingResult
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * A unified class for loading a FaceNet TensorFlow Lite model,
 * extracting face embeddings, performing necessary preprocessing,
 * and calculating similarity between embeddings.
 */
class UnifiedFaceEmbeddingProcessor(private val context: Context) {

    // --- Constants for FaceNet Model ---
    companion object {
        private const val TAG = "UnifiedFaceProcessor"
        private const val MODEL_FILE = "facenet.tflite" // Your FaceNet TFLite model file name
        const val INPUT_SIZE =
            160 // FaceNet model's expected input image size (e.g., 160x160 pixels)a
        const val EMBEDDING_SIZE =
            512 // FaceNet model's output embedding size (e.g., 512-dimensional vector)
        private const val BYTES_PER_FLOAT = 4 // A float occupies 4 bytes
        private const val NUM_CHANNELS = 3 // RGB image has 3 color channels
    }

    private var interpreter: Interpreter? = null // TensorFlow Lite interpreter instance
    private var gpuDelegate: GpuDelegate? = null // Optional GPU delegate for hardware acceleration
    private val compatList = CompatibilityList() // Utility to check GPU delegate compatibility

    init {
        loadModel() // Load the TFLite model when an instance of this class is created
    }

    /**
     * Loads the FaceNet TFLite model from the app's assets.
     * Configures the interpreter to use CPU threads.
     * GPU delegate option is available but currently commented out for CPU-only processing.
     */
    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile() // Get the model file as a memory-mapped buffer
            val options = Interpreter.Options() // Create interpreter options

            // --- GPU Delegate Option (Uncomment and add dependency if needed) ---
            // If you want to enable GPU acceleration, uncomment this block
            // and ensure 'org.tensorflow:tensorflow-lite-gpu' is in your build.gradle.
            /*
            if (compatList.isDelegateSupportedOnThisDevice) {
                Log.d(TAG, "GPU delegate is supported, using GPU acceleration")
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate!!)
            } else {
                Log.d(TAG, "GPU delegate not supported, using CPU")
                options.setNumThreads(4) // Use 4 CPU threads for better performance on CPU
            }
            */

            // For this example, we explicitly use CPU for consistency with the previous code.
            Log.d(TAG, "Using CPU inference with 4 threads.")
            options.setNumThreads(4) // Configure the number of CPU threads for inference

            // Initialize the TensorFlow Lite interpreter
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "✅ FaceNet model loaded successfully.")

            // Log model input and output tensor shapes for debugging and verification
            val inputShape = interpreter!!.getInputTensor(0).shape()
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            Log.d(TAG, "Model Input Shape: ${inputShape.contentToString()}")
            Log.d(TAG, "Model Output Shape: ${outputShape.contentToString()}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading FaceNet model: ${e.message}", e)
            interpreter = null // Ensure interpreter is null on failure
        }
    }

    /**
     * Loads the TFLite model file from the application's assets into a MappedByteBuffer.
     * This provides efficient, direct memory access for the interpreter.
     * @return A `MappedByteBuffer` containing the model data.
     * @throws java.io.IOException if the model file cannot be opened.
     */
    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Generates a face embedding from a given Bitmap.
     * This method handles image preprocessing, runs the FaceNet inference,
     * and normalizes the output embedding. It also includes a quality check.
     *
     * @param faceBitmap The `Bitmap` containing the cropped face.
     * It should ideally already be cropped to contain just the face.
     * @return A `FaceProcessingResult` indicating success/failure and containing the embedding if successful.
     */
    fun generateEmbedding(faceBitmap: Bitmap): FaceProcessingResult {
        // Ensure the model is loaded before attempting inference
        val currentInterpreter = interpreter ?: run {
            return FaceProcessingResult(
                success = false,
                error = "FaceNet model not loaded. Please check logs for errors during initialization."
            )
        }

        // 1. Validate face quality (e.g., minimum size, clarity)
        if (!FaceProcessingUtils.isValidFaceQuality(faceBitmap)) {
            return FaceProcessingResult(
                success = false,
                error = "Poor face quality. Ensure face is clear, well-lit, and sufficiently large."
            )
        }

        // Declare preprocessedBitmap outside the try block so it's accessible in finally
        var preprocessedBitmap: Bitmap? = null

        try {
            // 2. Preprocess Bitmap for FaceNet input (resizing and pixel value preparation)
            preprocessedBitmap = FaceProcessingUtils.preprocessForFaceNet(faceBitmap)
            // The `preprocessForFaceNet` function in FaceProcessingUtils should return a new Bitmap
            // if scaling is needed, or the original if not. If it returns a new one, we need to recycle it.
            // If it returns the original and it's reused later, avoid recycling here.
            // Assuming `preprocessForFaceNet` might return a scaled copy, so we'll handle recycling.

            val inputBuffer =
                bitmapToByteBuffer(preprocessedBitmap) // Handles pixel normalization (0-255 to 0-1)

            // 3. Prepare output buffer for the embedding
            val outputBuffer = ByteBuffer.allocateDirect(EMBEDDING_SIZE * BYTES_PER_FLOAT).apply {
                order(ByteOrder.nativeOrder()) // Use native byte order for efficiency
            }

            // 4. Run inference using the TFLite interpreter
            val startTime = System.currentTimeMillis()
            currentInterpreter.run(inputBuffer, outputBuffer)
            val inferenceTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ Inference completed in ${inferenceTime}ms.")

            // 5. Extract output embedding and L2 normalize it
            outputBuffer.rewind() // Reset buffer position to read from the beginning
            val rawEmbedding = FloatArray(EMBEDDING_SIZE)
            outputBuffer.asFloatBuffer().get(rawEmbedding) // Copy floats from buffer to array

            // L2 normalize the output embedding - standard for FaceNet
            val normalizedEmbedding = normalizeEmbedding(rawEmbedding)
            Log.d(TAG, "Embedding generated successfully, size: ${normalizedEmbedding.size}")
            Log.d(TAG, "First 5 embedding values: ${normalizedEmbedding.take(5)}")

            return FaceProcessingResult(
                success = true,
                embedding = normalizedEmbedding,
                confidence = 1.0f // Placeholder confidence; adjust if your model provides it
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating embedding: ${e.message}", e)
            return FaceProcessingResult(
                success = false,
                error = "Embedding generation failed: ${e.message}"
            )
        } finally {
            // Corrected recycling logic: Only recycle if a *new* bitmap was created by preprocessing.
            // Check if the preprocessedBitmap is not null AND if it's a different instance than the original faceBitmap
            // AND if it's not already recycled.
            // This prevents recycling a bitmap that might be owned by another component or the original.
            if (preprocessedBitmap != null && preprocessedBitmap != faceBitmap && !preprocessedBitmap.isRecycled) {
                preprocessedBitmap.recycle()
            }
        }
    }

    /**
     * Converts a `Bitmap` to a `ByteBuffer` for the TensorFlow Lite model input.
     * This method handles the crucial step of pixel normalization from `[0, 255]` to `[0, 1]`
     * and arranging the data in the correct format (e.g., RGB floats).
     *
     * @param bitmap The `Bitmap` to convert. Must be already scaled to `INPUT_SIZE`x`INPUT_SIZE`.
     * @return A `ByteBuffer` containing the normalized pixel data.
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // Allocate a direct ByteBuffer for efficiency.
        // Size: (INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS) * BYTES_PER_FLOAT
        val byteBuffer = ByteBuffer.allocateDirect(
            INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * BYTES_PER_FLOAT
        ).apply {
            order(ByteOrder.nativeOrder()) // Set native byte order
        }

        // Get all pixels from the bitmap as an IntArray (ARGB_8888 format)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // Iterate through pixels, extract RGB, normalize, and put into ByteBuffer as floats
        for (pixel in pixels) {
            // Extract R, G, B components. `(pixel shr 16) and 0xFF` gets the red component.
            // `0xFF` masks ensure only the byte value is taken.
            // Normalize to [0, 1] range by dividing by 255.0f.
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        // Rewind the buffer to prepare it for reading by the interpreter
        byteBuffer.rewind()
        return byteBuffer
    }

    /**
     * Performs L2 normalization on the given float array (embedding).
     * This scales the vector so its Euclidean norm (magnitude) is 1.0,
     * which is a common requirement for comparing FaceNet embeddings.
     *
     * @param embedding The input float array (raw embedding) to normalize.
     * @return The L2-normalized float array.
     */
    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        val norm = sqrt(embedding.map { it * it }.sum()) // Calculate the Euclidean norm
        return if (norm > 0) {
            embedding.map { it / norm }.toFloatArray() // Divide each element by the norm
        } else {
            embedding // Return original if norm is zero to prevent division by zero
        }
    }

    /**
     * Calculates the cosine similarity between two face embeddings.
     * A higher score (closer to 1.0) indicates greater similarity.
     * This function uses the utility from `FaceProcessingUtils`.
     *
     * @param embedding1 The first face embedding.
     * @param embedding2 The second face embedding.
     * @return The cosine similarity score (between -1.0 and 1.0).
     */
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        return FaceProcessingUtils.calculateSimilarity(embedding1, embedding2)
    }

    /**
     * Closes the TensorFlow Lite interpreter and releases any associated hardware delegate resources.
     * This is crucial to prevent memory leaks and free up system resources when the class is no longer needed.
     */
    fun close() {
        interpreter?.close() // Close the interpreter
        interpreter = null   // Nullify the reference
        gpuDelegate?.close() // Close the GPU delegate if it was used
        gpuDelegate = null   // Nullify the reference
        Log.d(TAG, "🧹 UnifiedFaceEmbeddingProcessor resources closed.")
    }
}