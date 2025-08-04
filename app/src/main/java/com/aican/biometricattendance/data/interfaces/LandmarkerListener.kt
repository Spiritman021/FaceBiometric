package com.aican.biometricattendance.data.interfaces

import com.aican.biometricattendance.data.models.facelandmark.ResultBundle
import com.aican.biometricattendance.ml.facelandmark.FaceLandmarkerHelper.Companion.OTHER_ERROR

interface LandmarkerListener {
    fun onError(error: String, errorCode: Int = OTHER_ERROR)
    fun onResults(resultBundle:  ResultBundle)
}