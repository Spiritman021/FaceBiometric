package com.aican.biometricattendance.data.models.camera.enums

enum class SimpleFaceStatus {
    NO_FACE,           // No face detected
    FACE_DETECTED,     // Face found and ready to capture
    POOR_QUALITY       // Face detected but quality issues (too small, dark, etc.)
}