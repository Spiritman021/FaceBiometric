package com.aican.biometricattendance.data.network.api

import retrofit2.Response
import retrofit2.http.*


interface AttendanceApi {
    @Headers("Content-Type: application/json")
    @PUT("v1/users/hrms/sync/{employeeId}")
    suspend fun syncAttendance(
        @Path("employeeId") employeeId: String,
        @Query("allowPostman") allowPostman: Boolean = true,
        @Body body: Map<String, AttendanceDay>
    ): Response<AttendanceSyncResponse>
}

/** Now the request is just a Map<String, AttendanceDay> at the top level */
data class AttendanceDay(
    val login: String? = null,   // ISO-8601 UTC, e.g. "2025-06-11T03:30:00.000Z"
    val logout: String? = null   // nullable/omitted if not present
)

data class AttendanceSyncRequest(
    val attendance: Map<String, AttendanceDay>
)



data class AttendanceRecord(
    val timestamp: Long,
    val eventType: String, // "CHECK_IN" or "CHECK_OUT"
    val matchPercent: Float,
    val recordId: Int // local database ID for reference
)

data class AttendanceSyncResponse(
    val _id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val employeeId: String? = null,
    val rfid: String? = null,
    val role: String? = null,
    val attendance: Map<String, Any>? = null
) {
    fun isValidSyncResponse(): Boolean {
        return !_id.isNullOrEmpty() && (!name.isNullOrEmpty() || !email.isNullOrEmpty())
    }
}

data class SyncError(
    val recordId: Int,
    val error: String
)
