package com.aican.biometricattendance.presentation.screens.face_registration

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aican.biometricattendance.data.db.entity.FaceData
import com.aican.biometricattendance.ml.facenet.UnifiedFaceEmbeddingProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceRegistrationScreen(
    viewModel: FaceRegistrationViewModel,
    onNavigateBack: () -> Unit,
    onSubmissionSuccess: () -> Unit
) {
    val context = LocalContext.current

    val capturedFaceUri by viewModel.capturedFaceUri.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val submitError by viewModel.submitError.collectAsStateWithLifecycle()
    val isSubmitted by viewModel.isSubmitted.collectAsStateWithLifecycle()
    val faceEmbedding by viewModel.faceEmbedding.collectAsStateWithLifecycle()

    // Run navigation on successful submission
    LaunchedEffect(isSubmitted) {
        if (isSubmitted) {
            onSubmissionSuccess()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.reset()
        }
    }

    // Safe image processing function
    suspend fun processImageSafely(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = inputStream?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }

                if (bitmap != null) {
                    val embeddingGenerator = UnifiedFaceEmbeddingProcessor(context)
                    val embeddingResult = embeddingGenerator.generateEmbedding(bitmap)
                    embeddingGenerator.close()

                    if (embeddingResult.success && embeddingResult.embedding != null) {
                        val matchedFaceData: FaceData =
                            viewModel.findMatchingEmail(embeddingResult.embedding!!)
                        withContext(Dispatchers.Main) {
                            viewModel.updateUserEmail(matchedFaceData.email)
                            viewModel.updateUserName(matchedFaceData.name)
                        }
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e("FaceRegistrationScreen", "Error processing image: ${e.message}")
                false
            }
        }
    }

    LaunchedEffect(capturedFaceUri) {
        capturedFaceUri?.let { uri ->
            // Only try auto-match if email is blank and we have a face
            if (userEmail.isBlank()) {
                try {
                    processImageSafely(uri)
                } catch (e: Exception) {
                    Log.e("FaceRegistrationScreen", "LaunchedEffect error: ${e.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Face Registration") }, navigationIcon = {
                IconButton(
                    onClick = onNavigateBack, enabled = !isSubmitting
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
            )
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (faceEmbedding != null) {
                Text(
                    text = "Face Embedding (first 16 values):\n" + faceEmbedding!!.take(16)
                    .joinToString(", ") { "%.4f".format(it) } + if (faceEmbedding!!.size > 16) "\n..." else "",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 16.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Face captured successfully!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Rectangular image preview
            capturedFaceUri?.let { uri ->
                Card(
                    modifier = Modifier
                        .size(200.dp)
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(0.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Captured Face",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Inside
                    )
                }
            }

            Text(
                text = "Please provide your details to complete registration",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = userName,
                        onValueChange = viewModel::updateUserName,
                        label = { Text("Full Name") },
                        placeholder = { Text("Enter your full name") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person, contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        enabled = !isSubmitting,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text, imeAction = ImeAction.Next
                        ),
                        isError = userName.isBlank() && submitError != null
                    )
                    if (userName.isBlank() && submitError != null) {
                        Text(
                            "Please enter your name",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    OutlinedTextField(
                        value = userEmail,
                        onValueChange = viewModel::updateUserEmail,
                        label = { Text("Email Address") },
                        placeholder = { Text("Enter your email") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email, contentDescription = null
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email, imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (viewModel.isFormValid() && !isSubmitting) {
                                    viewModel.submitFaceData(context)
                                }
                            }),
                        isError = userEmail.isNotBlank() && !viewModel.isEmailValid())
                    if (userEmail.isNotBlank() && !viewModel.isEmailValid()) {
                        Text(
                            "Please enter a valid email address",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            submitError?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { viewModel.submitFaceData(context) }, modifier = Modifier.weight(2f)
                ) {
                    if (isSubmitting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submitting...")
                        }
                    } else {
                        Text("Submit Registration")
                    }
                }
            }
        }
    }
}