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


import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.unit.Dp
import com.aican.biometricattendance.presentation.screens.camera.components.CoordinateDebugger

/**
 * A static overlay that draws a centered oval guide for the user to position their face in.
 *
 * @param guideColor The color of the oval's border. Can be changed dynamically for feedback.
 * @param strokeWidth The width of the oval's border.
 */
@Composable
fun FacePositioningGuide(
    modifier: Modifier = Modifier,
    guideColor: Color = Color.White,
    strokeWidth: Dp = 3.dp
) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Debug oval info
        CoordinateDebugger.logOvalGuideInfo(canvasWidth, canvasHeight)


        // Define the oval's size and position (centered)
        val ovalWidth = canvasWidth * 0.75f
        val ovalHeight = ovalWidth * 1.3f // Ovals for faces are taller than wide
        val left = (canvasWidth - ovalWidth) / 2
        val top = (canvasHeight - ovalHeight) / 2

        val ovalRect = Rect(Offset(left, top), Size(ovalWidth, ovalHeight))

        // Draw the semi-transparent background scrim
        drawRect(color = Color.Black.copy(alpha = 0.5f))

        // "Cut out" the oval shape from the background scrim
        drawOval(
            color = Color.Transparent,
            topLeft = ovalRect.topLeft,
            size = ovalRect.size,
            blendMode = BlendMode.Clear // This creates the transparent cutout
        )

        // Draw the border around the oval cutout
        drawOval(
            color = guideColor,
            topLeft = ovalRect.topLeft,
            size = ovalRect.size,
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}
