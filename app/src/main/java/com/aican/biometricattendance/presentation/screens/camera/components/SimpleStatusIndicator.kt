package com.aican.biometricattendance.presentation.screens.camera.components


import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aican.biometricattendance.data.models.camera.enums.SimpleFaceStatus

@Composable
fun SimpleStatusIndicator(
    faceStatus: SimpleFaceStatus,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, textColor, icon, text) = when (faceStatus) {
        SimpleFaceStatus.FACE_DETECTED -> Tuple4(
            Color(0xFF4CAF50).copy(alpha = 0.9f),
            Color.White,
            Icons.Default.CameraAlt,
            "Ready to Capture"
        )

        SimpleFaceStatus.POOR_QUALITY -> Tuple4(
            Color(0xFFFF9800).copy(alpha = 0.9f),
            Color.White,
            Icons.Default.Warning,
            "Improve Position"
        )

        SimpleFaceStatus.NO_FACE -> Tuple4(
            Color.Gray.copy(alpha = 0.8f),
            Color.White,
            Icons.Default.Face,
            "Position Your Face"
        )
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)