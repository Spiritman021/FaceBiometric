package com.aican.biometricattendance.navigation


import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aican.biometricattendance.data.local.TokenStore
import com.aican.biometricattendance.navigation.routes.AppRoutes
import com.aican.biometricattendance.presentation.screens.accounts.AccountScreen
import com.aican.biometricattendance.presentation.screens.accounts.AccountViewModel
import com.aican.biometricattendance.presentation.screens.attendance_dashboard.FaceAttendanceScreen
import com.aican.biometricattendance.presentation.screens.camera.CameraPreviewScreen
import com.aican.biometricattendance.presentation.screens.camera.CameraPreviewViewModel
import com.aican.biometricattendance.presentation.screens.face_registration.CheckEmployeeScreen
import com.aican.biometricattendance.presentation.screens.face_registration.FaceImagePickerScreen
import com.aican.biometricattendance.presentation.screens.face_registration.FaceRegistrationScreen
import com.aican.biometricattendance.presentation.screens.face_registration.FaceRegistrationSuccessScreen
import com.aican.biometricattendance.presentation.screens.face_registration.FaceRegistrationViewModel
import com.aican.biometricattendance.presentation.screens.login.LoginScreen
import com.aican.biometricattendance.presentation.screens.login.LoginViewModel
import com.aican.biometricattendance.presentation.screens.mark_attendance.AttendanceVerificationViewModel
import com.aican.biometricattendance.presentation.screens.mark_attendance.MarkAttendanceScreen
import com.aican.biometricattendance.presentation.screens.mark_attendance.MarkStatusScreen
import com.aican.biometricattendance.presentation.screens.registered_user.RegisteredUsersScreen
import com.aican.biometricattendance.presentation.screens.registered_user.RegisteredUsersViewModel
import com.aican.biometricattendance.presentation.screens.reports_screen.AttendanceReportScreen
import com.aican.biometricattendance.presentation.screens.reports_screen.AttendanceReportViewModel
import com.aican.biometricattendance.presentation.screens.splash.SplashScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun GlobalNavigationHost(
    navHostController: NavHostController,
    faceRegistrationViewModel: FaceRegistrationViewModel,
    tokenStore: TokenStore = koinInject(),
) {

    NavHost(
        navController = navHostController, startDestination = AppRoutes.ROUTE_SPLASH_SCREEN.route
    ) {

        composable(
            route = AppRoutes.ROUTE_LOGIN.route,
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition
        ) {
            val vm: LoginViewModel = koinViewModel()
            LoginScreen(navController = navHostController, viewModel = vm)
        }

        composable(
            route = AppRoutes.ROUTE_SPLASH_SCREEN.route,
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition


        ) {


            SplashScreen {
                // in SplashScreen onFinished:
                if (tokenStore.getToken().isNullOrEmpty()) {
                    navHostController.navigate(AppRoutes.ROUTE_LOGIN.route)
                } else {
                    navHostController.navigate(AppRoutes.ROUTE_DASHBOARD.route)
                }

//                navHostController.navigate(AppRoutes.ROUTE_DASHBOARD.route)
            }
        }

        composable(
            route = AppRoutes.ROUTE_ACCOUNT.route,
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition
        ) {
            val vm: AccountViewModel = koinViewModel()
            AccountScreen(navController = navHostController, viewModel = vm)
        }


        composable(
            route = AppRoutes.ROUTE_DASHBOARD.route,
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition

        ) {

            FaceAttendanceScreen(navController = navHostController) {
                tokenStore.clear()

                navHostController.navigate(AppRoutes.ROUTE_LOGIN.route)
            }

        }

        composable(
            route = AppRoutes.ROUTE_CAMERA_SCREEN.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition


        ) { backStackEntry ->

            val id = backStackEntry.arguments?.getString("id") ?: ""


            val cameraPreviewViewModel: CameraPreviewViewModel = koinViewModel()

//
//            LaunchedEffect(Unit) {
//                cameraPreviewViewModel.resetCameraState()
//            }

            CameraPreviewScreen(
                viewModel = cameraPreviewViewModel,
                id = id,
                onNavigateToFaceRegistration = { uri ->

                    navHostController.navigate(
                        AppRoutes.navigateToFaceCapture(uri.toString(), id)
                    ) {
                        popUpTo(AppRoutes.ROUTE_CAMERA_SCREEN.route) {
                            inclusive = true
                        }
                    }

                },
                handleClose = {
                    navHostController.popBackStack()
                })
        }

        composable(
            route = AppRoutes.ROUTE_MARK_ATTENDANCE_SCREEN.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition


        ) { backStackEntry ->
            val attendanceVerificationViewModel: AttendanceVerificationViewModel = koinViewModel()
            val id = backStackEntry.arguments?.getString("id") ?: ""



            MarkAttendanceScreen(
                id = id.toString(),
                viewModel = attendanceVerificationViewModel,
                navController = navHostController,
                handleClose = {
                    navHostController.popBackStack()
                },
                onNavigateToFaceRegistration = { uri ->
                    navHostController.navigate(
                        AppRoutes.navigateToFaceCapture(uri.toString(), id)
                    ) {
                        popUpTo(AppRoutes.ROUTE_CAMERA_SCREEN.route) {
                            inclusive = true
                        }
                    }

                },
            )
        }
        composable(
            route = "mark-status/{email}/{percentage}",
            arguments = listOf(
                navArgument("email") { type = NavType.StringType },
                navArgument("percentage") { type = NavType.StringType }),
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition
        ) { backStackEntry ->
            val attendanceVerificationViewModel: AttendanceVerificationViewModel = koinViewModel()

            val employeeId = backStackEntry.arguments?.getString("email") ?: ""
            val percentage = backStackEntry.arguments?.getString("percentage") ?: ""
            MarkStatusScreen(
                email = employeeId,
                matchPercent = percentage,
                employeeId = employeeId,
                viewModel = attendanceVerificationViewModel,
                onBack = {
                    // Pop everything up to (but not including) the Dashboard
                    navHostController.popBackStack(
                        AppRoutes.ROUTE_DASHBOARD.route,
                        /* inclusive = */ false
                    )
                })
        }


        composable(
            route = AppRoutes.ROUTE_CHECK_EMPLOYEE.route,
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition
        ) {
            CheckEmployeeScreen(
                faceRegistrationViewModel = faceRegistrationViewModel,
                navController = navHostController,
                proceedToRegister = { employeeId ->
                    navHostController.navigate(AppRoutes.navigateToCameraScreen(employeeId))
                })
        }


        composable(
            route = AppRoutes.ROUTE_FACE_REGISTRATION.route,
            arguments = listOf(
                navArgument("capturedUri") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType }),
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition
        ) { backStackEntry ->
            val capturedUriString = backStackEntry.arguments?.getString("capturedUri")
            val id = backStackEntry.arguments?.getString("id") ?: ""
            val capturedUri = capturedUriString?.let { Uri.parse(Uri.decode(it)) }


            // Initialize the ViewModel with the captured URI
            LaunchedEffect(capturedUri) {
                capturedUri?.let { faceRegistrationViewModel.initializeWithUri(it) }
            }

            FaceRegistrationScreen(viewModel = faceRegistrationViewModel, id, onNavigateBack = {
                navHostController.popBackStack()
            }, onSubmissionSuccess = { name, id ->
                navHostController.navigate(
                    "face_registration_success/${Uri.encode(name)}/${
                        Uri.encode(
                            id
                        )
                    }"
                ) {

                }
            })

        }


        composable(
            route = "face_registration_success/{name}/{id}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
            ),
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: ""
            val id = backStackEntry.arguments?.getString("id") ?: ""

            FaceRegistrationSuccessScreen(
                name = name,
                employeeId = id,
                onNavigateHome = {
                    navHostController.navigate(AppRoutes.ROUTE_DASHBOARD.route) {
                        popUpTo(0)
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
            FaceImagePickerScreen(onImagePicked = { uri ->
                navHostController.navigate(
                    AppRoutes.navigateToFaceCapture(uri.toString(), "")
                ) {
                    popUpTo(AppRoutes.ROUTE_FACE_IMAGE_PICKER.route) {
                        inclusive = true
                    }
                }

            }, onCancel = { navHostController.popBackStack() })
        }

        composable(
            route = AppRoutes.ROUTE_REGISTERED_USERS.route,
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition
        ) {

            val registeredUsersViewModel: RegisteredUsersViewModel = koinViewModel()
            RegisteredUsersScreen(viewModel = registeredUsersViewModel, onBack = {
                navHostController.popBackStack()
            })

        }
        composable(
            route = AppRoutes.ROUTE_ATTENDANCE_REPORT.route,
            enterTransition = slideUpDownEnterTransition,
            exitTransition = slideUpDownExitTransition,
            popEnterTransition = slideUpDownPopEnterTransition,
            popExitTransition = slideUpDownPopExitTransition
        ) {

            val attendanceReportViewModel: AttendanceReportViewModel = koinViewModel()
            AttendanceReportScreen(viewModel = attendanceReportViewModel, onBack = {
                navHostController.popBackStack()
            })

        }
    }


}
