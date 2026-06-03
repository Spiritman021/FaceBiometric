package com.aican.biometricattendance.data.db.repository

import com.aican.biometricattendance.data.db.dao.FaceEmbeddingDao
import com.aican.biometricattendance.data.db.entity.FaceEmbeddingEntity

class FaceEmbeddingRepository(private val dao: FaceEmbeddingDao) {
    suspend fun insert(faceEmbedding: FaceEmbeddingEntity) = dao.insert(faceEmbedding)
    suspend fun getAll(): List<FaceEmbeddingEntity> = dao.getAllEmbeddings()
    suspend fun findByEmployeeId(employeeId: String): FaceEmbeddingEntity? = dao.findByEmployeeId(employeeId)

    suspend fun delete(faceEmbedding: FaceEmbeddingEntity) = dao.delete(faceEmbedding)
    suspend fun deleteByEmployeeId(employeeId: String) = dao.deleteByEmployeeId(employeeId)
    suspend fun deleteAll() = dao.deleteAll()
}
