package com.aican.biometricattendance.di

import org.koin.dsl.module
import androidx.room.Room
import com.aican.biometricattendance.data.db.database.FaceEmbeddingDatabase
import com.aican.biometricattendance.data.db.repository.FaceEmbeddingRepository

val faceEmbeddingModule = module {
    single {
        Room.databaseBuilder(get(), FaceEmbeddingDatabase::class.java, "face_embedding.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<FaceEmbeddingDatabase>().faceEmbeddingDao() }
    single { FaceEmbeddingRepository(get()) }
}
