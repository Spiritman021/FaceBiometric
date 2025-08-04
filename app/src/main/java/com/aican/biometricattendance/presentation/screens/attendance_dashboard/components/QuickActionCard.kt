package com.aican.biometricattendance.presentation.screens.attendance_dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aican.biometricattendance.data.models.ui.QuickAction
import com.aican.biometricattendance.presentation.theme.AttendanceTheme


@Composable
fun QuickActionCard(action: QuickAction, modifier: Modifier,) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = AttendanceTheme.Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { action.onClick() }
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(action.color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    action.icon,
                    contentDescription = null,
                    tint = action.color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = action.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AttendanceTheme.OnSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = action.subtitle,
                fontSize = 12.sp,
                color = AttendanceTheme.OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
