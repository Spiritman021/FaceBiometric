package com.aican.biometricattendance.presentation.screens.reports_screen

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aican.biometricattendance.data.db.entity.AttendanceEntity
import com.aican.biometricattendance.data.db.repository.AttendanceRepository
import kotlinx.coroutines.launch

class AttendanceReportViewModel(
    private val repository: AttendanceRepository
) : ViewModel() {

    private val _attendanceLogs = mutableStateListOf<AttendanceEntity>()
    val attendanceLogs: List<AttendanceEntity> = _attendanceLogs

    fun fetchAllAttendanceLogs() {
        viewModelScope.launch {
            val logs = repository.getAllAttendanceLogs()
            _attendanceLogs.clear()
            _attendanceLogs.addAll(logs)
        }
    }
}
