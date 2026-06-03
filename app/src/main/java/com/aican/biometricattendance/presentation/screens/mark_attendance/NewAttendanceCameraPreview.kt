package com.aican.biometricattendance.presentation.screens.mark_attendance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController

@Composable
fun NewAttendanceCameraPreview(
    navController: NavHostController,
    vm: NewAttendanceCameraViewModel,
    onClose: () -> Unit,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    val context = LocalContext.current
    val faceBoxes by vm.faceBoxes.collectAsState()
    val liveness by vm.livenessStatus.collectAsState()
    val quality by vm.faceQuality.collectAsState()
    val similarity by vm.similarityScore.collectAsState()
    val result by vm.attendanceResult.collectAsState()

    // Your existing CameraX binding (preview + analyzer)
    // 1) Bind camera & analyzer
    // 2) In analyzer callback, call vm.onFrame(faceBitmap, boxes, liveness, quality)

    LaunchedEffect(result) {
        result?.takeIf { it.success && !it.employeeId.isNullOrBlank() }?.let { r ->
            val percent = (r.similarity * 100f)
            navController.navigate("mark-status/${r.employeeId}/$percent")
            // Optionally reset vm state if you plan to re-open quickly:
            // vm.resetState()
        }
    }

    // --- UI ---
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 1) Camera viewfinder (same as your old surfaceRequest + CameraXViewfinder)
            // 2) Overlays: face boxes, liveness/quality banners
            // 3) Optional similarity bar (hide in prod)

            // Example overlays (reuse yours):
            // EnhancedFaceOverlay(faceBoxes = faceBoxes, livenessStatus = liveness, modifier = Modifier.matchParentSize())
            // QualityIndicator(quality = quality, isVisible = faceBoxes.isNotEmpty())
            // SimilarityScoreDisplay(score = similarity) // if you want

            // Top bar / close:
            // CameraTopBar(livenessStatus = liveness, onClose = onClose)
        }
    }
}
