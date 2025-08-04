package com.aican.biometricattendance.presentation.screens.face_registration

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceImagePickerScreen(
    onImagePicked: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Function to copy image to app's internal storage
    suspend fun copyImageToInternalStorage(originalUri: Uri): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(originalUri)
                if (inputStream != null) {
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()

                    if (bitmap != null) {
                        // Create a file in internal storage
                        val filename = "face_image_${System.currentTimeMillis()}.jpg"
                        val file = File(context.filesDir, filename)

                        val outputStream = FileOutputStream(file)
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                        outputStream.close()

                        // Return file URI
                        Uri.fromFile(file)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("FaceImagePicker", "Error copying image: ${e.message}")
                null
            }
        }
    }

    // Launcher to pick image from gallery/files
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { originalUri ->
            isProcessing = true
            errorMessage = null

            // Use LaunchedEffect to handle the coroutine
            // We'll trigger this through a state change
            pickedUri = originalUri
        }
    }

    // Handle image processing when pickedUri changes
    LaunchedEffect(pickedUri) {
        pickedUri?.let { originalUri ->
            if (isProcessing) {
                try {
                    // First, try to read the image directly
                    val testStream = context.contentResolver.openInputStream(originalUri)
                    testStream?.close()

                    // If successful, copy to internal storage for reliable access
                    val copiedUri = copyImageToInternalStorage(originalUri)
                    if (copiedUri != null) {
                        pickedUri = copiedUri
                    } else {
                        errorMessage = "Failed to process the selected image. Please try again."
                        pickedUri = null
                    }
                } catch (e: Exception) {
                    Log.e("FaceImagePicker", "Error processing image: ${e.message}")
                    errorMessage = "Cannot access the selected image. Please try selecting from your device's gallery."
                    pickedUri = null
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pick Face Image") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                when {
                    isProcessing -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Processing image...")
                    }

                    pickedUri != null && !isProcessing -> {
                        // Show a preview of the picked image in a circle
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(Color.DarkGray.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = pickedUri,
                                contentDescription = "Picked Face",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { pickedUri?.let(onImagePicked) },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            Text("Use This Image")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                pickedUri = null
                                errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            Text("Choose Different Image")
                        }
                    }

                    else -> {
                        Text(
                            "Select an image from your gallery or files to begin face registration.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        Button(
                            onClick = {
                                launcher.launch("image/*")
                                errorMessage = null
                            }
                        ) {
                            Text("Choose Image")
                        }
                    }
                }

                // Show error message if any
                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}