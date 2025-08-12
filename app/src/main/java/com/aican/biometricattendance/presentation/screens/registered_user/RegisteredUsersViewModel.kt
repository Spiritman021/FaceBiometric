package com.aican.biometricattendance.presentation.screens.registered_user

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aican.biometricattendance.data.db.entity.FaceEmbeddingEntity
import com.aican.biometricattendance.data.db.repository.FaceEmbeddingRepository
import kotlinx.coroutines.launch

class RegisteredUsersViewModel(
    private val repository: FaceEmbeddingRepository
) : ViewModel() {

    private val _users = mutableStateListOf<FaceEmbeddingEntity>()
    val users: List<FaceEmbeddingEntity> = _users

    init {
        viewModelScope.launch {
            _users.addAll(repository.getAll())
        }
    }
}
