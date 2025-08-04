package com.aican.biometricattendance.data.models.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector


data class MenuItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val trailing: @Composable (() -> Unit)? = null,
    val onClick: () -> Unit = {}
)