package com.aican.biometricattendance.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aican.biometricattendance.data.db.entity.FaceEmbeddingEntity

@Dao
interface FaceEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(faceEmbedding: FaceEmbeddingEntity)

    @Query("SELECT * FROM face_embeddings")
    suspend fun getAllEmbeddings(): List<FaceEmbeddingEntity>

    @Query("SELECT * FROM face_embeddings WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): FaceEmbeddingEntity?
}
