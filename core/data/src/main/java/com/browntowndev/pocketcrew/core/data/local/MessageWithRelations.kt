package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep

/**
 * DTO for message with its crew pipeline step.
 * Used for fetching messages along with their pipeline step information.
 */
data class MessageWithPipelineStep(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "message_id"
    )
    val pipelineStep: CrewPipelineStepEntity?
)

/**
 * DTO for message with its thinking steps.
 * Used for fetching messages along with their thinking chunks.
 */
data class MessageWithThinkingSteps(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "message_id"
    )
    val thinkingSteps: List<ThinkingStepsEntity>
)

/**
 * DTO for message with both pipeline step and thinking steps.
 */
data class MessageWithAllRelations(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "message_id"
    )
    val pipelineStep: CrewPipelineStepEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "message_id"
    )
    val thinkingSteps: List<ThinkingStepsEntity>
)
