package com.browntowndev.pocketcrew.core.data.mapper

import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.TavilySourceEntity
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.TavilySource

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

fun MessageEntity.toDomain(tavilySources: List<TavilySourceEntity> = emptyList()): Message {
    return Message(
        id = id,
        content = Content(
            text = content,
            pipelineStep = pipelineStep,
            imageUri = imageUri,
            tavilySources = tavilySources.map { it.toDomain() }
        ),
        role = role,
        chatId = chatId,
        messageState = messageState,
        createdAt = createdAt,
        thinkingDurationSeconds = thinkingDurationSeconds,
        thinkingRaw = thinkingRaw,
        thinkingStartTime = thinkingStartTime,
        thinkingEndTime = thinkingEndTime,
        modelType = modelType,
        tavilySources = tavilySources.map { it.toDomain() }
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

fun TavilySourceEntity.toDomain(): TavilySource = TavilySource(
    id = id,
    messageId = messageId,
    title = title,
    url = url,
    content = content,
    score = score,
    extracted = extracted,
)

fun TavilySource.toEntity(): TavilySourceEntity = TavilySourceEntity(
    id = id,
    messageId = messageId,
    title = title,
    url = url,
    content = content,
    score = score,
    extracted = extracted,
)
