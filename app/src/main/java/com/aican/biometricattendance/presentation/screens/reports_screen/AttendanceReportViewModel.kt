package com.aican.biometricattendance.presentation.screens.reports_screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aican.biometricattendance.data.db.entity.AttendanceEntity
import com.aican.biometricattendance.data.db.repository.AttendanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min


class AttendanceReportViewModel(
    private val repository: AttendanceRepository,
) : ViewModel() {

    private val _attendanceLogs = mutableStateListOf<AttendanceEntity>()
    val attendanceLogs: List<AttendanceEntity> = _attendanceLogs

    var isSyncing by mutableStateOf(false)
        private set
    var syncProgress by mutableIntStateOf(0)        // 0..100
        private set
    var syncMessage by mutableStateOf<String?>(null)
        private set
    var syncError by mutableStateOf<String?>(null)
        private set

    fun syncAttendance(batchSize: Int = 100) {
        if (isSyncing) return

        viewModelScope.launch {
            isSyncing = true
            syncProgress = 0
            syncMessage = null
            syncError = null

            // 1) Load all unsynced (no LIMIT)
            val unsynced = withContext(Dispatchers.IO) { repository.getUnsynced() }
            val total = unsynced.size
            if (total == 0) {
                syncMessage = "All records are already synced."
                isSyncing = false
                fetchAllAttendanceLogs()
                return@launch
            }

            val safeBatch = max(1, batchSize)
            val totalBatches = ceil(total / safeBatch.toDouble()).toInt()
            var processed = 0
            var failedTotal = 0

            // 2) Iterate batches
            for (batchIndex in 0 until totalBatches) {
                val from = batchIndex * safeBatch
                val to = min(from + safeBatch, total)
                val batch = unsynced.subList(from, to)

                // Optional: small delay to make progress visible / avoid UI jank
                // delay(100)

                // 3) Upload this batch
                val result = withContext(Dispatchers.IO) {
                    // If you don't have a backend yet, you can simulate:
                    delay(300)
                    // SyncResult(syncedIds = batch.map { it.id }, failed = emptyMap())
                }


                processed += batch.size

                // 5) Update progress & status
                val pct = ((processed / total.toFloat()) * 100).toInt().coerceIn(0, 100)
                syncProgress = pct
                syncMessage = buildString {
                    append("Syncing ")
                    append(processed)
                    append("/")
                    append(total)
                    append(" (")
                    append(pct)
                    append("%)")

                }
            }

            // 6) Done
            isSyncing = false
            if (failedTotal == 0) {
                syncMessage = "Sync completed successfully."
            } else {
                syncError = "Sync completed with $failedTotal failure(s)."
            }

            // 7) Refresh UI lists and counts
            fetchAllAttendanceLogs()
        }
    }

    var unsyncedCount by mutableIntStateOf(0)
        private set

    fun fetchUnsyncedCount() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.getUnsyncedCount() }
                .onSuccess { count ->
                    withContext(Dispatchers.Main) { unsyncedCount = count }
                }
        }
    }


    fun fetchAllAttendanceLogs() {
        viewModelScope.launch {
            val logs = repository.getAllAttendanceLogs()
            _attendanceLogs.clear()
            _attendanceLogs.addAll(logs)
            fetchUnsyncedCount()
        }
    }
}
