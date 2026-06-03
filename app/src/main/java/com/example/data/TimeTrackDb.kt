package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "time_logs")
data class TimeLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // e.g., "工作", "学习", "运动", "休息", "娱乐", "日常"
    val description: String,
    val startTime: Long, // Epoch millis
    val durationMinutes: Long,
    val endTime: Long = startTime + durationMinutes * 60 * 1000, // Epoch millis
    val updatedTime: Long = System.currentTimeMillis(), // Epoch millis
    val belongDate: String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(startTime)),
    val timerType: Int = 0
)

@Dao
interface TimeLogDao {
    @Query("SELECT * FROM time_logs ORDER BY startTime DESC")
    fun getAllLogs(): Flow<List<TimeLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TimeLog)

    @Update
    suspend fun updateLog(log: TimeLog)

    @Delete
    suspend fun deleteLog(log: TimeLog)

    @Query("DELETE FROM time_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)

    @Query("DELETE FROM time_logs")
    suspend fun deleteAllLogs()

    @Query("SELECT * FROM time_logs WHERE startTime >= :startOfDay AND startTime <= :endOfDay ORDER BY startTime DESC")
    fun getLogsForDay(startOfDay: Long, endOfDay: Long): Flow<List<TimeLog>>
}

@Database(entities = [TimeLog::class], version = 3, exportSchema = false)
abstract class TimeTrackDatabase : RoomDatabase() {
    abstract fun timeLogDao(): TimeLogDao
}

class TimeTrackRepository(private val dao: TimeLogDao) {
    val allLogs: Flow<List<TimeLog>> = dao.getAllLogs()

    fun getLogsForDay(startOfDay: Long, endOfDay: Long): Flow<List<TimeLog>> =
        dao.getLogsForDay(startOfDay, endOfDay)

    suspend fun insertLog(log: TimeLog) = dao.insertLog(log)
    suspend fun updateLog(log: TimeLog) = dao.updateLog(log)
    suspend fun deleteLog(log: TimeLog) = dao.deleteLog(log)
    suspend fun deleteLogById(id: Int) = dao.deleteLogById(id)
    suspend fun deleteAllLogs() = dao.deleteAllLogs()
}
