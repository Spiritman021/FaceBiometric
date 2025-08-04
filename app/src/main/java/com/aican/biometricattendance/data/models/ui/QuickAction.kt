package com.aican.biometricattendance.data.models.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class QuickAction(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val color: Color,
    val onClick: () -> Unit = {}
)