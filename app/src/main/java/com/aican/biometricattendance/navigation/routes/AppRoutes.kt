package com.aican.biometricattendance.navigation.routes

import android.net.Uri


sealed class AppRoutes(val route: String) {

    data object ROUTE_SPLASH_SCREEN : AppRoutes("splash_screen")


    data object ROUTE_DASHBOARD : AppRoutes("dashboard")

    data object ROUTE_CAMERA_SCREEN : AppRoutes("camera_preview_screen/{id}")
    data object ROUTE_MARK_ATTENDANCE_SCREEN : AppRoutes("mark_attendance_screen/{id}")

    data object ROUTE_FACE_REGISTRATION : AppRoutes("face_capture/{capturedUri}/{id}")
    data object ROUTE_SUBMISSION_SUCCESS : AppRoutes("submission_success")
    data object ROUTE_FACE_IMAGE_PICKER : AppRoutes("face_image_picker")
    data object ROUTE_CHECK_EMPLOYEE : AppRoutes("check_employee")

    data object ROUTE_REGISTERED_USERS : AppRoutes("registered_users")

    data object ROUTE_ATTENDANCE_REPORT : AppRoutes("attendance_report")


    companion object {
        fun navigateToFaceCapture(capturedUri: String, id: String): String {
            return "face_capture/${Uri.encode(capturedUri)}/$id"
        }

        fun navigateToMarkAttendance(id: String): String {
            return "mark_attendance_screen/$id"
        }

        fun navigateToCameraScreen(id: String): String {
            return "camera_preview_screen/$id"
        }

        fun navigateToSuccess(): String {
            return ROUTE_SUBMISSION_SUCCESS.route
        }
    }
}