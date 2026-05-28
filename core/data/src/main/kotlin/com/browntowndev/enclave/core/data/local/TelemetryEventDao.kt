package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class TelemetryEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(event: TelemetryEventEntity)

    @Query("SELECT * FROM telemetry_events WHERE uploaded = 0 ORDER BY timestamp_ms ASC LIMIT :limit")
    abstract suspend fun getPendingUploads(limit: Int = 100): List<TelemetryEventEntity>

    @Query("UPDATE telemetry_events SET uploaded = 1 WHERE id IN (:ids)")
    abstract suspend fun markUploaded(ids: List<String>)

    @Query("DELETE FROM telemetry_events WHERE id IN (SELECT id FROM telemetry_events ORDER BY created_at ASC LIMIT :count)")
    abstract suspend fun evictOldest(count: Int)

    @Query("SELECT COUNT(*) FROM telemetry_events")
    abstract suspend fun getCount(): Int

    @Query("DELETE FROM telemetry_events WHERE uploaded = 1")
    abstract suspend fun deleteUploaded()
}
