package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChargingSessionEntity::class, TelemetrySampleEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VoltPulseDatabase : RoomDatabase() {
    abstract fun chargingDao(): ChargingDao

    companion object {
        @Volatile
        private var INSTANCE: VoltPulseDatabase? = null

        fun getInstance(context: Context): VoltPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoltPulseDatabase::class.java,
                    "voltpulse_telemetry.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
