package com.aican.biometricattendance.data.models.camera

data class FaceBoundary(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val isCompletelyInFrame: Boolean,
    val visibilityRatio: Float
)
