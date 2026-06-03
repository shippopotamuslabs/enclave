package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "profile_corpus_messages",
    foreignKeys = [
        ForeignKey(
            entity = ProfileCorpusConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversation_id"),
        Index("corpus_run_id"),
    ],
)
data class ProfileCorpusMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    @ColumnInfo(name = "corpus_run_id")
    val corpusRunId: String,
    @ColumnInfo(name = "role")
    val role: String,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long?,
    @ColumnInfo(name = "order_index")
    val orderIndex: Int,
)
