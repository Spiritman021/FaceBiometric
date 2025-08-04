package com.aican.biometricattendance.presentation.screens.attendance_dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aican.biometricattendance.presentation.screens.attendance_dashboard.StatCard
import com.aican.biometricattendance.presentation.theme.AttendanceTheme


@Composable
fun QuickStatsSection() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "Today",
            value = "24",
            subtitle = "Present",
            color = AttendanceTheme.Secondary,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "This Week",
            value = "156",
            subtitle = "Check-ins",
            color = AttendanceTheme.Primary,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Total",
            value = "45",
            subtitle = "Employees",
            color = AttendanceTheme.Accent,
            modifier = Modifier.weight(1f)
        )
    }
}
