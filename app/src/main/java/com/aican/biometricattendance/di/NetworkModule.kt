package com.aican.biometricattendance.di

import com.aican.biometricattendance.data.local.TokenStore
import com.aican.biometricattendance.data.network.api.AttendanceApi
import com.aican.biometricattendance.data.network.api.AuthApi
import com.aican.biometricattendance.data.network.interceptor.AuthInterceptor
import com.aican.biometricattendance.data.network.repository.AttendanceSyncRepository
import com.aican.biometricattendance.data.network.repository.AuthRepository
import com.aican.biometricattendance.data.network.repository.AuthRepositoryImpl
import com.aican.biometricattendance.presentation.screens.login.LoginViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val BASE_URL =
    "http://ec2-15-207-134-83.ap-south-1.compute.amazonaws.com/"

val networkModule = module {
    single {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(get()))
            .addInterceptor(logger)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    single<AuthApi> {
        get<Retrofit>().create(AuthApi::class.java)
    }

    single { TokenStore(androidContext()) }
    single<AuthRepository> { AuthRepositoryImpl(get()) }
    viewModel { LoginViewModel(get(), get()) }

    single<AttendanceApi> {
        get<Retrofit>().create(AttendanceApi::class.java)
    }

    single<AttendanceSyncRepository> {
        AttendanceSyncRepository(
            attendanceApi = get(),
            attendanceRepository = get()
        )
    }
}