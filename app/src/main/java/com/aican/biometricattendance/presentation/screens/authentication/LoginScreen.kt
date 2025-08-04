package com.aican.biometricattendance.presentation.screens.authentication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
fun LoginScreen() {
    val emailState = remember { mutableStateOf("") }
    val passwordState = remember { mutableStateOf("") }
    val showPassword = remember { mutableStateOf(false) }
    val rememberMe = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 40.dp, top = 160.dp)
    ) {
        // Logo row
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFDBB41),
                                Color(0xFFF78513)
                            )
                        ),
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Optiwise",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0xFF223144), shape = RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Sign In",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF223144)
        )
        Text(
            text = "Enter your credentials to access the software",
            color = Color(0xFF728093),
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        // Email
        OutlinedTextField(
            value = emailState.value,
            onValueChange = { emailState.value = it },
            placeholder = { Text("Enter Email", color = Color(0xFFBFC4D2)) },
            modifier = Modifier.fillMaxWidth(0.85f),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Gray,
                disabledTextColor = Color.LightGray,
                errorTextColor = Color.Red,

                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color(0xFFE0E0E0),
                disabledContainerColor = Color(0xFFF0F0F0),
                errorContainerColor = Color(0xFFFFE0E0),
            ),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Password
        OutlinedTextField(
            value = passwordState.value,
            onValueChange = { passwordState.value = it },
            placeholder = { Text("Password", color = Color(0xFFBFC4D2)) },
            modifier = Modifier.fillMaxWidth(0.85f),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Gray,
                disabledTextColor = Color.LightGray,
                errorTextColor = Color.Red,

                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color(0xFFE0E0E0),
                disabledContainerColor = Color(0xFFF0F0F0),
                errorContainerColor = Color(0xFFFFE0E0),
            ),
            singleLine = true,
            visualTransformation = if (showPassword.value) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword.value = !showPassword.value }) {
                    Icon(
                        imageVector = if (showPassword.value) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Toggle Password"
                    )
                }
            },
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Checkbox(
                checked = rememberMe.value,
                onCheckedChange = { rememberMe.value = it }
            )
            Text("Remember Me", color = Color(0xFF223144))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { /* TODO: Handle sign in */ },
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF223144)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Sign In", color = Color.White, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            Text(
                text = "Not a member?",
                color = Color(0xFF728093)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Sign Up",
                color = Color(0xFF223144),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
