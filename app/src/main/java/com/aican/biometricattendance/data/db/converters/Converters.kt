package com.aican.biometricattendance.data.db.converters

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {
    @TypeConverter
    fun fromFloatArray(value: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(value.size * 4)
        buffer.order(ByteOrder.nativeOrder())
        buffer.asFloatBuffer().put(value)
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.nativeOrder())
        val floatBuffer = buffer.asFloatBuffer()
        val result = FloatArray(floatBuffer.limit())
        floatBuffer.get(result)
        return result
    }
}
