package com.aican.biometricattendance.presentation.components.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


@Composable
fun CameraTopBar(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        Column(Modifier.align(Alignment.Center)) {
            Text(
                "Face Capture Camera",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Cropped Face Mode",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}