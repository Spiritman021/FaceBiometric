package com.aican.biometricattendance.presentation.components.camera

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus
import com.aican.biometricattendance.ml.camera.FaceAnalyzer


@Composable
fun SmartCaptureButton(
    livenessStatus: LivenessStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled = livenessStatus == LivenessStatus.LIVE_FACE
    val isAnalyzing = livenessStatus == LivenessStatus.CHECKING

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(80.dp)
    ) {
        // Background circle
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    when (livenessStatus) {
                        LivenessStatus.LIVE_FACE -> Color.Green.copy(alpha = 0.8f)
                        LivenessStatus.CHECKING -> Color.Blue.copy(alpha = 0.6f)
                        LivenessStatus.POOR_QUALITY -> Color(0xFFFF9800).copy(alpha = 0.6f)
                        LivenessStatus.POOR_POSITION -> Color(0xFF9C27B0).copy(alpha = 0.6f)
                        LivenessStatus.FACE_TOO_CLOSE_TO_EDGE -> Color(0xFFE91E63).copy(alpha = 0.6f)
                        LivenessStatus.SPOOF_DETECTED -> Color.Red.copy(alpha = 0.8f)
                        LivenessStatus.NO_FACE -> Color.Gray.copy(alpha = 0.6f)
                    }
                )
                .border(
                    4.dp,
                    if (isEnabled) Color.White else Color.Gray.copy(alpha = 0.7f),
                    CircleShape
                )
                .clickable(enabled = isEnabled) {
                    onClick()
                }
        )

        // Pulsing animation for ready state
        if (isEnabled) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size((80 * scale).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }

        // Progress indicator for analyzing state
        if (isAnalyzing) {
            CircularProgressIndicator(
                modifier = Modifier.size(88.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }

        // Center icon
        Icon(
            imageVector = when (livenessStatus) {
                LivenessStatus.LIVE_FACE -> Icons.Default.CameraAlt
                LivenessStatus.CHECKING -> Icons.Default.Visibility
                LivenessStatus.POOR_QUALITY -> Icons.Default.VisibilityOff
                LivenessStatus.POOR_POSITION -> Icons.Default.CenterFocusWeak
                LivenessStatus.FACE_TOO_CLOSE_TO_EDGE -> Icons.Default.CropFree
                LivenessStatus.SPOOF_DETECTED -> Icons.Default.Block
                LivenessStatus.NO_FACE -> Icons.Default.Face
            },
            contentDescription = "Capture Face Image",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}