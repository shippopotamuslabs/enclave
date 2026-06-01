package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "profile_corpus_conversations",
    indices = [Index("corpus_run_id")],
)
data class ProfileCorpusConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "corpus_run_id")
    val corpusRunId: String,
    @ColumnInfo(name = "external_id")
    val externalId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "provider")
    val provider: String,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long?,
    @ColumnInfo(name = "user_token_count")
    val userTokenCount: Int,
    @ColumnInfo(name = "assistant_token_count")
    val assistantTokenCount: Int,
    @ColumnInfo(name = "message_count")
    val messageCount: Int,
    @ColumnInfo(name = "user_message_count")
    val userMessageCount: Int,
    /** Cached title + first user turn for embedding without reloading all messages. */
    @ColumnInfo(name = "embedding_seed_text")
    val embeddingSeedText: String,
    /** Populated in Phase 2 after scoring. */
    @ColumnInfo(name = "signal_score")
    val signalScore: Float? = null,
    /** Populated in Phase 2 after embedding + clustering. */
    @ColumnInfo(name = "cluster_id")
    val clusterId: Int? = null,
    @ColumnInfo(name = "selected_for_analysis")
    val selectedForAnalysis: Boolean = false,
)
