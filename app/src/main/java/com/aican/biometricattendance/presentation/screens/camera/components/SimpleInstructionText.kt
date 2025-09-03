package com.aican.biometricattendance.presentation.screens.camera.components


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.aican.biometricattendance.data.models.camera.enums.SimpleFaceStatus

@Composable
fun SimpleInstructionText(
    faceStatus: SimpleFaceStatus,
    modifier: Modifier = Modifier
) {
    val instruction = when (faceStatus) {
        SimpleFaceStatus.NO_FACE -> "Position your face in the frame"
        SimpleFaceStatus.POOR_QUALITY -> "Move closer or improve lighting"
        SimpleFaceStatus.FACE_DETECTED -> "Tap the button to capture your photo"
    }

    AnimatedVisibility(
        visible = instruction.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
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