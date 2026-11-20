package com.example.data

import com.example.data.db.ChargingDao
import com.example.data.db.ChargingSessionEntity
import com.example.data.db.TelemetrySampleEntity
import kotlinx.coroutines.flow.Flow

class ChargingRepository(private val dao: ChargingDao) {

    val allSessions: Flow<List<ChargingSessionEntity>> = dao.observeAllSessions()

    val activeSession: Flow<ChargingSessionEntity?> = dao.observeActiveSession()

    fun observeSession(sessionId: Long): Flow<ChargingSessionEntity?> =
        dao.observeSessionById(sessionId)

    fun observeSamples(sessionId: Long): Flow<List<TelemetrySampleEntity>> =
        dao.observeSamplesForSession(sessionId)

    suspend fun createSession(session: ChargingSessionEntity): Long =
        dao.insertSession(session)

    suspend fun updateSession(session: ChargingSessionEntity) =
        dao.updateSession(session)

    suspend fun addSample(sample: TelemetrySampleEntity) =
        dao.insertSample(sample)

    suspend fun deleteSession(sessionId: Long) =
        dao.deleteSessionById(sessionId)

    suspend fun clearAllSessions() =
        dao.clearAllSessions()
}
