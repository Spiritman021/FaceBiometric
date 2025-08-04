package com.aican.biometricattendance.data.models.facenet


data class UserEmbedding(
    val userId: String,
    val name: String,
    val embedding: FloatArray,
    val confidence: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserEmbedding

        if (userId != other.userId) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}


data class FaceProcessingResult(
    val success: Boolean,
    val embedding: FloatArray? = null,
    val error: String? = null,
    val confidence: Float = 0.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceProcessingResult

        if (success != other.success) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (error != other.error) return false
        if (confidence != other.confidence) return false

        return true
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + confidence.hashCode()
        return result
    }
}