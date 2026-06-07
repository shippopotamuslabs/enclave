package com.browntowndev.enclave.core.data.local

/**
 * Room schema version 2 adds [LiveVoiceProviderEntity] and the
 * `default_models.live_voice_provider_id` column for Live Voice provider assignments.
 * Upgrades rely on [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration]
 * (explicit developer authorization for Live Voice — see `live-voice` ticket).
 */
object EnclaveDatabaseMigrations
