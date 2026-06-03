package com.aican.biometricattendance.data.network.repository

import android.util.Log
import com.aican.biometricattendance.data.db.entity.AttendanceEntity
import com.aican.biometricattendance.data.db.entity.AttendanceEventType
import com.aican.biometricattendance.data.db.repository.AttendanceRepository
import com.aican.biometricattendance.data.network.api.AttendanceApi
import com.aican.biometricattendance.data.network.api.AttendanceDay
import com.aican.biometricattendance.data.network.api.AttendanceSyncResponse
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AttendanceSyncRepository(
    private val attendanceApi: AttendanceApi,
    private val attendanceRepository: AttendanceRepository,
) {

    companion object {
        private const val TAG = "AttendanceSyncRepository"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    data class SyncResult(
        val totalRecords: Int,
        val successCount: Int,
        val failureCount: Int,
        val errors: List<String> = emptyList(),
    )

    suspend fun syncAllAttendanceRecords(
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
    ): SyncResult {
        return try {
            val unsyncedRecords = attendanceRepository.getUnsynced()
            if (unsyncedRecords.isEmpty()) {
                onProgress(0, 0, "No unsynced records found")
                return SyncResult(0, 0, 0)
            }

            // ## NEW LOGIC: Filter for complete days (check-in and check-out) only ##
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            // Group records by a combination of the employee and the specific day
            val groupedByEmployeeAndDay = unsyncedRecords.groupBy {
                it.employeeId to dateFormat.format(Date(it.timestamp))
            }

            // Filter these groups to keep only those that have both event types, then flatten the result
            val recordsToSync = groupedByEmployeeAndDay.values.filter { dailyRecords ->
                val hasCheckIn = dailyRecords.any { it.eventType == AttendanceEventType.CHECK_IN }
                val hasCheckOut = dailyRecords.any { it.eventType == AttendanceEventType.CHECK_OUT }
                hasCheckIn && hasCheckOut
            }.flatten()

            if (recordsToSync.isEmpty()) {
                onProgress(0, 0, "No complete attendance days to sync")
                return SyncResult(0, 0, 0)
            }
            // ## END OF NEW LOGIC ##

            // Continue the process using the filtered list 'recordsToSync'
            val groupedByEmployee = recordsToSync.groupBy { it.employeeId }
            var totalProcessed = 0
            var successCount = 0
            val errors = mutableListOf<String>()

            onProgress(0, recordsToSync.size, "Starting sync for complete days...")

            for ((employeeId, records) in groupedByEmployee) {
                try {
                    val result = syncEmployeeAttendance(employeeId, records)
                    successCount += result.successCount
                    errors.addAll(result.errors)
                    totalProcessed += records.size

                    onProgress(
                        totalProcessed,
                        recordsToSync.size,
                        "Synced $successCount/$totalProcessed records"
                    )

                    delay(500) // Small delay to prevent overwhelming the server

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync employee $employeeId", e)
                    errors.add("Employee $employeeId: ${e.message}")
                    totalProcessed += records.size
                }
            }

            SyncResult(
                totalRecords = recordsToSync.size,
                successCount = successCount,
                failureCount = recordsToSync.size - successCount,
                errors = errors
            )

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed completely", e)
            SyncResult(0, 0, 0, listOf("Complete sync failure: ${e.message}"))
        }
    }

    private suspend fun syncEmployeeAttendance(
        employeeId: String,
        records: List<AttendanceEntity>,
    ): SyncResult {

        val daysMap = mutableMapOf<String, AttendanceDay>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        for (record in records) {
            val dateKey = dateFormat.format(Date(record.timestamp))
            val isoTimestamp = isoFormat.format(Date(record.timestamp))

            val existingDay = daysMap[dateKey] ?: AttendanceDay()

            val updatedDay = when (record.eventType) {
                AttendanceEventType.CHECK_IN ->
                    existingDay.copy(login = isoTimestamp)

                AttendanceEventType.CHECK_OUT ->
                    existingDay.copy(logout = isoTimestamp)
            }

            daysMap[dateKey] = updatedDay
        }

        // Filter out any incomplete pairs that might have slipped through, as a safeguard
        val completeDaysMap = daysMap.filterValues { it.login != null && it.logout != null }

        if (completeDaysMap.isEmpty()) {
            return SyncResult(
                totalRecords = records.size,
                successCount = 0,
                failureCount = records.size,
                errors = listOf("No complete days found for employee $employeeId after final processing.")
            )
        }

        return executeWithRetry(employeeId, completeDaysMap) { response ->
            if (response.isValidSyncResponse()) {
                val syncedIds = records.map { it.id }
                attendanceRepository.markAsSynced(syncedIds)

                Log.d(TAG, "Successfully synced ${syncedIds.size} records for employee $employeeId")
                Log.d(TAG, "User data received: ${response.name ?: "Unknown"} (${response.email ?: "Unknown"})")

                SyncResult(
                    totalRecords = records.size,
                    successCount = syncedIds.size,
                    failureCount = 0,
                    errors = emptyList()
                )
            } else {
                throw Exception("Empty or invalid response from server")
            }
        }
    }

    private suspend fun executeWithRetry(
        employeeId: String,
        requestBody: Map<String, AttendanceDay>,
        onSuccess: suspend (response: AttendanceSyncResponse) -> SyncResult,
    ): SyncResult {
        var lastException: Exception? = null

        for (attempt in 0 until MAX_RETRIES) {
            try {
                Log.d(TAG, "Syncing employee $employeeId, attempt ${attempt + 1}")

                val response = attendanceApi.syncAttendance(
                    employeeId = employeeId,
                    allowPostman = true,
                    body = requestBody
                )

                when {
                    response.isSuccessful -> {
                        val body = response.body()
                        if (body != null) {
                            return onSuccess(body)
                        } else {
                            throw Exception("Response body is null")
                        }
                    }
                    response.code() == 404 -> throw Exception("Employee not found on server")
                    response.code() == 401 -> throw Exception("Authentication failed")
                    response.code() >= 500 -> throw Exception("Server error: ${response.code()}")
                    else -> throw Exception("HTTP ${response.code()}: ${response.message()}")
                }

            } catch (e: HttpException) {
                Log.w(TAG, "HTTP exception on attempt ${attempt + 1}: ${e.message}")
                lastException = e
                if (e.code() == 401 || e.code() == 404) break
            } catch (e: IOException) {
                Log.w(TAG, "Network exception on attempt ${attempt + 1}: ${e.message}")
                lastException = e
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected exception on attempt ${attempt + 1}: ${e.message}")
                lastException = e
                break
            }

            if (attempt < MAX_RETRIES - 1) {
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }

        val errorMessage = lastException?.message ?: "Unknown error after $MAX_RETRIES attempts"
        Log.e(TAG, "Failed to sync employee $employeeId after $MAX_RETRIES attempts: $errorMessage")

        return SyncResult(
            totalRecords = requestBody.size,
            successCount = 0,
            failureCount = requestBody.size,
            errors = listOf(errorMessage)
        )
    }
}