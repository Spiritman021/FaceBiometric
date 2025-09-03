package com.aican.biometricattendance.presentation.screens.mark_attendance

import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.aican.biometricattendance.data.models.camera.enums.LivenessStatus
import com.aican.biometricattendance.presentation.components.camera.BoundaryGuideOverlay
import com.aican.biometricattendance.presentation.components.camera.CameraTopBar
import com.aican.biometricattendance.presentation.components.camera.DebugInfoText
import com.aican.biometricattendance.presentation.components.camera.EnhancedFaceOverlay
import com.aican.biometricattendance.presentation.components.camera.FaceInstructionText
import com.aican.biometricattendance.presentation.components.camera.FacePositioningGuide
import com.aican.biometricattendance.presentation.components.camera.ProcessingOverlay
import com.aican.biometricattendance.presentation.components.camera.QualityIndicator
import com.aican.biometricattendance.presentation.screens.mark_attendance.components.AttendanceStatusDialog
import com.google.android.datatransport.BuildConfig

@Composable
fun AttendanceCameraPreview(
    // ## CHANGE 1: Remove the `id` parameter ##
    // id: String,
    navController: NavHostController,
    attendanceVerificationViewModel: AttendanceVerificationViewModel,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val faceBoxes by attendanceVerificationViewModel.faceBoxes.collectAsState()
    val livenessStatus by attendanceVerificationViewModel.livenessStatus.collectAsState()
    val previewSize = remember { mutableStateOf(Size.Zero) }
    val isProcessingPhoto by attendanceVerificationViewModel.isProcessingPhoto.collectAsState()
    val surfaceRequest by attendanceVerificationViewModel.surfaceRequest.collectAsStateWithLifecycle()
    val shutterFlash by attendanceVerificationViewModel.shutterFlash.collectAsState()

    // Auto capture states
    val isAutoProcessing by attendanceVerificationViewModel.isAutoProcessing.collectAsState()
    val captureStatus by attendanceVerificationViewModel.captureStatus.collectAsState()
    val similarityScore by attendanceVerificationViewModel.similarityScore.collectAsState()
    val attendanceResult by attendanceVerificationViewModel.attendanceResult.collectAsState()
    val guideColor = when (livenessStatus) {
        LivenessStatus.LIVE_FACE -> Color(0xFF4CAF50) // Green for success (Ready to Capture)
        LivenessStatus.CHECKING -> Color.Yellow      // Yellow while checking
        LivenessStatus.NO_FACE -> Color.White       // Default white
        else -> Color(0xFFFF5722)                     // Red for any issue (poor position, etc.)
    }


    // ## NEW: State to control dialog visibility and hold data ##
    var showStatusDialog by remember { mutableStateOf(false) }
    var dialogData by remember { mutableStateOf<Pair<String, Float>?>(null) }

    // ## CHANGE 2: Update navigation logic to use the ID from the result ##
    LaunchedEffect(attendanceResult) {
        val result = attendanceResult
        if (result?.success == true && result.matchedEmployeeId != null) {
            val matchPercent = (similarityScore * 100)
            attendanceVerificationViewModel.pauseAnalysis()

            // ## CHANGE: Instead of navigating, set the state to show the dialog ##
            dialogData = Pair(result.matchedEmployeeId, matchPercent)
            showStatusDialog = true

            // Original navigation is removed
            // navController.navigate("mark-status/${result.matchedEmployeeId}/$matchPercent")
        }
    }

    // ## CHANGE 3: Remove the LaunchedEffect that depended on 'id' ##
    // LaunchedEffect(id) {
    //     attendanceVerificationViewModel.getFaceEmbeddingFromDatabase(id)
    // }

    LaunchedEffect(previewSize.value, lifecycleOwner) {
        if (previewSize.value.width > 0 && previewSize.value.height > 0) {
            attendanceVerificationViewModel.updatePreviewSize(
                previewSize.value.width,
                previewSize.value.height
            )
            attendanceVerificationViewModel.bindToCamera(context, lifecycleOwner)
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            attendanceVerificationViewModel.unbindCamera()
            // Reset state when leaving the screen to be ready for the next person
            attendanceVerificationViewModel.resetVerificationState()
        }
    }

    Scaffold { paddingValues ->
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
                FacePositioningGuide(
                    modifier = Modifier.fillMaxSize(),
                    guideColor = guideColor // The color will change based on the live status
                )
                // UI Overlays
                CameraTopBar(livenessStatus = livenessStatus, onClose = onClose)
                BoundaryGuideOverlay(
                    livenessStatus = livenessStatus,
                    modifier = Modifier.matchParentSize()
                )
//                EnhancedFaceOverlay(faceBoxes = faceBoxes, livenessStatus = livenessStatus, modifier = Modifier.matchParentSize())
                AutoCaptureStatusOverlay(
                    captureStatus = captureStatus,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }

            // ## CHANGE 4: Update the instructional text ##

            Text(
                text = "Align your face in oval face area, and blink your eyes",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth()
            )

            Text(
                text = "Searching for a registered face...",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth()
            )

            // Bottom control bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(color = Color.Black)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    FaceInstructionText(
                        livenessStatus = livenessStatus,
                        modifier = Modifier.padding(bottom = 16.dp, start = 32.dp, end = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        QualityIndicator(
                            quality = attendanceVerificationViewModel.getFaceQualityScore(),
                            isVisible = faceBoxes.isNotEmpty()
                        )
                    }

                    // Show the result card only when there's a result
                    attendanceResult?.let { result ->
                        Spacer(modifier = Modifier.height(8.dp))
                        AttendanceResultCard(
                            result = result,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f)) // Pushes debug info to the bottom

                    if (BuildConfig.DEBUG) {
                        DebugInfoText(
                            livenessStatus = livenessStatus,
                            quality = attendanceVerificationViewModel.getFaceQualityScore(),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }

    if (isProcessingPhoto || isAutoProcessing) {
        ProcessingOverlay()
    }

    if (showStatusDialog && dialogData != null) {
        AttendanceStatusDialog(
            employeeId = dialogData!!.first,
            matchPercent = dialogData!!.second,
            viewModel = attendanceVerificationViewModel,
            onDismiss = {
                showStatusDialog = false
                dialogData = null
                attendanceVerificationViewModel.resumeAnalysis()
                // Resetting the state makes the camera ready for the next person
                attendanceVerificationViewModel.resetVerificationState()
            }
        )
    }
}

@Composable
fun AutoCaptureStatusOverlay(
    captureStatus: CaptureStatus,
    modifier: Modifier = Modifier,
) {
    if (captureStatus == CaptureStatus.IDLE || captureStatus == CaptureStatus.COMPLETED) {
        // Don't show overlay for these states
        return
    }

    val (text, color) = when (captureStatus) {
        CaptureStatus.CAPTURING -> "Capturing..." to MaterialTheme.colorScheme.primary
        CaptureStatus.PROCESSING -> "Processing Face..." to MaterialTheme.colorScheme.secondary
        else -> "" to Color.Transparent
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AttendanceResultCard(
    result: AttendanceResult,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (result.success) Color(0xFF4CAF50) else Color(0xFFFF5722)
    val title = if (result.success) "✅ Verification Passed" else "❌ Verification Failed"

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.message,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
