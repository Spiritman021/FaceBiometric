package com.aican.biometricattendance.data.db.entity

import androidx.room.*

@Entity(tableName = "face_embeddings")
data class FaceEmbeddingEntity(
    @PrimaryKey val employeeId: String,
    val name: String,
    val embedding: ByteArray
)


data class FaceData(
    val name: String = "",
    val employeeId: String = "",
)

