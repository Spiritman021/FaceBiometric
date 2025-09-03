package com.aican.biometricattendance.presentation.screens.reports_screen

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aican.biometricattendance.data.db.entity.AttendanceEntity
import com.aican.biometricattendance.data.db.repository.AttendanceRepository
import com.aican.biometricattendance.data.network.repository.AttendanceSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min


class AttendanceReportViewModel(
    private val repository: AttendanceRepository,
    private val syncRepository: AttendanceSyncRepository // Add this dependency
) : ViewModel() {

    companion object {
        private const val TAG = "AttendanceReportVM"
    }

    private val _attendanceLogs = mutableStateListOf<AttendanceEntity>()
    val attendanceLogs: List<AttendanceEntity> = _attendanceLogs

    var isSyncing by mutableStateOf(false)
        private set
    var syncProgress by mutableIntStateOf(0)
        private set
    var syncMessage by mutableStateOf<String?>(null)
        private set
    var syncError by mutableStateOf<String?>(null)
        private set
    var unsyncedCount by mutableIntStateOf(0)
        private set

    init {
        fetchAllAttendanceLogs()
    }

    fun deleteLog(log: AttendanceEntity) {
        viewModelScope.launch {
            try {
                repository.delete(log)
                // Update the UI list efficiently without a full refresh
                _attendanceLogs.remove(log)
                // If the deleted log was unsynced, update the count
                if (!log.synced) {
                    fetchUnsyncedCount()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting log with id ${log.id}", e)
                // Optionally, you can set an error state to show a message to the user
                syncError = "Failed to delete record."
            }
        }
    }


    fun syncAttendance() {
        if (isSyncing) return

        viewModelScope.launch {
            isSyncing = true
            syncProgress = 0
            syncMessage = null
            syncError = null

            try {
                val result = syncRepository.syncAllAttendanceRecords { current, total, message ->
                    val progressPercent = if (total > 0) ((current.toFloat() / total) * 100).toInt() else 0
                    syncProgress = progressPercent.coerceIn(0, 100)
                    syncMessage = message
                }

                // Handle results
                when {
                    result.totalRecords == 0 -> {
                        syncMessage = "No records to sync"
                    }
                    result.successCount == result.totalRecords -> {
                        syncMessage = "All ${result.successCount} records synced successfully!"
                        syncError = null
                    }
                    result.successCount > 0 -> {
                        syncMessage = "Partially synced: ${result.successCount}/${result.totalRecords} records"
                        syncError = if (result.errors.isNotEmpty()) {
                            "Errors: ${result.errors.take(2).joinToString("; ")}"
                        } else null
                    }
                    else -> {
                        syncMessage = null
                        syncError = "Sync failed: ${result.errors.firstOrNull() ?: "Unknown error"}"
                    }
                }

                Log.d(TAG, "Sync completed - Success: ${result.successCount}, Failed: ${result.failureCount}")

            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                syncError = "Sync failed: ${e.message}"
                syncMessage = null
            } finally {
                isSyncing = false
                syncProgress = 100
                // Refresh the UI data
                fetchAllAttendanceLogs()
            }
        }
    }

    fun fetchUnsyncedCount() {
        viewModelScope.launch {
            try {
                unsyncedCount = repository.getUnsyncedCount()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching unsynced count", e)
            }
        }
    }

    fun fetchAllAttendanceLogs() {
        viewModelScope.launch {
            try {
                val logs = repository.getAllAttendanceLogs()
                _attendanceLogs.clear()
                _attendanceLogs.addAll(logs)
                fetchUnsyncedCount()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching attendance logs", e)
            }
        }
    }
}
