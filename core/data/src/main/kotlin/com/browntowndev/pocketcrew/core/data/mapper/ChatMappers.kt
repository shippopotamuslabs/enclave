package com.browntowndev.pocketcrew.core.data.mapper

import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message

fun ChatEntity.toDomain(): Chat = Chat(
    id = id,
    name = name,
    created = created,
    lastModified = lastModified,
    pinned = pinned
)

fun Chat.toEntity(): ChatEntity = ChatEntity(
    id = id,
    name = name,
    created = created,
    lastModified = lastModified,
    pinned = pinned
)

fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        content = Content(
            text = content,
            pipelineStep = pipelineStep,
            imageUri = imageUri,
        ),
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

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    content = content.text,
    imageUri = content.imageUri,
    role = role,
    chatId = chatId,
    messageState = messageState,
    createdAt = createdAt,
    modelType = modelType,
    pipelineStep = content.pipelineStep
)
