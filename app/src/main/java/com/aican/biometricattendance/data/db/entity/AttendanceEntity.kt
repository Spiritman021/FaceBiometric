package com.aican.biometricattendance.data.db.entity


import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_logs",
    foreignKeys = [
        ForeignKey(
            entity = FaceEmbeddingEntity::class,
            parentColumns = ["employeeId"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE // or SET_NULL or RESTRICT
        )
    ],
    indices = [Index(value = ["employeeId"])]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeId: String,
    val timestamp: Long,
    val eventType: AttendanceEventType,
    val matchPercent: Float,
    val synced: Boolean = false
)

enum class AttendanceEventType {
    CHECK_IN,
    CHECK_OUT
}
