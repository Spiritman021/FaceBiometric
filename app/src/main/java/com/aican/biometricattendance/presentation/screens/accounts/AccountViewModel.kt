package com.aican.biometricattendance.presentation.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aican.biometricattendance.data.local.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AccountUiState(
    val name: String? = null,
    val email: String? = null,
    val userId: String? = null,
    val token: String? = null
)

class AccountViewModel(
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _ui = MutableStateFlow(AccountUiState())
    val ui: StateFlow<AccountUiState> = _ui

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.value = AccountUiState(
                name = tokenStore.getUserName(),
                email = tokenStore.getUserEmail(),
                userId = tokenStore.getUserId(),
                token = tokenStore.getToken()
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenStore.clear()
        }
    }
}
