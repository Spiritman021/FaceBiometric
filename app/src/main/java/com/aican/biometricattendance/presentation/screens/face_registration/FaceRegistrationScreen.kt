package com.aican.biometricattendance.presentation.screens.face_registration

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aican.biometricattendance.ml.facenet.UnifiedFaceEmbeddingProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceRegistrationScreen(
    viewModel: FaceRegistrationViewModel,
    id: String,
    onNavigateBack: () -> Unit,
    onSubmissionSuccess: (String, String) -> Unit
) {
    val context = LocalContext.current

    val capturedFaceUri by viewModel.capturedFaceUri.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userId by viewModel.userId.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val submitError by viewModel.submitError.collectAsStateWithLifecycle()
    val isSubmitted by viewModel.isSubmitted.collectAsStateWithLifecycle()
    val faceEmbedding by viewModel.faceEmbedding.collectAsStateWithLifecycle()

    // New state: if matched email is found
    var matchedId by remember { mutableStateOf<String?>(null) }
    var showProceedAnyway by remember { mutableStateOf(false) }

    LaunchedEffect(matchedId) {
        if (matchedId == null) {
            viewModel.updateUserId(id)
            viewModel.updateUserName("")
        }
    }

    LaunchedEffect(isSubmitted) {
        if (isSubmitted) {
            onSubmissionSuccess(userName, userId)

        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.reset()
        }
    }

    suspend fun processImageSafely(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = inputStream?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    val embeddingGenerator = UnifiedFaceEmbeddingProcessor(context)
                    val embeddingResult = embeddingGenerator.generateEmbedding(bitmap)
                    embeddingGenerator.close()

                    if (embeddingResult.success && embeddingResult.embedding != null) {
                        val matchedFaceData =
                            viewModel.findMatchingEmail(embeddingResult.embedding!!)
                        withContext(Dispatchers.Main) {
                            if (matchedFaceData.employeeId.isNotBlank()) {
                                matchedId = matchedFaceData.employeeId
                                viewModel.updateUserId(matchedFaceData.employeeId)
                                viewModel.updateUserName(matchedFaceData.name)
                            }
                            viewModel.updateFaceEmbedding(embeddingResult.embedding)
                        }
                    }
                    true
                } else false
            } catch (e: Exception) {
                Log.e("FaceRegistrationScreen", "Image processing error: ${e.message}")
                false
            }
        }
    }

    LaunchedEffect(capturedFaceUri) {
        capturedFaceUri?.let { uri ->
            if (userId.isBlank()) {
                processImageSafely(uri)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Face Registration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !isSubmitting) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Face match warning section
            if (matchedId != null && !showProceedAnyway) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "This face appears to be already registered with:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Email: $matchedId",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(modifier = Modifier.height(2.dp))
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            "Proceed Anyway with: $id", modifier = Modifier.align(Alignment.End),
                            fontSize = 10.sp
                        )
                        Button(
                            onClick = {
                                viewModel.updateUserId(id)
                                viewModel.updateUserName("")
                                showProceedAnyway = true
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Proceed Anyway")
                        }


                    }
                }
            }

            if (faceEmbedding != null) {
                Text(
                    text = "Embedding Preview:\n" +
                            faceEmbedding!!.take(16).joinToString(", ") { "%.3f".format(it) } +
                            if (faceEmbedding!!.size > 16) "\n..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            // Face image
            capturedFaceUri?.let { uri ->
                Card(
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .size(200.dp)
                        .padding(bottom = 24.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Captured Face",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Inside
                    )
                }
            }

            if (matchedId == null || showProceedAnyway) {
                // Form fields
                Text(
                    text = "Please provide your details",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = userName,
                    onValueChange = viewModel::updateUserName,
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    singleLine = true,
                    enabled = !isSubmitting
                )

                OutlinedTextField(
                    value = userId,
                    onValueChange = viewModel::updateUserId,
                    label = { Text("Employee ID") },
                    leadingIcon = { Icon(Icons.Default.VerifiedUser, null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    singleLine = true,
                    enabled = !isSubmitting,
                    isError = userId.isBlank()
                )

                if (userId.isBlank()) {
                    Text(
                        text = "Please enter a valid user id",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Submit error
                submitError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        onClick = { viewModel.submitFaceData(context) },
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(2f)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submitting...")
                        } else {
                            Text("Submit Registration")
                        }
                    }
                }
            }
        }
    }
}
