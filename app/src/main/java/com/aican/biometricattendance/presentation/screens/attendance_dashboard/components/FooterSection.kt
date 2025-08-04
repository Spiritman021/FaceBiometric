package com.aican.biometricattendance.presentation.screens.attendance_dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aican.biometricattendance.presentation.theme.AttendanceTheme


@Composable
fun FooterSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        Text(
            text = "Need help?",
            fontSize = 14.sp,
            color = AttendanceTheme.OnSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = "📧 support@facesecure.com",
                fontSize = 13.sp,
                color = AttendanceTheme.Primary,
                modifier = Modifier.clickable { /* Email */ }
            )
            Text(
                text = "📞 +91 92117 12997",
                fontSize = 13.sp,
                color = AttendanceTheme.Primary,
                modifier = Modifier.clickable { /* Call */ }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Version 1.0.0 • © 2024 FaceSecure",
            fontSize = 11.sp,
            color = AttendanceTheme.OnSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}