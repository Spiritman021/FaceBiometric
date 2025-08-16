package com.aican.biometricattendance


import android.app.Application
import com.aican.biometricattendance.di.faceEmbeddingModule
import com.aican.biometricattendance.di.loginModule
import com.aican.biometricattendance.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@MyApplication)
            modules(viewModelModule)
            modules(faceEmbeddingModule)
            modules(loginModule)
        }

    }
}