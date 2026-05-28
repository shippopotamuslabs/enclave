package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_inference_telemetry",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("message_id"),
        Index("chat_id"),
        Index("provider_key"),
        Index("model_id"),
        Index("completed_at_ms"),
    ],
)
data class MessageInferenceTelemetryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "chat_id") val chatId: String,
    @ColumnInfo(name = "sequence_index") val sequenceIndex: Int,
    @ColumnInfo(name = "provider_key") val providerKey: String,
    @ColumnInfo(name = "provider_label") val providerLabel: String,
    @ColumnInfo(name = "model_id") val modelId: String,
    @ColumnInfo(name = "model_label") val modelLabel: String,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "request_started_at_ms") val requestStartedAtMs: Long,
    @ColumnInfo(name = "first_output_token_at_ms") val firstOutputTokenAtMs: Long?,
    @ColumnInfo(name = "completed_at_ms") val completedAtMs: Long?,
    @ColumnInfo(name = "input_tokens") val inputTokens: Int?,
    @ColumnInfo(name = "output_tokens") val outputTokens: Int?,
    @ColumnInfo(name = "reasoning_tokens") val reasoningTokens: Int?,
    @ColumnInfo(name = "cached_input_tokens") val cachedInputTokens: Int?,
    @ColumnInfo(name = "cache_read_input_tokens") val cacheReadInputTokens: Int?,
    @ColumnInfo(name = "cache_write_input_tokens") val cacheWriteInputTokens: Int?,
    @ColumnInfo(name = "cache_creation_input_tokens") val cacheCreationInputTokens: Int?,
    @ColumnInfo(name = "cost_usd_decimal") val costUsdDecimal: String?,
    @ColumnInfo(name = "cost_status") val costStatus: String,
    @ColumnInfo(name = "cost_source") val costSource: String,
    @ColumnInfo(name = "app_version_name") val appVersionName: String,
    @ColumnInfo(name = "app_version_code") val appVersionCode: Long,
    @ColumnInfo(name = "telemetry_schema_version") val telemetrySchemaVersion: Int,
    @ColumnInfo(name = "pending_sync") val pendingSync: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "deleted") val deleted: Boolean = false,
)
