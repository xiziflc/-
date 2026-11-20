package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "charging_sessions")
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val startLevelPct: Int,
    val endLevelPct: Int,
    val plugTypeLabel: String,
    val protocolLabel: String = "",
    val peakPowerWatts: Float,
    val avgPowerWatts: Float,
    val peakTempC: Float,
    val avgTempC: Float,
    val mahAdded: Float,
    val sampleCount: Int,
    val isCompleted: Boolean = false
) {
    val durationMs: Long
        get() = (endTimeMs - startTimeMs).coerceAtLeast(0L)

    val levelDeltaPct: Int
        get() = (endLevelPct - startLevelPct).coerceAtLeast(0)

    val estimatedFullCapacityMah: Float?
        get() = if (levelDeltaPct >= 15 && mahAdded >= 200f) {
            (mahAdded / (levelDeltaPct / 100f)).takeIf { it in 1000f..15000f }
        } else {
            null
        }
}

@Entity(
    tableName = "telemetry_samples",
    foreignKeys = [
        ForeignKey(
            entity = ChargingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val timestampMs: Long,
    val levelPct: Int,
    val powerWatts: Float,
    val currentMa: Float,
    val voltageV: Float,
    val temperatureC: Float
)
