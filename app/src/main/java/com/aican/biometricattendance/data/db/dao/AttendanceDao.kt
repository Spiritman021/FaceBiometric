package com.aican.biometricattendance.data.db.dao


import androidx.room.Dao
import androidx.room.Delete
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

    @Query("UPDATE attendance_logs SET synced = 1 WHERE id IN (:recordIds)")
    suspend fun markAsSynced(recordIds: List<Int>)

    @Query("UPDATE attendance_logs SET synced = 1 WHERE id = :recordId")
    suspend fun markAsSynced(recordId: Int)

    @Delete
    suspend fun delete(log: AttendanceEntity)
}
