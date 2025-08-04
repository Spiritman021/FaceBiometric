package com.aican.biometricattendance.presentation.components.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


@Composable
fun QualityIndicator(
    quality: Float,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        quality >= 0.8f -> Icons.Default.CheckCircle
                        quality >= 0.6f -> Icons.Default.Warning
                        else -> Icons.Default.Error
                    },
                    contentDescription = null,
                    tint = when {
                        quality >= 0.8f -> Color.Green
                        quality >= 0.6f -> Color.Yellow
                        else -> Color.Red
                    },
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Quality: ",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )

                LinearProgressIndicator(
                    progress = { quality },
                    modifier = Modifier
                        .width(80.dp)
                        .height(4.dp),
                    color = when {
                        quality >= 0.8f -> Color.Green
                        quality >= 0.6f -> Color.Yellow
                        else -> Color.Red
                    },
                    trackColor = Color.White.copy(alpha = 0.3f),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${(quality * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}