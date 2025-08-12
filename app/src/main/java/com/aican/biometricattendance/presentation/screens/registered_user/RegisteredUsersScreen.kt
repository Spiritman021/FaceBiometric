package com.aican.biometricattendance.presentation.screens.registered_user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aican.biometricattendance.presentation.theme.AttendanceTheme.Divider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisteredUsersScreen(
    viewModel: RegisteredUsersViewModel,
    onBack: () -> Unit
) {
    val users = viewModel.users

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registered Users") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "Total Registered Users: ${users.size}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (users.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No registered users found.")
                }
            } else {
                // Table Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEEEEEE))
                        .padding(vertical = 8.dp),
                ) {
                    Text("Name", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Employee ID", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(4.dp))

                // Table Body
                LazyColumn {
                    items(users) { user ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(Color(0xFFF7F7F7)),
                        ) {
                            Text(user.name, Modifier.weight(1f))
                            Text(user.employeeId, Modifier.weight(1f))
                        }

                        Divider()
                    }
                }
            }
        }
    }
}
