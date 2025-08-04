package com.aican.biometricattendance.presentation.components.camera

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus
import com.aican.biometricattendance.ml.camera.FaceAnalyzer

@Composable
fun DebugInfoText(
    livenessStatus: LivenessStatus,
    quality: Float,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Debug: ${livenessStatus.name} | Quality: ${(quality * 100).toInt()}%",
        color = Color.White.copy(alpha = 0.7f),
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
        textAlign = TextAlign.Center
    )
}
