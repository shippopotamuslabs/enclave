package com.browntowndev.pocketcrew.core.data.mapper

import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageFileEntity
import com.browntowndev.pocketcrew.core.data.local.MessageWithFiles
import com.browntowndev.pocketcrew.core.data.local.TavilySourceEntity
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.FileAttachment
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.TavilySource
import kotlinx.serialization.json.Json
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import kotlinx.serialization.encodeToString

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

fun MessageWithFiles.toDomain(tavilySources: List<TavilySourceEntity> = emptyList()): Message {
    return message.toDomain(tavilySources, files)
}

fun MessageEntity.toDomain(
    tavilySources: List<TavilySourceEntity> = emptyList(),
    files: List<MessageFileEntity> = emptyList()
): Message {
    val artifacts = try {
        artifactsJson?.let { Json.decodeFromString<List<ArtifactGenerationRequest>>(it) } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    return Message(
        id = id,
        content = Content(
            text = content,
            pipelineStep = pipelineStep,
            imageUri = imageUri,
            tavilySources = tavilySources.map { it.toDomain() },
            artifacts = artifacts,
            fileAttachments = files.map { it.toDomain() }
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

fun Message.toEntity(): MessageEntity {
    val artifactsJson = if (content.artifacts.isNotEmpty()) {
        Json.encodeToString(content.artifacts)
    } else null

    return MessageEntity(
        id = id,
        content = content.text,
        imageUri = content.imageUri,
        role = role,
        chatId = chatId,
        messageState = messageState,
        createdAt = createdAt,
        modelType = modelType,
        pipelineStep = content.pipelineStep,
        artifactsJson = artifactsJson
    )
}

fun MessageFileEntity.toDomain(): FileAttachment = FileAttachment(
    name = name,
    content = content
)

fun FileAttachment.toEntity(id: String, messageId: String): MessageFileEntity = MessageFileEntity(
    id = id,
    messageId = messageId,
    name = name,
    content = content
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
