package com.aican.biometricattendance.data.local


import android.content.Context
import androidx.core.content.edit

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) = prefs.edit { putString("jwt", token) }
    fun getToken(): String? = prefs.getString("jwt", null)

    fun saveUserId(id: String) = prefs.edit { putString("user_id", id) }
    fun getUserId(): String? = prefs.getString("user_id", null)

    // 🔽 NEW (optional but recommended)
    fun saveUserEmail(email: String?) = prefs.edit { putString("user_email", email) }
    fun getUserEmail(): String? = prefs.getString("user_email", null)

    fun saveUserName(name: String?) = prefs.edit { putString("user_name", name) }
    fun getUserName(): String? = prefs.getString("user_name", null)

    fun clear() = prefs.edit { clear() }
}
