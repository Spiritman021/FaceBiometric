package com.aican.biometricattendance.data.models.camera.enums

enum class LivenessStatus {
    NO_FACE,
    POOR_QUALITY,
    POOR_POSITION,
    FACE_TOO_CLOSE_TO_EDGE, // New status for boundary issues
    CHECKING,
    LIVE_FACE,
    SPOOF_DETECTED
}
