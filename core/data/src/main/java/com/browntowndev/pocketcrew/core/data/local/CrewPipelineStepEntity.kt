package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep

/**
 * Entity to track which pipeline step a message belongs to in Crew mode.
 * This associates a message with its pipeline step (DRAFT_ONE, DRAFT_TWO, SYNTHESIS, FINAL).
 */
@Entity(
    tableName = "crew_pipeline_steps",
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
data class CrewPipelineStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "message_id")
    val messageId: Long,
    @ColumnInfo(name = "pipeline_step")
    val pipelineStep: PipelineStep
)
