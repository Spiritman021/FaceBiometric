package com.aican.biometricattendance.presentation.screens.face_registration

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aican.biometricattendance.data.db.converters.Converters
import com.aican.biometricattendance.data.db.entity.FaceData
import com.aican.biometricattendance.data.db.entity.FaceEmbeddingEntity
import com.aican.biometricattendance.data.db.repository.FaceEmbeddingRepository
import com.aican.biometricattendance.ml.facenet.FaceProcessingUtils
import com.aican.biometricattendance.ml.facenet.UnifiedFaceEmbeddingProcessor
import com.aican.biometricattendance.presentation.screens.camera.components.EmbeddingDebugger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FaceRegistrationViewModel(private val repository: FaceEmbeddingRepository) : ViewModel() {

    private val _capturedFaceUri = MutableStateFlow<Uri?>(null)
    val capturedFaceUri: StateFlow<Uri?> = _capturedFaceUri.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userId = MutableStateFlow("")
    val userId: StateFlow<String> = _userId.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _submitError = MutableStateFlow<String?>(null)
    val submitError: StateFlow<String?> = _submitError.asStateFlow()

    private val _isSubmitted = MutableStateFlow(false)
    val isSubmitted: StateFlow<Boolean> = _isSubmitted.asStateFlow()

    private val _faceEmbedding = MutableStateFlow<FloatArray?>(null)
    val faceEmbedding: StateFlow<FloatArray?> = _faceEmbedding.asStateFlow()

    fun updateFaceEmbedding(embedding: FloatArray) {
        _faceEmbedding.value = embedding
    }

    private val converter = Converters()

    fun initializeWithUri(uri: Uri) {
        _capturedFaceUri.value = uri
    }

    fun updateUserName(name: String) {
        _userName.value = name.trim()
        clearError()
    }

    fun updateUserId(email: String) {
        _userId.value = email.trim()
        clearError()
    }


    fun isFormValid(): Boolean {
        return _userName.value.isNotBlank() &&
                _userId.value.isNotBlank()
    }

    private fun clearError() {
        _submitError.value = null
    }


    private val LOG_TAG = "FaceRegistrationVM"

    fun submitFaceData(context: Context) {
        if (!isFormValid()) {
            _submitError.value = "Please fill in all fields correctly"
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _submitError.value = null

            try {
                val uri = _capturedFaceUri.value ?: throw Exception("Face image not found")
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Unable to open face image")

                val bitmap = BitmapFactory.decodeStream(inputStream)
                    ?: throw Exception("Failed to decode face image")
                EmbeddingDebugger.logImageProcessing("REGISTRATION", bitmap, "Pre-cropped from camera")

                inputStream.close()

                val embeddingGenerator = UnifiedFaceEmbeddingProcessor(context)
                val embeddingResult = embeddingGenerator.generateEmbedding(bitmap)
                EmbeddingDebugger.logEmbeddingGeneration("REGISTRATION", embeddingResult.embedding, embeddingResult.success, embeddingResult.error)

                embeddingGenerator.close()

                if (embeddingResult.success && embeddingResult.embedding != null) {
                    _faceEmbedding.value = embeddingResult.embedding

                    saveEmbedding(
                        name = _userName.value,
                        email = _userId.value,
                        embedding = embeddingResult.embedding
                    )

                    _isSubmitted.value = true // <-- Trigger navigation
                } else {
                    throw Exception(embeddingResult.error ?: "Embedding generation failed")
                }

            } catch (e: Exception) {
                _submitError.value = e.localizedMessage
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun saveEmbedding(name: String, email: String, embedding: FloatArray) {
        viewModelScope.launch {
            val embeddingBytes = converter.fromFloatArray(embedding)
            val entity = FaceEmbeddingEntity(
                name = name,
                employeeId = email,
                embedding = embeddingBytes
            )
            repository.insert(entity)
            Log.d("FaceRegistrationVM", "Saved embedding for $email")
        }
    }


    suspend fun findMatchingEmail(embedding: FloatArray, threshold: Float = 0.6f): FaceData {
        var faceData = FaceData()
        val entities = repository.getAll()
        var bestMatch: String? = null
        var bestScore = -1f
        for (entity in entities) {
            val storedEmbedding = converter.toFloatArray(entity.embedding)
            if (storedEmbedding.size == embedding.size) {
                val similarity = FaceProcessingUtils.calculateSimilarity(storedEmbedding, embedding)
                Log.d("FaceRegistrationVM", "Comparing ${entity.employeeId}: $similarity")
                if (similarity > threshold && similarity > bestScore) {
                    faceData = faceData.copy(name = entity.name, employeeId = entity.employeeId)
                    bestMatch = entity.employeeId
                    bestScore = similarity
                    Log.d("FaceRegistrationVM", "New best match: $bestMatch, $bestScore")
                }
            } else {
                Log.w("FaceRegistrationVM", "Embedding size mismatch for ${entity.employeeId}")
            }
        }
        if (bestMatch != null) {
            Log.i("FaceRegistrationVM", "Final matched email: $bestMatch with score $bestScore")
        } else {
            Log.i("FaceRegistrationVM", "No matching email found.")
        }
        return faceData
    }

    suspend fun findByEmployeeId(employeeId: String) = repository.findByEmployeeId(employeeId)

    private suspend fun submitToServer(
        faceUri: Uri?,
        name: String,
        email: String,
        context: Context
    ): Boolean {
        kotlinx.coroutines.delay(2000)
        return (0..10).random() > 2
    }

    fun reset() {
        _capturedFaceUri.value = null
        _userName.value = ""
        _userId.value = ""
        _isSubmitting.value = false
        _submitError.value = null
        _isSubmitted.value = false
    }
}
