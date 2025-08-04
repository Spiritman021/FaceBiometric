package com.aican.biometricattendance.data.models.facelandmark

import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult


data class ResultBundle(
    val results: List<FaceLandmarkerResult>,
    val inferenceTime: Long,
    val inputImageHeight: Int,
    val inputImageWidth: Int,
)
