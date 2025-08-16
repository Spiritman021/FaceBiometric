package com.aican.biometricattendance.presentation.screens.login


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aican.biometricattendance.data.local.TokenStore
import com.aican.biometricattendance.data.network.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class LoginViewModel(
    private val repo: AuthRepository,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui

    fun onEmailChange(v: String) { _ui.value = _ui.value.copy(email = v) }
    fun onPasswordChange(v: String) { _ui.value = _ui.value.copy(password = v) }

    fun login() {
        val s = _ui.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _ui.value = s.copy(errorMessage = "Please enter email & password")
            return
        }
        _ui.value = s.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            runCatching {
                repo.login(s.email.trim(), s.password)
            }.onSuccess { res ->
                tokenStore.saveToken(res.token)
                tokenStore.saveUserId(res.userId)
                _ui.value = _ui.value.copy(isLoading = false, isSuccess = true)
            }.onFailure { e ->
                _ui.value = _ui.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Login failed"
                )
            }
        }
    }

    fun consumeError() { _ui.value = _ui.value.copy(errorMessage = null) }
}
