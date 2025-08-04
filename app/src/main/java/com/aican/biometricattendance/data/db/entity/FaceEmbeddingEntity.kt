package com.aican.biometricattendance.data.db.entity

import androidx.room.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Entity(tableName = "face_embeddings")
data class FaceEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val embedding: ByteArray
)

data class FaceData(
    val name: String = "",
    val email: String = "",
)

