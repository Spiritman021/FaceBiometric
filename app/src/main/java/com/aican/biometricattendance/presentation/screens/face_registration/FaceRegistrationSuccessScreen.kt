package com.aican.biometricattendance.presentation.screens.face_registration


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceRegistrationSuccessScreen(
    name: String,
    employeeId: String,
    onNavigateHome: () -> Unit
) {
    BackHandler {
        // Disable back or navigate to dashboard instead
        onNavigateHome()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registration Successful") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(80.dp)
            )

            Text("Employee Registered!", style = MaterialTheme.typography.headlineSmall)

            Column(horizontalAlignment = Alignment.Start) {
                Text("👤 Name: $name", fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("🆔 ID: $employeeId", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onNavigateHome) {
                Text("Back to Dashboard")
            }
        }
    }
}
