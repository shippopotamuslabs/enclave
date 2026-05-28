package com.browntowndev.enclave.core.data.local

/**
 * Room schema version 2 adds [MessageInferenceTelemetryEntity].
 * Upgrades rely on [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration]
 * (explicit developer authorization for telemetry storage — see FEAT-inference-telemetry-stats).
 */
object EnclaveDatabaseMigrations
