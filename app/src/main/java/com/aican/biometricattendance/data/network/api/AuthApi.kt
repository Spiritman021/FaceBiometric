package com.aican.biometricattendance.data.network.api

// data/network/AuthApi.kt

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface AuthApi {
    @Headers("Content-Type: application/json")
    @POST("v1/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse
}

data class LoginRequest(
    val email: String,
    val password: String
)

// adjust to your backend response
data class LoginResponse(
    val jwtToken: String,
    val user: UserDto
)

data class UserDto(
    val _id: String,
    val name: String? = null,
    val email: String? = null
)
