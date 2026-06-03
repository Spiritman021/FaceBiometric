package com.aican.biometricattendance.presentation.screens.registered_user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aican.biometricattendance.data.db.entity.FaceEmbeddingEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisteredUsersScreen(
    viewModel: RegisteredUsersViewModel,
    onBack: () -> Unit
) {
    val users = viewModel.users
    var toDelete by remember { mutableStateOf<FaceEmbeddingEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registered Users") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                // Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEEEEEE))
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Name", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Employee ID", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(40.dp)) // space for delete icon
                }

                Spacer(Modifier.height(4.dp))

                LazyColumn {
                    items(users, key = { it.employeeId }) { user ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 12.dp)
                                .background(Color(0xFFF7F7F7)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(user.name, Modifier.weight(1f))
                            Text(user.employeeId, Modifier.weight(1f))

                            IconButton(
                                onClick = { toDelete = user }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirm dialog
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete user") },
            text = { Text("Are you sure you want to delete ${toDelete!!.name} (${toDelete!!.employeeId})? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteUserByEmployeeId(toDelete!!.employeeId)
                        toDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel") }
            }
        )
    }
}
