package com.aican.biometricattendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.aican.biometricattendance.navigation.GlobalNavigationHost
import com.aican.biometricattendance.presentation.screens.camera.CameraPreviewViewModel
import com.aican.biometricattendance.presentation.screens.face_registration.FaceRegistrationViewModel
import org.koin.androidx.viewmodel.ext.android.getViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {

//            val cameraPreviewViewModel: CameraPreviewViewModel = getViewModel()
            val navigationHostController = rememberNavController()
            val faceRegistrationViewModel: FaceRegistrationViewModel = getViewModel()

            GlobalNavigationHost(
//                cameraPreviewViewModel = cameraPreviewViewModel,
                faceRegistrationViewModel =faceRegistrationViewModel,
                navHostController = navigationHostController,
            )
        }

    }
}
