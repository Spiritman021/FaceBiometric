package com.aican.biometricattendance.presentation.screens.face_registration


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aican.biometricattendance.data.db.entity.FaceEmbeddingEntity
import com.aican.biometricattendance.data.db.repository.FaceEmbeddingRepository
import com.aican.biometricattendance.presentation.screens.camera.CameraPreviewViewModel
import kotlinx.coroutines.launch

@Composable
fun CheckEmployeeScreen(
    modifier: Modifier = Modifier,
    faceRegistrationViewModel: FaceRegistrationViewModel,
    navController: NavController // To navigate to register screen
    , proceedToRegister: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var employeeId by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FaceEmbeddingEntity?>(null) }
    var checked by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    Scaffold { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            Text(
                text = "🔍 Check whether employee exists or not",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = employeeId,
                onValueChange = { employeeId = it },
                label = { Text("Enter Employee ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (employeeId.isNotBlank()) {
                        loading = true
                        checked = false
                        coroutineScope.launch {
                            result = faceRegistrationViewModel.findByEmployeeId(employeeId)
                            loading = false
                            checked = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Check")
            }

            if (loading) {
                CircularProgressIndicator()
            } else if (checked) {
                Spacer(modifier = Modifier.height(16.dp))
                if (result != null) {

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Text("✅ Employee Found", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Name: ${result?.name}")
                            Text("Employee ID: ${result?.employeeId}")
                            Text("Embedding: ${result?.embedding?.size ?: 0} bytes")
                        }
                    }
                } else {
                    // Employee not found
                    Text("❌ Employee not found", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            proceedToRegister.invoke(employeeId)
                        }
                    ) {
                        Text("Proceed to Register")
                    }
                }
            }
        }
    }

}
