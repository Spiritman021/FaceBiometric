package com.aican.biometricattendance.presentation.components.camera

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus
import com.aican.biometricattendance.ml.camera.FaceAnalyzer


@Composable
fun LivenessStatusIndicator(
    status: LivenessStatus,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                LivenessStatus.LIVE_FACE -> Color.Green.copy(alpha = 0.9f)
                LivenessStatus.SPOOF_DETECTED -> Color.Red.copy(alpha = 0.9f)
                LivenessStatus.CHECKING -> Color.Blue.copy(alpha = 0.9f)
                LivenessStatus.POOR_QUALITY -> Color(0xFFFF9800).copy(alpha = 0.9f)
                LivenessStatus.POOR_POSITION -> Color(0xFF9C27B0).copy(alpha = 0.9f)
                LivenessStatus.FACE_TOO_CLOSE_TO_EDGE -> Color(0xFFE91E63).copy(alpha = 0.9f)
                LivenessStatus.NO_FACE -> Color.Gray.copy(alpha = 0.9f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (status) {
                    LivenessStatus.LIVE_FACE -> Icons.Default.CheckCircle
                    LivenessStatus.SPOOF_DETECTED -> Icons.Default.Warning
                    LivenessStatus.CHECKING -> Icons.Default.Visibility
                    LivenessStatus.POOR_QUALITY -> Icons.Default.VisibilityOff
                    LivenessStatus.POOR_POSITION -> Icons.Default.CenterFocusWeak
                    LivenessStatus.FACE_TOO_CLOSE_TO_EDGE -> Icons.Default.CropFree
                    LivenessStatus.NO_FACE -> Icons.Default.Face
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = when (status) {
                    LivenessStatus.LIVE_FACE -> "✓ Ready to Capture"
                    LivenessStatus.SPOOF_DETECTED -> "⚠ Not a Live Face"
                    LivenessStatus.CHECKING -> "🔍 Analyzing Face..."
                    LivenessStatus.POOR_QUALITY -> "📷 Improve Lighting"
                    LivenessStatus.POOR_POSITION -> "📍 Center Your Face"
                    LivenessStatus.FACE_TOO_CLOSE_TO_EDGE -> "📐 Move Away from Edge"
                    LivenessStatus.NO_FACE -> "👤 Show Your Face"
                },
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
