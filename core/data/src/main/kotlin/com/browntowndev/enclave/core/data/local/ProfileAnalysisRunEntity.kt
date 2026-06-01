package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists the state of one profile analysis run for resumption if the worker is interrupted.
 *
 * [corpusRunId] is the primary key and matches the corpus + findings tables.
 * [currentPassIndex] is 0-3; passes below it are complete.
 */
@Entity(tableName = "profile_analysis_runs")
data class ProfileAnalysisRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "corpus_run_id")
    val corpusRunId: String,
    @ColumnInfo(name = "config_id")
    val configId: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "current_pass_index")
    val currentPassIndex: Int = 0,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at_ms")
    val completedAtMs: Long? = null,
)
