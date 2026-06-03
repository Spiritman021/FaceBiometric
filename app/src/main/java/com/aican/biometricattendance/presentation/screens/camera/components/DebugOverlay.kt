package com.aican.biometricattendance.presentation.screens.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aican.biometricattendance.data.models.camera.FaceBox

@Composable
fun DebugOverlay(
    faceBoxes: List<FaceBox>,
    previewWidth: Float,
    previewHeight: Float,
    modifier: Modifier = Modifier,
) {
    if (faceBoxes.isNotEmpty()) {
        val faceBox = faceBoxes.first()

        Box(modifier = modifier.fillMaxSize()) {
            // Show face box coordinates as text overlay
            Text(
                text = """
                    Preview: ${previewWidth.toInt()}x${previewHeight.toInt()}
                    FaceBox: L:${faceBox.left.toInt()} T:${faceBox.top.toInt()}
                             R:${faceBox.right.toInt()} B:${faceBox.bottom.toInt()}
                    Size: ${(faceBox.right - faceBox.left).toInt()}x${(faceBox.bottom - faceBox.top).toInt()}
                """.trimIndent(),
                color = Color.Yellow,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(4.dp)
            )
        }
    }
}