package com.aican.biometricattendance.presentation.components.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus
import com.aican.biometricattendance.ml.camera.FaceAnalyzer


@Composable
fun FacePositioningGuide(
    livenessStatus: LivenessStatus,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = livenessStatus == LivenessStatus.POOR_POSITION,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .aspectRatio(0.75f)
            ) {
                val strokeWidth = 3.dp.toPx()
                val dashLength = 10.dp.toPx()
                val gapLength = 8.dp.toPx()

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.8f),
                    size = size,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(dashLength, gapLength),
                            0f
                        )
                    ),
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = "Position your face within this area",
                    modifier = Modifier.padding(12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
