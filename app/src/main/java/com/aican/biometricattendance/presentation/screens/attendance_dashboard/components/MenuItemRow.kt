package com.aican.biometricattendance.presentation.screens.attendance_dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aican.biometricattendance.data.models.ui.MenuItem
import com.aican.biometricattendance.presentation.theme.AttendanceTheme


@Composable
fun MenuItemRow(
    item: MenuItem,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { item.onClick() }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AttendanceTheme.Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = AttendanceTheme.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = AttendanceTheme.OnSurface
                )
                Text(
                    text = item.subtitle,
                    fontSize = 13.sp,
                    color = AttendanceTheme.OnSurfaceVariant
                )
            }

            item.trailing?.invoke()

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = AttendanceTheme.OnSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        if (showDivider) {
            Divider(
                color = AttendanceTheme.Divider,
                modifier = Modifier.padding(start = 76.dp)
            )
        }
    }
}