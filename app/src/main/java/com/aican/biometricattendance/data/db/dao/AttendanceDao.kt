package com.aican.biometricattendance.data.db.dao


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aican.biometricattendance.data.db.entity.AttendanceEntity

@Dao
interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attendance: AttendanceEntity)

    @Query("SELECT * FROM attendance_logs WHERE employeeId = :employeeId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEvent(employeeId: String): AttendanceEntity?

    @Query("SELECT * FROM attendance_logs WHERE employeeId = :employeeId AND date(timestamp / 1000, 'unixepoch') = date('now')")
    suspend fun getTodayLogs(employeeId: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance_logs ORDER BY timestamp DESC")
    suspend fun getAllAttendanceLogs(): List<AttendanceEntity>

    @Query("SELECT * FROM attendance_logs WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<AttendanceEntity>

    @Query("SELECT COUNT(*) FROM attendance_logs WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int

}
