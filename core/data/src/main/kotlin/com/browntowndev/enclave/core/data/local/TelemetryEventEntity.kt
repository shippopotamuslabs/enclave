package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telemetry_events",
    indices = [
        Index(value = ["uploaded"]),
        Index(value = ["created_at"]),
    ],
)
data class TelemetryEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    @ColumnInfo(name = "severity") val severity: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "uploaded") val uploaded: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
