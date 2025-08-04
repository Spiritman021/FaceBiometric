package com.aican.biometricattendance.navigation


import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aican.biometricattendance.navigation.routes.AppRoutes
import com.aican.biometricattendance.presentation.screens.attendance_dashboard.FaceAttendanceScreen
import com.aican.biometricattendance.presentation.screens.camera.CameraPreviewScreen
import com.aican.biometricattendance.presentation.screens.camera.CameraPreviewViewModel
import com.aican.biometricattendance.presentation.screens.face_registration.FaceImagePickerScreen
import com.aican.biometricattendance.presentation.screens.face_registration.FaceRegistrationScreen
import com.aican.biometricattendance.presentation.screens.face_registration.FaceRegistrationViewModel
import com.aican.biometricattendance.presentation.screens.face_registration.SubmissionSuccessScreen
import com.aican.biometricattendance.presentation.screens.splash.SplashScreen
import org.koin.androidx.compose.koinViewModel


@Composable
fun GlobalNavigationHost(
    navHostController: NavHostController,
    faceRegistrationViewModel: FaceRegistrationViewModel,
) {

    NavHost(
        navController = navHostController,
        startDestination = AppRoutes.ROUTE_SPLASH_SCREEN.route
    ) {
        composable(
            route = AppRoutes.ROUTE_SPLASH_SCREEN.route,
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition


        ) {
            SplashScreen {
                navHostController.navigate(AppRoutes.ROUTE_DASHBOARD.route)
            }
        }

        composable(
            route = AppRoutes.ROUTE_DASHBOARD.route,
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition

        ) {

            FaceAttendanceScreen(navController = navHostController)

        }

        composable(
            route = AppRoutes.ROUTE_CAMERA_SCREEN.route,
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition


        ) {

            val cameraPreviewViewModel: CameraPreviewViewModel = koinViewModel()

//
//            LaunchedEffect(Unit) {
//                cameraPreviewViewModel.resetCameraState()
//            }

            CameraPreviewScreen(
                viewModel = cameraPreviewViewModel,
                onNavigateToFaceRegistration = { uri ->

                    navHostController.navigate(
                        AppRoutes.navigateToFaceCapture(uri.toString())
                    ) {
                        popUpTo(AppRoutes.ROUTE_CAMERA_SCREEN.route) {
                            inclusive = true
                        }
                    }

                }, handleClose = {
                    navHostController.popBackStack()
                }
            )
        }

        composable(
            route = AppRoutes.ROUTE_FACE_REGISTRATION.route,
            arguments = listOf(navArgument("capturedUri") { type = NavType.StringType }),
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition
        ) { backStackEntry ->
            val capturedUriString = backStackEntry.arguments?.getString("capturedUri")
            val capturedUri = capturedUriString?.let { Uri.parse(Uri.decode(it)) }


            // Initialize the ViewModel with the captured URI
            LaunchedEffect(capturedUri) {
                capturedUri?.let { faceRegistrationViewModel.initializeWithUri(it) }
            }

            FaceRegistrationScreen(
                viewModel = faceRegistrationViewModel,
                onNavigateBack = {
                    navHostController.popBackStack()
                },
                onSubmissionSuccess = {
                    // Navigate to success screen
                    navHostController.navigate(AppRoutes.ROUTE_SUBMISSION_SUCCESS.route) {
                        popUpTo(AppRoutes.ROUTE_CAMERA_SCREEN.route) { inclusive = true }
                    }
                }
            )
        }


        composable(
            route = AppRoutes.ROUTE_SUBMISSION_SUCCESS.route,
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition
        ) {
            SubmissionSuccessScreen(
                onNavigateHome = {
                    navHostController.navigate(AppRoutes.ROUTE_DASHBOARD.route) {
                        popUpTo(0) // Clear entire back stack
                    }
                }
            )
        }

        composable(
            route = AppRoutes.ROUTE_FACE_IMAGE_PICKER.route,
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition
        ) {
            FaceImagePickerScreen(
                onImagePicked = { uri ->
                    navHostController.navigate(
                        AppRoutes.navigateToFaceCapture(uri.toString())
                    ) {
                        popUpTo(AppRoutes.ROUTE_FACE_IMAGE_PICKER.route) {
                            inclusive = true
                        }
                    }

                },
                onCancel = { navHostController.popBackStack() }
            )
        }

    }


}
