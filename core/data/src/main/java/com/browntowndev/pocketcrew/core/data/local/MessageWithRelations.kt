package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep

/**
 * DTO for message with its crew pipeline step.
 * Used for fetching messages along with their pipeline step information.
 * Thinking is now stored directly on the MessageEntity.
 */
data class MessageWithPipelineStep(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "message_id"
    )
    val pipelineStep: CrewPipelineStepEntity?
)
