package com.browntowndev.pocketcrew.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep

/**
 * Entity for storing step completion data from Crew mode.
 * Each step in the pipeline (Draft One, Draft Two, Synthesis, Final)
 * generates output that should be persisted for display and rehydration.
 * Display names are derived from stepType and modelType at runtime.
 */
@Entity(
    tableName = "step_completion",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("message_id")]
)
data class StepCompletionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "message_id")
    val messageId: Long,
    @ColumnInfo(name = "step_type")
    val stepType: PipelineStep,
    @ColumnInfo(name = "step_output")
    val stepOutput: String,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,
    @ColumnInfo(name = "thinking_steps")
    val thinkingSteps: String, // JSON array stored as string
    @ColumnInfo(name = "model_type")
    val modelType: ModelType
)
