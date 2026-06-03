package com.aican.biometricattendance.presentation.screens.camera.components

import android.graphics.Bitmap
import android.util.Log

object EmbeddingDebugger {
    private const val TAG = "EmbeddingDebug"

    fun logImageProcessing(context: String, bitmap: Bitmap, source: String) {

        Log.d(TAG, """
            🖼️ IMAGE PROCESSING [$context]:
            - Source: $source
            - Dimensions: ${bitmap.width}x${bitmap.height}
            - Config: ${bitmap.config}
            - Bytes: ${bitmap.byteCount}
            - Density: ${bitmap.density}
        """.trimIndent())
    }

    fun logEmbeddingGeneration(
        context: String,
        embedding: FloatArray?,
        success: Boolean,
        error: String?
    ) {
        Log.d(TAG, """
            🧠 EMBEDDING GENERATION [$context]:
            - Success: $success
            - Error: $error
            - Embedding size: ${embedding?.size ?: 0}
            - First 10 values: ${embedding?.take(10)?.joinToString(", ") { "%.4f".format(it) } ?: "null"}
            - L2 Norm: ${embedding?.let { kotlin.math.sqrt(it.map { v -> v * v }.sum()) } ?: 0f}
        """.trimIndent())
    }

    fun compareEmbeddings(embedding1: FloatArray, embedding2: FloatArray, label1: String, label2: String) {
        val similarity = calculateCosineSimilarity(embedding1, embedding2)
        Log.d(TAG, """
            🔍 EMBEDDING COMPARISON:
            - $label1 vs $label2
            - Similarity: $similarity
            - Size match: ${embedding1.size == embedding2.size}
            - Threshold met (0.8): ${similarity >= 0.8f}
        """.trimIndent())
    }

    private fun calculateCosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val magnitude = kotlin.math.sqrt(normA * normB)
        return if (magnitude > 0) dotProduct / magnitude else 0f
    }
}
