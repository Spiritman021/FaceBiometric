package com.aican.biometricattendance.presentation.components.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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
import kotlin.collections.forEach


@Composable
fun EnhancedFaceOverlay(
    faceBoxes: List<FaceBox>,
    livenessStatus: LivenessStatus,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        faceBoxes.forEach { faceBox ->
            val color = when (livenessStatus) {
                LivenessStatus.LIVE_FACE -> Color.Green
                LivenessStatus.SPOOF_DETECTED -> Color.Red
                LivenessStatus.CHECKING -> Color.Blue
                LivenessStatus.POOR_QUALITY -> Color(0xFFFF9800)
                LivenessStatus.POOR_POSITION -> Color(0xFF9C27B0)
                LivenessStatus.FACE_TOO_CLOSE_TO_EDGE -> Color(0xFFE91E63)
                LivenessStatus.NO_FACE -> Color.Gray
            }

            val strokeWidth = when (livenessStatus) {
                LivenessStatus.LIVE_FACE -> 8.dp.toPx() // Reduced from 10dp
                LivenessStatus.FACE_TOO_CLOSE_TO_EDGE -> 6.dp.toPx() // Reduced from 8dp
                LivenessStatus.CHECKING -> 4.dp.toPx() // Reduced from 6dp
                else -> 3.dp.toPx() // Reduced from 4dp
            }

            // FIXED: Use the face box coordinates directly (they're already scaled in FaceAnalyzer)
            val left = faceBox.left
            val top = faceBox.top
            val right = faceBox.right
            val bottom = faceBox.bottom

            // Main bounding box with correct coordinates
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = strokeWidth)
            )

            // Enhanced corner indicators for LIVE_FACE status
            if (livenessStatus == LivenessStatus.LIVE_FACE) {
                val faceWidth = right - left
                val faceHeight = bottom - top

                // FIXED: Scale corner length based on face size
                val cornerLength = minOf(faceWidth, faceHeight) * 0.15f // 15% of face size
                val cornerStroke = strokeWidth * 1.2f

                // Draw corner indicators with proper scaling
                listOf(
                    // Top-left
                    listOf(
                        Offset(left, top) to Offset(left + cornerLength, top),
                        Offset(left, top) to Offset(left, top + cornerLength)
                    ),
                    // Top-right
                    listOf(
                        Offset(right, top) to Offset(right - cornerLength, top),
                        Offset(right, top) to Offset(right, top + cornerLength)
                    ),
                    // Bottom-left
                    listOf(
                        Offset(left, bottom) to Offset(left + cornerLength, bottom),
                        Offset(left, bottom) to Offset(left, bottom - cornerLength)
                    ),
                    // Bottom-right
                    listOf(
                        Offset(right, bottom) to Offset(right - cornerLength, bottom),
                        Offset(right, bottom) to Offset(right, bottom - cornerLength)
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

            // Special effect for boundary issues
            if (livenessStatus == LivenessStatus.FACE_TOO_CLOSE_TO_EDGE) {
                // Draw a warning border around the face
                val warningStroke = strokeWidth * 0.8f
                drawRect(
                    color = color.copy(alpha = 0.4f),
                    topLeft = Offset(left - warningStroke, top - warningStroke),
                    size = Size(
                        (right - left) + (warningStroke * 2),
                        (bottom - top) + (warningStroke * 2)
                    ),
                    style = Stroke(width = warningStroke)
                )
            }
        }

        // Draw frame boundary guide for edge detection issues
        if (livenessStatus == LivenessStatus.FACE_TOO_CLOSE_TO_EDGE) {
            val margin = minOf(size.width, size.height) * 0.05f
            val guideColor = Color(0xFFE91E63).copy(alpha = 0.6f) // More subtle
            val guideStroke = 2.dp.toPx() // Thinner stroke

            drawRect(
                color = guideColor,
                topLeft = Offset(margin, margin),
                size = Size(size.width - 2 * margin, size.height - 2 * margin),
                style = Stroke(
                    width = guideStroke,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(12.dp.toPx(), 8.dp.toPx()),
                        0f
                    )
                )
            )
        }
    }
}
