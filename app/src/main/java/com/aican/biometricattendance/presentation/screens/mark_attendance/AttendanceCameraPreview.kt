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
import com.aican.biometricattendance.data.models.enums.CameraType
import com.aican.biometricattendance.presentation.components.camera.BoundaryGuideOverlay
import com.aican.biometricattendance.presentation.components.camera.CameraTopBar
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
fun AttendanceCameraPreview(
    id: String,
    navController: NavHostController,
    attendanceVerificationViewModel: AttendanceVerificationViewModel,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onClose: () -> Unit

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
    val matchedFaceData =
        attendanceVerificationViewModel.faceEmbeddedDataFromDatabase.collectAsState().value

    LaunchedEffect(attendanceResult) {
        if (attendanceResult?.success == true && matchedFaceData != null) {


//            attendanceVerificationViewModel.resetVerificationState()


            val matchPercent = (similarityScore * 100)
            print("MatchPercent: $matchPercent")

            navController.navigate("mark-status/${matchedFaceData.employeeId}/$matchPercent")

        }
    }
    LaunchedEffect(id) {
        attendanceVerificationViewModel.getFaceEmbeddingFromDatabase(id)
    }

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
                CameraTopBar(livenessStatus = livenessStatus, onClose = onClose)

                // Boundary guide overlay for edge detection issues
                BoundaryGuideOverlay(
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

                // Auto capture status overlay
                AutoCaptureStatusOverlay(
                    captureStatus = captureStatus,
                    isProcessing = isAutoProcessing,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )

                // Instruction text with detailed guidance

            }

            Text(
                text = "Verifying face for $id",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth()
            )

            // Enhanced bottom control bar with attendance results
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp) // Increased height for attendance results
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = Color.Black)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    FaceInstructionText(
                        livenessStatus = livenessStatus,
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .padding(horizontal = 32.dp)
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
                    // Attendance result card
                    attendanceResult?.let { result ->
                        AttendanceResultCard(
                            result = result,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }



                    Spacer(modifier = Modifier.height(8.dp))

                    // Similarity score display
//                    if (similarityScore > 0) {
//                        SimilarityScoreDisplay(
//                            score = similarityScore,
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(horizontal = 32.dp)
//                        )
//                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Control buttons row (if needed for manual operations)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Add manual controls if needed
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Debug info (remove in production)
                    if (BuildConfig.DEBUG) {
                        DebugInfoText(
                            livenessStatus = livenessStatus,
                            quality = attendanceVerificationViewModel.getFaceQualityScore(),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }

    // Processing overlay
    if (isProcessingPhoto || isAutoProcessing) {
        ProcessingOverlay()
    }
}

@Composable
fun AutoCaptureStatusOverlay(
    captureStatus: CaptureStatus,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    when (captureStatus) {
        CaptureStatus.CAPTURING -> {
            Card(
                modifier = modifier,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Capturing...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        CaptureStatus.PROCESSING -> {
            Card(
                modifier = modifier,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Processing face...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        else -> { /* No overlay for other states */
        }
    }
}

@Composable
fun AttendanceResultCard(
    result: AttendanceResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (result.success) {
                Color(0xFF4CAF50) // Green for success
            } else {
                Color(0xFFFF5722) // Red for failure
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (result.success) "✅ Verification Passed" else "❌ Verification Failed",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = result.message,
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SimilarityScoreDisplay(
    score: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when {
                score >= 0.8f -> Color(0xFF4CAF50) // Green for high similarity
                score >= 0.6f -> Color(0xFFFF9800) // Orange for medium similarity
                else -> Color(0xFFFF5722) // Red for low similarity
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Similarity Score",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "${(score * 100).toInt()}%",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}