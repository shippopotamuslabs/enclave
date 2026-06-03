package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "profile_corpus_findings",
    indices = [Index("corpus_run_id")],
)
data class ProfileFindingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "corpus_run_id")
    val corpusRunId: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = System.currentTimeMillis(),
)
