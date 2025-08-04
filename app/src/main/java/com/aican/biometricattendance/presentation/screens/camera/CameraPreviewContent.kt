package com.aican.biometricattendance.presentation.screens.camera

import android.net.Uri
import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aican.biometricattendance.presentation.components.camera.BoundaryGuideOverlay
import com.aican.biometricattendance.presentation.components.camera.CameraTopBar
import com.aican.biometricattendance.presentation.components.camera.ControlButton
import com.aican.biometricattendance.presentation.components.camera.DebugInfoText
import com.aican.biometricattendance.presentation.components.camera.EnhancedFaceOverlay
import com.aican.biometricattendance.presentation.components.camera.FaceInstructionText
import com.aican.biometricattendance.presentation.components.camera.FacePositioningGuide
import com.aican.biometricattendance.presentation.components.camera.LivenessStatusIndicator
import com.aican.biometricattendance.presentation.components.camera.ProcessingOverlay
import com.aican.biometricattendance.presentation.components.camera.QualityIndicator
import com.aican.biometricattendance.presentation.components.camera.SmartCaptureButton
import com.google.android.datatransport.BuildConfig

@Composable
fun CameraPreviewContent(
    viewModel: CameraPreviewViewModel,
    onNavigateToFaceRegistration: (Uri) -> Unit,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // UI States
    val shutterFlash by viewModel.shutterFlash.collectAsState()
    val showDialog by viewModel.showPreviewDialog.collectAsState()
    val capturedUri by viewModel.capturedPhotoUri.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val isProcessingPhoto by viewModel.isProcessingPhoto.collectAsState()
    val faceBoxes by viewModel.faceBoxes.collectAsState()
    val previewSize = remember { mutableStateOf(Size.Zero) }

    // Enhanced face detection states
    val livenessStatus by viewModel.livenessStatus.collectAsState()

    val showCaptureDialog by viewModel.showCaptureDialog.collectAsState()
    val capturedFaceUri by viewModel.capturedFaceUri.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()

    // Handle camera binding with fresh start



//    LaunchedEffect(previewSize.value) {
//        Log.d("CameraPreviewContent", "Preview size updated: ${previewSize.value}")
//        if (previewSize.value.width > 0 && previewSize.value.height > 0) {
//            viewModel.updatePreviewSize(previewSize.value.width, previewSize.value.height)
//
//        }
//    }

    LaunchedEffect(previewSize.value, lifecycleOwner) {
        if (previewSize.value.width > 0 && previewSize.value.height > 0) {
            viewModel.updatePreviewSize(previewSize.value.width, previewSize.value.height)
            viewModel.bindToCamera(context, lifecycleOwner)
        }
    }


//    LaunchedEffect(lifecycleOwner) {
//        Log.d("CameraPreviewContent", "Binding to camera")
//        viewModel.bindToCamera(context.applicationContext, lifecycleOwner)
//    }


    //Preview size updated: Size(1080.0, 1624.0)
    //  Preview size updated: Size(1080.0, 1624.0)

    // Navigate to face capture screen when face is captured
    LaunchedEffect(capturedFaceUri) {
        capturedFaceUri?.let { uri ->
            // Unbind camera before navigation to prevent surface abandonment
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
        // ... rest of your existing UI code remains the same ...
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { layoutCoordinates ->
                        val width = layoutCoordinates.size.width.toFloat()
                        val height = layoutCoordinates.size.height.toFloat()
                        Log.d("CameraPreviewContent", "Preview size updated2: $width x $height")
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

                // Top bar
                CameraTopBar(onClose = {})

                // Boundary guide overlay for edge detection issues
                BoundaryGuideOverlay(
                    livenessStatus = livenessStatus,
                    modifier = Modifier.matchParentSize()
                )

                // Face positioning guide for poor position
                FacePositioningGuide(
                    livenessStatus = livenessStatus,
                    modifier = Modifier.matchParentSize()
                )

                // Enhanced face overlay with boundary indication
                EnhancedFaceOverlay(
                    faceBoxes = faceBoxes,
                    livenessStatus = livenessStatus,
                    modifier = Modifier.matchParentSize()
                )

                // Status indicator at top
                LivenessStatusIndicator(
                    status = livenessStatus,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                )

                // Instruction text with detailed guidance
                FaceInstructionText(
                    livenessStatus = livenessStatus,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .padding(horizontal = 32.dp)
                )
            }

            // Enhanced bottom control bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = Color.Black)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Quality indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        QualityIndicator(
                            quality = viewModel.getFaceQualityScore(),
                            isVisible = faceBoxes.isNotEmpty()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Control buttons row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Camera toggle button
                        ControlButton(
                            icon = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Toggle Camera",
                            onClick = { viewModel.toggleCamera() }
                        )

                        // Enhanced capture button for cropped face
                        SmartCaptureButton(
                            livenessStatus = livenessStatus,
                            onClick = {
                                if (viewModel.isFaceSuitableForCapture()) {
                                    viewModel.capturePhoto(context) { croppedUri ->
                                        // Handle successful cropped face capture
                                        // The URI points to the cropped face image only
                                    }
                                }
                            }
                        )

                        // Flash toggle button
                        ControlButton(
                            icon = Icons.Default.FlashOn,
                            contentDescription = "Toggle Flash",
                            onClick = { viewModel.toggleFlash() }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Debug info (remove in production)
                    if (BuildConfig.DEBUG) {
                        DebugInfoText(
                            livenessStatus = livenessStatus,
                            quality = viewModel.getFaceQualityScore(),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }

    // Processing overlay
    if (isProcessingPhoto) {
        ProcessingOverlay()
    }
}
