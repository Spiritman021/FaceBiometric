package com.aican.biometricattendance.presentation.components.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aican.biometricattendance.data.models.camera.FaceBox
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus
import com.aican.biometricattendance.ml.camera.FaceAnalyzer

@Composable
fun FaceOverlay(
    faceBoxes: List<FaceBox>,
    livenessStatus: LivenessStatus = LivenessStatus.NO_FACE,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        faceBoxes.forEach { faceBox ->
            val color = when (livenessStatus) {
                LivenessStatus.LIVE_FACE -> Color.Green
                LivenessStatus.SPOOF_DETECTED -> Color.Red
                LivenessStatus.CHECKING -> Color.Blue
                LivenessStatus.POOR_QUALITY -> Color(0xFFFF9800) // Orange
                LivenessStatus.POOR_POSITION -> Color(0xFF9C27B0) // Purple
                LivenessStatus.FACE_TOO_CLOSE_TO_EDGE -> Color(0xFFE91E63) // Pink
                LivenessStatus.NO_FACE -> Color.Gray
            }

            val strokeWidth = when (livenessStatus) {
                LivenessStatus.LIVE_FACE -> 10.dp.toPx()
                LivenessStatus.FACE_TOO_CLOSE_TO_EDGE -> 8.dp.toPx() // Prominent for boundary issues
                LivenessStatus.CHECKING -> 6.dp.toPx()
                else -> 4.dp.toPx()
            }

            // Main bounding box
            drawRect(
                color = color,
                topLeft = Offset(faceBox.left, faceBox.top),
                size = Size(
                    faceBox.right - faceBox.left,
                    faceBox.bottom - faceBox.top
                ),
                style = Stroke(width = strokeWidth)
            )

            // Special indication for boundary issues
            if (livenessStatus == LivenessStatus.FACE_TOO_CLOSE_TO_EDGE) {
                // Draw flashing border effect
                val flashStroke = strokeWidth * 1.5f
                drawRect(
                    color = color.copy(alpha = 0.5f),
                    topLeft = Offset(faceBox.left - flashStroke / 2, faceBox.top - flashStroke / 2),
                    size = Size(
                        faceBox.right - faceBox.left + flashStroke,
                        faceBox.bottom - faceBox.top + flashStroke
                    ),
                    style = Stroke(width = flashStroke)
                )
            }

            // Enhanced corner indicators for LIVE_FACE
            if (livenessStatus == LivenessStatus.LIVE_FACE) {
                val cornerLength = 50.dp.toPx()
                val cornerStroke = strokeWidth * 1.2f

                // Draw all four corners
                listOf(
                    // Top-left
                    listOf(
                        Offset(faceBox.left, faceBox.top) to Offset(
                            faceBox.left + cornerLength,
                            faceBox.top
                        ),
                        Offset(faceBox.left, faceBox.top) to Offset(
                            faceBox.left,
                            faceBox.top + cornerLength
                        )
                    ),
                    // Top-right
                    listOf(
                        Offset(faceBox.right, faceBox.top) to Offset(
                            faceBox.right - cornerLength,
                            faceBox.top
                        ),
                        Offset(faceBox.right, faceBox.top) to Offset(
                            faceBox.right,
                            faceBox.top + cornerLength
                        )
                    ),
                    // Bottom-left
                    listOf(
                        Offset(faceBox.left, faceBox.bottom) to Offset(
                            faceBox.left + cornerLength,
                            faceBox.bottom
                        ),
                        Offset(faceBox.left, faceBox.bottom) to Offset(
                            faceBox.left,
                            faceBox.bottom - cornerLength
                        )
                    ),
                    // Bottom-right
                    listOf(
                        Offset(
                            faceBox.right,
                            faceBox.bottom
                        ) to Offset(faceBox.right - cornerLength, faceBox.bottom),
                        Offset(faceBox.right, faceBox.bottom) to Offset(
                            faceBox.right,
                            faceBox.bottom - cornerLength
                        )
                    )
                ).flatten().forEach { (start, end) ->
                    drawLine(
                        color = color,
                        start = start,
                        end = end,
                        strokeWidth = cornerStroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Draw frame boundary guide for boundary issues
        if (livenessStatus == LivenessStatus.FACE_TOO_CLOSE_TO_EDGE) {
            val margin = minOf(size.width, size.height) * 0.05f // 5% margin
            val guideColor = Color(0xFFE91E63).copy(alpha = 0.8f)
            val guideStroke = 3.dp.toPx()

            // Draw boundary guide rectangle
            drawRect(
                color = guideColor,
                topLeft = Offset(margin, margin),
                size = Size(size.width - 2 * margin, size.height - 2 * margin),
                style = Stroke(
                    width = guideStroke,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(15.dp.toPx(), 10.dp.toPx()),
                        0f
                    )
                )
            )
        }
    }
}