package com.aican.biometricattendance.di

import com.aican.biometricattendance.presentation.screens.camera.CameraPreviewViewModel
import com.aican.biometricattendance.presentation.screens.face_registration.FaceRegistrationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {

    viewModel {
        CameraPreviewViewModel()
    }
    viewModel {
        FaceRegistrationViewModel(get())

    }
}