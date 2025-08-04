package com.aican.biometricattendance.data.db.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aican.biometricattendance.data.db.converters.Converters
import com.aican.biometricattendance.data.db.dao.FaceEmbeddingDao
import com.aican.biometricattendance.data.db.entity.FaceEmbeddingEntity

@Database(
    entities = [FaceEmbeddingEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FaceEmbeddingDatabase : RoomDatabase() {
    abstract fun faceEmbeddingDao(): FaceEmbeddingDao
}
