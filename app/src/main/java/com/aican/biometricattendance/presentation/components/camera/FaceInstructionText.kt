package com.aican.biometricattendance.presentation.components.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus
import com.aican.biometricattendance.ml.camera.FaceAnalyzer


@Composable
fun FaceInstructionText(
    livenessStatus: LivenessStatus,
    modifier: Modifier = Modifier
) {
    val instruction = when (livenessStatus) {
        LivenessStatus.NO_FACE -> "Position your face in the frame"
        LivenessStatus.POOR_QUALITY -> "Move to better lighting and face the camera directly"
        LivenessStatus.POOR_POSITION -> "Move closer and center your face in the frame"
        LivenessStatus.FACE_TOO_CLOSE_TO_EDGE -> "Keep your entire face within the frame boundaries"
        LivenessStatus.CHECKING -> "Hold still while we verify your live face..."
        LivenessStatus.LIVE_FACE -> "Perfect! Tap to capture your face"
        LivenessStatus.SPOOF_DETECTED -> "Please show your live face to the camera"
    }

    AnimatedVisibility(
        visible = instruction.isNotEmpty(),
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it }
    ) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = instruction,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
