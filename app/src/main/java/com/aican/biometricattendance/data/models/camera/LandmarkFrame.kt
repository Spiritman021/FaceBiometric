package com.aican.biometricattendance.data.models.camera

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

data class LandmarkFrame(
    val landmarks: List<NormalizedLandmark>,
    val timestamp: Long,
    val quality: Float
)