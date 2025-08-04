package com.aican.biometricattendance.presentation.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    splashTime: Long = 1500L,
    onSplashComplete: () -> Unit
) {
    val primaryBlue = Color(0xFF0B4478)
    val accentOrange = Color(0xFFFFA726)
    val logo = Icons.Default.VerifiedUser

    LaunchedEffect(Unit) {
        delay(splashTime)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(primaryBlue)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = logo,
                contentDescription = "App Logo",
                tint = accentOrange,
                modifier = Modifier.size(92.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "FaceSecure",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Effortless & Secure Attendance",
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(30.dp))
            CircularProgressIndicator(
                color = accentOrange,
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}
