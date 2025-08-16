package com.aican.biometricattendance.di

import com.aican.biometricattendance.presentation.screens.accounts.AccountViewModel
import com.aican.biometricattendance.presentation.screens.camera.CameraPreviewViewModel
import com.aican.biometricattendance.presentation.screens.face_registration.FaceRegistrationViewModel
import com.aican.biometricattendance.presentation.screens.mark_attendance.AttendanceVerificationViewModel
import com.aican.biometricattendance.presentation.screens.registered_user.RegisteredUsersViewModel
import com.aican.biometricattendance.presentation.screens.reports_screen.AttendanceReportViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {

    viewModel {
        CameraPreviewViewModel()
    }
    viewModel {
        FaceRegistrationViewModel(get())

    }

    viewModel {
        RegisteredUsersViewModel(get())

    }
    viewModel {
        AttendanceReportViewModel(get())

    }
    viewModel {
        AttendanceVerificationViewModel(get(), get())

    }

    viewModel {
        AccountViewModel(get())
    }
}