package com.aican.biometricattendance.navigation.routes

import android.net.Uri


sealed class AppRoutes(val route: String) {

    data object ROUTE_SPLASH_SCREEN : AppRoutes("splash_screen")


    data object ROUTE_DASHBOARD : AppRoutes("dashboard")

    data object ROUTE_CAMERA_SCREEN : AppRoutes("camera_preview_screen")

    data object ROUTE_FACE_REGISTRATION : AppRoutes("face_capture/{capturedUri}")
    data object ROUTE_SUBMISSION_SUCCESS : AppRoutes("submission_success")
    data object ROUTE_FACE_IMAGE_PICKER : AppRoutes("face_image_picker")

    companion object {
        fun navigateToFaceCapture(capturedUri: String): String {
            return "face_capture/${Uri.encode(capturedUri)}"
        }

        fun navigateToSuccess(): String {
            return ROUTE_SUBMISSION_SUCCESS.route
        }
    }
}