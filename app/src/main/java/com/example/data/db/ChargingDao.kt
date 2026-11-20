package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingDao {

    @Query("SELECT * FROM charging_sessions ORDER BY startTimeMs DESC")
    fun observeAllSessions(): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE isCompleted = 0 ORDER BY startTimeMs DESC LIMIT 1")
    fun observeActiveSession(): Flow<ChargingSessionEntity?>

    @Query("SELECT * FROM charging_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSessionById(sessionId: Long): Flow<ChargingSessionEntity?>

    @Query("SELECT * FROM telemetry_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeSamplesForSession(sessionId: Long): Flow<List<TelemetrySampleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChargingSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChargingSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: TelemetrySampleEntity)

    @Query("DELETE FROM charging_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM charging_sessions")
    suspend fun clearAllSessions()
}
