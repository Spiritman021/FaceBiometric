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
import com.aican.biometricattendance.utils.GlobalSharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FaceRegistrationViewModel(private val repository: FaceEmbeddingRepository) : ViewModel() {

    private val _capturedFaceUri = MutableStateFlow<Uri?>(null)
    val capturedFaceUri: StateFlow<Uri?> = _capturedFaceUri.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow("")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _submitError = MutableStateFlow<String?>(null)
    val submitError: StateFlow<String?> = _submitError.asStateFlow()

    private val _isSubmitted = MutableStateFlow(false)
    val isSubmitted: StateFlow<Boolean> = _isSubmitted.asStateFlow()

    private val _faceEmbedding = MutableStateFlow<FloatArray?>(null)
    val faceEmbedding: StateFlow<FloatArray?> = _faceEmbedding.asStateFlow()

    private val converter = Converters()

    fun initializeWithUri(uri: Uri) {
        _capturedFaceUri.value = uri
    }

    fun updateUserName(name: String) {
        _userName.value = name.trim()
        clearError()
    }

    fun updateUserEmail(email: String) {
        _userEmail.value = email.trim()
        clearError()
    }


    fun isEmailValid(): Boolean {
        val email = _userEmail.value
        // Only consider non-blank as valid if it matches pattern
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isFormValid(): Boolean {
        return _userName.value.isNotBlank() &&
                _userEmail.value.isNotBlank() &&
                isEmailValid()
    }

    private fun clearError() {
        _submitError.value = null
    }


    private val LOG_TAG = "FaceRegistrationVM"

    fun submitFaceData(context: Context) {
        if (!isFormValid()) {
            Log.w(
                LOG_TAG,
                "Form invalid: username='${_userName.value}', email='${_userEmail.value}'"
            )
            _submitError.value = "Please fill in all fields correctly"
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _submitError.value = null

            try {
                Log.i(LOG_TAG, "Submitting face data...")

                val uri = _capturedFaceUri.value
                Log.d(LOG_TAG, "Processing URI: $uri")


                // 2025-07-31 17:46:48.675 30656-30787 FaceEmbeddingGenerator  com.aican.biometricattendance        D  Model loaded successfully
                //2025-07-31 17:46:48.677 30656-30787 FaceProcessingUtils     com.aican.biometricattendance        D  Bitmap preprocessed: 160x160
                //2025-07-31 17:46:48.728 30656-30787 FaceEmbeddingGenerator  com.aican.biometricattendance        D  Embedding generated successfully, size: 512
                //2025-07-31 17:46:48.733 30656-30787 FaceEmbeddingGenerator  com.aican.biometricattendance        D  Model resources closed
                //2025-07-31 17:46:48.747 30656-30787 FaceRegistrationVM      com.aican.biometricattendance        D  Loaded 1 registered embeddings.
                //2025-07-31 17:46:48.747 30656-30787 FaceRegistrationVM      com.aican.biometricattendance        D  Comparing to vishal@aican.in: similarity = 1.0 (threshold: 0.7)
                // 2025-07-31 17:46:48.747 30656-30787 FaceRegistrationVM      com.aican.biometricattendance        I  Current best match: vishal@aican.in, score: 1.0
                //2025-07-31 17:46:48.747 30656-30787 FaceRegistrationVM      com.aican.biometricattendance        I  Final matched email: vishal@aican.in with score 1.0

                //2025-07-31 17:47:35.462 30656-30788 FaceEmbeddingGenerator  com.aican.biometricattendance        D  Model loaded successfully
                //2025-07-31 17:47:35.463 30656-30788 FaceProcessingUtils     com.aican.biometricattendance        D  Bitmap preprocessed: 160x160
                //2025-07-31 17:47:35.502 30656-30788 FaceEmbeddingGenerator  com.aican.biometricattendance        D  Embedding generated successfully, size: 512
                //2025-07-31 17:47:35.510 30656-30788 FaceEmbeddingGenerator  com.aican.biometricattendance        D  Model resources closed
                //2025-07-31 17:47:35.515 30656-30788 FaceRegistrationVM      com.aican.biometricattendance        D  Loaded 1 registered embeddings.
                //2025-07-31 17:47:35.516 30656-30788 FaceRegistrationVM      com.aican.biometricattendance        D  Comparing to vishal@aican.in: similarity = 0.55522007 (threshold: 0.7)
                //2025-07-31 17:47:35.516 30656-30788 FaceRegistrationVM      com.aican.biometricattendance        I  No matching email found.

                if (uri == null) {
                    Log.e(LOG_TAG, "Face image URI is null")
                    _submitError.value = "Face image not found"
                    return@launch
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    Log.e(LOG_TAG, "Unable to open face image stream for URI: $uri")
                    _submitError.value = "Unable to open face image"
                    return@launch
                }
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap == null) {
                    Log.e(LOG_TAG, "Failed to decode face image for URI: $uri")
                    _submitError.value = "Failed to decode face image"
                    return@launch
                }

                val embeddingGenerator = UnifiedFaceEmbeddingProcessor(context)

                val embeddingResult = embeddingGenerator.generateEmbedding(bitmap)
                embeddingGenerator.close()

                if (embeddingResult.success && embeddingResult.embedding != null) {
                    _faceEmbedding.value = embeddingResult.embedding
                    Log.i(
                        LOG_TAG, "Embedding generated (first 3 values): ${
                            embeddingResult.embedding.take(3).joinToString()
                        }"
                    )
                    // This is where you should save into Room DB:
                    saveEmbedding(
                        name = _userName.value,
                        email = _userEmail.value,
                        embedding = embeddingResult.embedding
                    )
                    // (Remove or comment out any SharedPreferences save calls here)
                } else {
                    Log.e(LOG_TAG, "Embedding generation failed: ${embeddingResult.error}")
                    _submitError.value = embeddingResult.error ?: "Embedding generation failed"
                }

            } catch (e: Exception) {
                Log.e(LOG_TAG, "Exception during submit: ${e.localizedMessage}", e)
                _submitError.value = "Exception: ${e.localizedMessage}"
            } finally {
                Log.i(LOG_TAG, "Submission completed.")
                _isSubmitting.value = false
            }
        }
    }

    fun saveEmbedding(name: String, email: String, embedding: FloatArray) {
        viewModelScope.launch {
            val embeddingBytes = converter.fromFloatArray(embedding)
            val entity = FaceEmbeddingEntity(
                name = name,
                email = email,
                embedding = embeddingBytes
            )
            repository.insert(entity)
            Log.d("FaceRegistrationVM", "Saved embedding for $email")
        }
    }


    suspend fun findMatchingEmail(embedding: FloatArray, threshold: Float = 0.7f): FaceData {
        var faceData = FaceData()
        val entities = repository.getAll()
        var bestMatch: String? = null
        var bestScore = -1f
        for (entity in entities) {
            val storedEmbedding = converter.toFloatArray(entity.embedding)
            if (storedEmbedding.size == embedding.size) {
                val similarity = FaceProcessingUtils.calculateSimilarity(storedEmbedding, embedding)
                Log.d("FaceRegistrationVM", "Comparing ${entity.email}: $similarity")
                if (similarity > threshold && similarity > bestScore) {
                    faceData = faceData.copy(name = entity.name, email = entity.email)
                    bestMatch = entity.email
                    bestScore = similarity
                    Log.d("FaceRegistrationVM", "New best match: $bestMatch, $bestScore")
                }
            } else {
                Log.w("FaceRegistrationVM", "Embedding size mismatch for ${entity.email}")
            }
        }
        if (bestMatch != null) {
            Log.i("FaceRegistrationVM", "Final matched email: $bestMatch with score $bestScore")
        } else {
            Log.i("FaceRegistrationVM", "No matching email found.")
        }
        return faceData
    }

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
        _userEmail.value = ""
        _isSubmitting.value = false
        _submitError.value = null
        _isSubmitted.value = false
    }
}
