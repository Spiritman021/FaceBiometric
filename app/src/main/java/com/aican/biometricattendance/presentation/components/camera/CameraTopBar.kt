package com.aican.biometricattendance.presentation.components.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus


@Composable
fun CameraTopBar(
    livenessStatus: LivenessStatus,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
        // Add some horizontal space if desired:
        // Spacer(modifier = Modifier.width(8.dp))
//        FacePositioningGuide(
//            livenessStatus = livenessStatus,
//            modifier = Modifier
//                .weight(1f)
//                .fillMaxHeight() // Use remaining height (optional)
//        )

        LivenessStatusIndicator(
            status = livenessStatus,
            modifier = Modifier.weight(1f)
        )

    }
}

