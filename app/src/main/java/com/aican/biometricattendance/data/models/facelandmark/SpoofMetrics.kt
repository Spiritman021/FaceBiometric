package com.aican.biometricattendance.data.models.facelandmark

data class SpoofMetrics(
    val blinkDetected: Boolean = false,
    val microMovementScore: Float = 0f,
    val textureVariance: Float = 0f,
    val depthConsistency: Float = 0f,
    val naturalMovementPattern: Float = 0f,
    val edgeConsistency: Float = 0f,
    val colorDistribution: Float = 0f
)