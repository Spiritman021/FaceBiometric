package com.aican.biometricattendance.presentation.screens.camera.components


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aican.biometricattendance.data.models.camera.enums.SimpleFaceStatus

@Composable
fun SimpleCaptureButton(
    faceStatus: SimpleFaceStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled = faceStatus == SimpleFaceStatus.FACE_DETECTED
    val (backgroundColor, iconColor, borderColor, icon) = when (faceStatus) {
        SimpleFaceStatus.FACE_DETECTED -> {
            // Green - ready to capture
            Tuple4(
                Color(0xFF4CAF50).copy(alpha = 0.9f),
                Color.White,
                Color.White,
                Icons.Default.CameraAlt
            )
        }
        SimpleFaceStatus.POOR_QUALITY -> {
            // Orange - face detected but poor quality
            Tuple4(
                Color(0xFFFF9800).copy(alpha = 0.7f),
                Color.White,
                Color.Gray,
                Icons.Default.Warning
            )
        }
        SimpleFaceStatus.NO_FACE -> {
            // Gray - no face detected
            Tuple4(
                Color.Gray.copy(alpha = 0.5f),
                Color.White,
                Color.Gray,
                Icons.Default.Face
            )
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(80.dp)
    ) {
        // Main capture button
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(4.dp, borderColor, CircleShape)
                .clickable(enabled = isEnabled) {
                    if (isEnabled) onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = when (faceStatus) {
                    SimpleFaceStatus.FACE_DETECTED -> "Capture Face"
                    SimpleFaceStatus.POOR_QUALITY -> "Poor Quality"
                    SimpleFaceStatus.NO_FACE -> "No Face Detected"
                },
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
