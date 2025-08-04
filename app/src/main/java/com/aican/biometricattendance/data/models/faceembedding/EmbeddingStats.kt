package com.aican.biometricattendance.data.models.faceembedding

data class EmbeddingStats(
    val min: Float,
    val max: Float,
    val mean: Float,
    val stdDev: Float,
    val l2Norm: Float
)

data class EmbeddingQuality(
    val isNormalized: Boolean,
    val hasValidRange: Boolean,
    val nonZeroValues: Int,
    val qualityScore: Float
)