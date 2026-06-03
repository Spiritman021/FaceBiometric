package com.aican.biometricattendance.data.network.repository

import com.aican.biometricattendance.data.network.api.AuthApi
import com.aican.biometricattendance.data.network.api.LoginRequest


interface AuthRepository {
    suspend fun login(email: String, password: String): AuthResult
}

data class AuthResult(
    val token: String,
    val userId: String,
)

class AuthRepositoryImpl(
    private val api: AuthApi,
) : AuthRepository {
    override suspend fun login(email: String, password: String): AuthResult {
        val res = api.login(LoginRequest(email, password))
        return AuthResult(token = res.jwtToken, userId = res.user._id)
    }
}
