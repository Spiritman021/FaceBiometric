package com.aican.biometricattendance.ml.facenet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.aican.biometricattendance.data.models.camera.FaceBox
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object FaceProcessingUtils {

     fun decodeUprightBitmapFromFile(file: File): Bitmap {
        val raw = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("Decode failed: ${file.absolutePath}")

        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        if (degrees == 0f) return raw
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, false).also {
            raw.recycle()
        }
    }

    private const val TAG = "FaceProcessingUtils"
    private const val FACENET_INPUT_SIZE = 160

    /**
     * Crop face from captured photo using face bounds
     */
    fun cropFaceFromPhoto(photoFile: File, faceBox: FaceBox): Bitmap? {
        return try {
            val originalBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                ?: return null

            cropFaceFromBitmap(originalBitmap, faceBox)
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping face from photo", e)
            null
        }
    }

    /**
     * Crop face from bitmap using face bounds
     */
    private fun cropFaceFromBitmap(bitmap: Bitmap, faceBox: FaceBox): Bitmap? {
        return try {
            val left = max(0, faceBox.left.toInt())
            val top = max(0, faceBox.top.toInt())
            val right = min(bitmap.width, faceBox.right.toInt())
            val bottom = min(bitmap.height, faceBox.bottom.toInt())

            val width = right - left
            val height = bottom - top

            if (width <= 0 || height <= 0) {
                Log.e(TAG, "Invalid face bounds: width=$width, height=$height")
                return null
            }

            val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
            Log.d(TAG, "Face cropped successfully: ${width}x${height}")
            croppedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping face from bitmap", e)
            null
        }
    }

    /**
     * Resize bitmap to 160x160 for FaceNet input
     */
    fun resizeBitmapForFaceNet(bitmap: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, FACENET_INPUT_SIZE, FACENET_INPUT_SIZE, true)
    }

    /**
     * Preprocess bitmap for FaceNet (resize + normalize)
     */
    fun preprocessForFaceNet(bitmap: Bitmap): Bitmap {
        val resized = resizeBitmapForFaceNet(bitmap)
        Log.d(TAG, "Bitmap preprocessed: ${resized.width}x${resized.height}")
        return resized
    }

    /**
     * Check if face quality is good enough for embedding generation
     */
    fun isValidFaceQuality(bitmap: Bitmap): Boolean {
        if (bitmap.width < 80 || bitmap.height < 80) {
            Log.w(TAG, "Face too small: ${bitmap.width}x${bitmap.height}")
            return false
        }

        return true
    }

    /**
     * Calculate similarity between two embeddings using cosine similarity
     */
    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) {
            Log.w(TAG, "Embedding size mismatch: ${embedding1.size} vs ${embedding2.size}")
            return 0f
        }

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        val magnitude = sqrt(norm1 * norm2)
        return if (magnitude > 0) dotProduct / magnitude else 0f
    }
}