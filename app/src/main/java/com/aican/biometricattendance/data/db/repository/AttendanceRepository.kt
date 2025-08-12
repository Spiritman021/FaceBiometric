package com.aican.biometricattendance.data.db.repository


import com.aican.biometricattendance.data.db.dao.AttendanceDao
import com.aican.biometricattendance.data.db.entity.AttendanceEntity

class AttendanceRepository(private val dao: AttendanceDao) {

    suspend fun insert(attendance: AttendanceEntity) = dao.insert(attendance)

    suspend fun getLastEvent(employeeId: String): AttendanceEntity? = dao.getLastEvent(employeeId)

    suspend fun getTodayLogs(employeeId: String): List<AttendanceEntity> = dao.getTodayLogs(employeeId)

    suspend fun getAllAttendanceLogs(): List<AttendanceEntity> = dao.getAllAttendanceLogs()

}
