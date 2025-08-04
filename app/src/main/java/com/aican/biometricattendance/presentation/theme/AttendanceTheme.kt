package com.aican.biometricattendance.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object AttendanceTheme {
    val Primary = Color(0xFF2196F3)
    val PrimaryDark = Color(0xFF1976D2)
    val Secondary = Color(0xFF4CAF50)
    val Accent = Color(0xFFFF9800)
    val Error = Color(0xFFF44336)
    val Background = Color(0xFFF8FAFC)
    val Surface = Color.White
    val OnSurface = Color(0xFF1E293B)
    val OnSurfaceVariant = Color(0xFF64748B)
    val Divider = Color(0xFFE2E8F0)

    // Gradient backgrounds
    val PrimaryGradient = Brush.linearGradient(
        colors = listOf(Primary, PrimaryDark)
    )
    val SuccessGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF10B981), Color(0xFF059669))
    )
}