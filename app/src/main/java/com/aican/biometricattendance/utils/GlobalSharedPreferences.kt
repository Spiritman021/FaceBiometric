package com.aican.biometricattendance.utils

import android.content.Context

object GlobalSharedPreferences {


    fun saveEmbeddingToPrefs(context: Context, embedding: FloatArray, userEmail: String) {
        val prefs = context.getSharedPreferences("face_data", Context.MODE_PRIVATE)
        val embeddingStr = embedding.joinToString(",") // Serialize float array
        prefs.edit().putString(userEmail, embeddingStr).apply()
    }

    fun loadAllEmbeddings(context: Context): Map<String, FloatArray> {
        val prefs = context.getSharedPreferences("face_data", Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->
            if (value is String) {
                val floatArray = value.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
                key to floatArray
            } else null
        }.toMap()
    }

}