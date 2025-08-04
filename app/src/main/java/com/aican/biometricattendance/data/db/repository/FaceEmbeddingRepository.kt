package com.aican.biometricattendance.data.db.repository

import com.aican.biometricattendance.data.db.dao.FaceEmbeddingDao
import com.aican.biometricattendance.data.db.entity.FaceEmbeddingEntity

class FaceEmbeddingRepository(private val dao: FaceEmbeddingDao) {
    suspend fun insert(faceEmbedding: FaceEmbeddingEntity) = dao.insert(faceEmbedding)
    suspend fun getAll(): List<FaceEmbeddingEntity> = dao.getAllEmbeddings()
    suspend fun findByEmail(email: String): FaceEmbeddingEntity? = dao.findByEmail(email)
}
