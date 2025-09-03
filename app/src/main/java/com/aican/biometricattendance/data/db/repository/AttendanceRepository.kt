package com.aican.biometricattendance.data.db.repository


import com.aican.biometricattendance.data.db.dao.AttendanceDao
import com.aican.biometricattendance.data.db.entity.AttendanceEntity

class AttendanceRepository(private val dao: AttendanceDao) {

    suspend fun insert(attendance: AttendanceEntity) = dao.insert(attendance)

    suspend fun getLastEvent(employeeId: String): AttendanceEntity? = dao.getLastEvent(employeeId)

    suspend fun getTodayLogs(employeeId: String): List<AttendanceEntity> =
        dao.getTodayLogs(employeeId)

    suspend fun getAllAttendanceLogs(): List<AttendanceEntity> = dao.getAllAttendanceLogs()

    suspend fun getUnsynced(): List<AttendanceEntity> = dao.getUnsynced()

    suspend fun getUnsyncedCount(): Int = dao.getUnsyncedCount()

    suspend fun markAsSynced(recordIds: List<Int>) = dao.markAsSynced(recordIds)

    suspend fun markAsSynced(recordId: Int) = dao.markAsSynced(recordId)

    suspend fun delete(log: AttendanceEntity) = dao.delete(log)

}
