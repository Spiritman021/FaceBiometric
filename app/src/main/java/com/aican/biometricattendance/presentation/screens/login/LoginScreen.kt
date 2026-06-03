package com.aican.biometricattendance.presentation.screens.login


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aican.biometricattendance.R
import com.aican.biometricattendance.navigation.routes.AppRoutes
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel,
) {
    val ui by viewModel.ui.collectAsState()
    var obscure by remember { mutableStateOf(true) }

    // Navigate on success
    LaunchedEffect(ui.isSuccess) {
        if (ui.isSuccess) {
            // replace stack with Dashboard
            navController.navigate(AppRoutes.ROUTE_DASHBOARD.route) {
                popUpTo(0)
                launchSingleTop = true
            }
        }
    }

    // Show transient error
    ui.errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            delay(100)
            // You can replace with SnackbarHost if you prefer
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Sign In") })
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            // Logo
            Card(
                modifier = Modifier.size(140.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                // replace with your logo
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Logo",
                        modifier = Modifier.size(96.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Enter your credentials to access the software", color = Color.Gray)

            Spacer(Modifier.height(24.dp))

            // Email
            OutlinedTextField(
                value = ui.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Password
            OutlinedTextField(
                value = ui.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = {
                    IconButton(onClick = { obscure = !obscure }) {
                        Icon(
                            imageVector = if (obscure) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(24.dp))

            if (ui.isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { viewModel.login() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign In", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            AnimatedVisibility(ui.errorMessage != null) {
                ui.errorMessage?.let {
                    Text(it, color = Color(0xFFD32F2F))
                }
            }
        }
    }
}
