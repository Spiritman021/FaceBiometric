package com.aican.biometricattendance.presentation.screens.camera


import android.net.Uri
import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aican.biometricattendance.data.models.camera.enums.SimpleFaceStatus
import com.aican.biometricattendance.data.models.enums.CameraType
import com.aican.biometricattendance.presentation.components.camera.*
import com.aican.biometricattendance.presentation.screens.camera.components.DebugOverlay
import com.aican.biometricattendance.presentation.screens.camera.components.SimpleCaptureButton
import com.aican.biometricattendance.presentation.screens.camera.components.SimpleInstructionText
import com.aican.biometricattendance.presentation.screens.camera.components.SimpleStatusIndicator

@Composable
fun SimplifiedCameraPreviewContent(
    viewModel: SimplifiedCameraPreviewViewModel,
    id: String,
    onNavigateToFaceRegistration: (Uri) -> Unit,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()

    // UI States
    val shutterFlash by viewModel.shutterFlash.collectAsState()
    val isProcessingPhoto by viewModel.isProcessingPhoto.collectAsState()
    val faceBoxes by viewModel.faceBoxes.collectAsState()
    val previewSize = remember { mutableStateOf(Size.Zero) }

    // Simple face detection states
    val simpleFaceStatus by viewModel.simpleFaceStatus.collectAsState()
    val capturedFaceUri by viewModel.capturedFaceUri.collectAsState()

    // Set camera type to registration
    viewModel.updateCameraType(CameraType.REGISTRATION)

    // Bind camera when preview size is available
    LaunchedEffect(previewSize.value, lifecycleOwner) {
        if (previewSize.value.width > 0 && previewSize.value.height > 0) {
            viewModel.updatePreviewSize(previewSize.value.width, previewSize.value.height)
            viewModel.bindToCamera(context, lifecycleOwner)
        }
    }

    // Navigate to face registration when face is captured
    LaunchedEffect(capturedFaceUri) {
        capturedFaceUri?.let { uri ->
            viewModel.unbindCamera()
            onNavigateToFaceRegistration(uri)
            viewModel.clearCapturedFaceUri()
        }
    }

    // Clean up camera when leaving composition
    DisposableEffect(lifecycleOwner) {
        onDispose {
            viewModel.unbindCamera()
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Camera preview area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { layoutCoordinates ->
                        val width = layoutCoordinates.size.width.toFloat()
                        val height = layoutCoordinates.size.height.toFloat()
                        Log.d("SimplifiedCamera", "Preview size: $width x $height")
                        previewSize.value = Size(width, height)
                    }
            ) {
                // Camera viewfinder
                surfaceRequest?.let { request ->
                    CameraXViewfinder(
                        surfaceRequest = request,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Shutter flash effect
                if (shutterFlash) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.6f))
                    )
                }

                // Simple top bar with close button and status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status indicator
                    SimpleStatusIndicator(
                        faceStatus = simpleFaceStatus,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Simple positioning guide (just an oval outline)
                FacePositioningGuide(
                    modifier = Modifier.fillMaxSize(),
                    guideColor = when (simpleFaceStatus) {
                        SimpleFaceStatus.FACE_DETECTED -> Color(0xFF4CAF50)
                        SimpleFaceStatus.POOR_QUALITY -> Color(0xFFFF9800)
                        SimpleFaceStatus.NO_FACE -> Color.White
                        else -> {
                            Color.White
                        }
                    }
                )

                // Instruction text at bottom of preview
                SimpleInstructionText(
                    faceStatus = simpleFaceStatus,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp, start = 32.dp, end = 32.dp)
                )
                DebugOverlay(
                    faceBoxes = faceBoxes,
                    previewWidth = previewSize.value.width,
                    previewHeight = previewSize.value.height,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // User ID display
            Text(
                text = "Capturing face for: $id",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            )

            // Bottom control area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(color = Color.Black)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    // Simple quality indicator (optional)
                    if (faceBoxes.isNotEmpty()) {
                        QualityIndicator(
                            quality = viewModel.getFaceQualityScore(),
                            isVisible = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Simple capture button
                    SimpleCaptureButton(
                        faceStatus = simpleFaceStatus,
                        onClick = {
                            if (viewModel.isFaceSuitableForCapture()) {
                                viewModel.capturePhoto(context) { croppedUri ->
                                    // Success callback - URI will be handled by LaunchedEffect
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Simple status text
                    Text(
                        text = when (simpleFaceStatus) {
                            SimpleFaceStatus.FACE_DETECTED -> "Tap to capture"
                            SimpleFaceStatus.POOR_QUALITY -> "Adjust position"
                            SimpleFaceStatus.NO_FACE -> "No face detected"
                            else -> {
                                ""
                            }
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    // Processing overlay
    if (isProcessingPhoto) {
        ProcessingOverlay()
    }
}