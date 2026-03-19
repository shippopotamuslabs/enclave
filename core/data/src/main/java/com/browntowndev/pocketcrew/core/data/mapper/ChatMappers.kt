package com.browntowndev.pocketcrew.core.data.mapper

import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.CrewPipelineStepEntity
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageWithPipelineStep
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message

/**
 * Mappers for converting between domain models and Room entities.
 */

/**
 * Converts ChatEntity to domain Chat model.
 */
fun ChatEntity.toDomain(): Chat = Chat(
    id = id,
    name = name,
    created = created,
    lastModified = lastModified,
    pinned = pinned
)

/**
 * Converts domain Chat model to ChatEntity.
 */
fun Chat.toEntity(): ChatEntity = ChatEntity(
    id = id,
    name = name,
    created = created,
    lastModified = lastModified,
    pinned = pinned
)

/**
 * Converts MessageEntity to domain Message model.
 */
fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        content = Content(text = content),
        role = role,
        chatId = chatId,
        messageState = messageState,
        createdAt = createdAt,
        thinkingDurationSeconds = thinkingDurationSeconds,
        thinkingRaw = thinkingRaw,
        thinkingStartTime = thinkingStartTime,
        thinkingEndTime = thinkingEndTime,
        modelType = modelType
    )
}

/**
 * Converts MessageWithPipelineStep to domain Message with Content containing pipeline step.
 */
fun MessageWithPipelineStep.toDomain(): Message {
    return Message(
        id = message.id,
        content = Content(
            text = message.content,
            pipelineStep = pipelineStep?.pipelineStep
        ),
        role = message.role,
        chatId = message.chatId,
        messageState = message.messageState,
        createdAt = message.createdAt,
        thinkingDurationSeconds = message.thinkingDurationSeconds,
        thinkingRaw = message.thinkingRaw,
        thinkingStartTime = message.thinkingStartTime,
        thinkingEndTime = message.thinkingEndTime,
        modelType = message.modelType
    )
}

/**
 * Converts domain Message model to MessageEntity.
 */
fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    content = content.text,
    role = role,
    chatId = chatId,
    messageState = messageState,
    createdAt = createdAt,
    modelType = modelType
)
