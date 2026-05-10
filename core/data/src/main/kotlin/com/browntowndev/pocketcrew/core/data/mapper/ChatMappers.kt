package com.browntowndev.pocketcrew.core.data.mapper

import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageFileEntity
import com.browntowndev.pocketcrew.core.data.local.MessageWithFiles
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceEntity
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.FileAttachment
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.WebSearchSource
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.artifact.FileSource
import kotlinx.serialization.json.Json
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import kotlinx.serialization.encodeToString

fun ChatEntity.toDomain(): Chat = Chat(
    id = id,
    name = name,
    created = created,
    lastModified = lastModified,
    pinned = pinned,
    isSyncing = created.time == 0L
)

fun Chat.toEntity(): ChatEntity = ChatEntity(
    id = id,
    name = name,
    created = created,
    lastModified = lastModified,
    pinned = pinned,
    pendingSync = 1
)

fun MessageWithFiles.toDomain(webSearchSources: List<WebSearchSourceEntity> = emptyList()): Message {
    return message.toDomain(webSearchSources, files)
}

fun MessageEntity.toDomain(
    webSearchSources: List<WebSearchSourceEntity> = emptyList(),
    files: List<MessageFileEntity> = emptyList()
): Message {
    val mediaUris = try {
        generatedMediaUrisJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    val mediaAssetIds = try {
        generatedMediaAssetIdsJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    val mediaTypes = try {
        generatedMediaTypesJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    val mediaPrompts = try {
        generatedMediaPromptsJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    val imageUris = try {
        imageUrisJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    return Message(
        id = id,
        content = Content(
            text = content,
            pipelineStep = pipelineStep,
            imageUris = imageUris,
            webSearchSources = webSearchSources.map { it.toDomain() },
            artifacts = files.filter { it.fileSource == FileSource.GENERATED }.map { 
                ArtifactGenerationRequest(targetFormat = it.targetFormat ?: MimeType.PDF, title = it.name, content = it.content)
            },
            fileAttachments = files.filter { it.fileSource == FileSource.ATTACHMENT }.map { it.toDomain() },
            generatedMediaUris = mediaUris,
            generatedMediaAssetIds = mediaAssetIds,
            generatedMediaTypes = mediaTypes,
            generatedMediaPrompts = mediaPrompts
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
        webSearchSources = webSearchSources.map { it.toDomain() }
    )
}

fun Message.toEntity(): MessageEntity {
    val mediaUrisJson = if (content.generatedMediaUris.isNotEmpty()) {
        Json.encodeToString(content.generatedMediaUris)
    } else null

    val mediaAssetIdsJson = if (content.generatedMediaAssetIds.isNotEmpty()) {
        Json.encodeToString(content.generatedMediaAssetIds)
    } else null

    val mediaTypesJson = if (content.generatedMediaTypes.isNotEmpty()) {
        Json.encodeToString(content.generatedMediaTypes)
    } else null

    val mediaPromptsJson = if (content.generatedMediaPrompts.isNotEmpty()) {
        Json.encodeToString(content.generatedMediaPrompts)
    } else null

    val imageUrisJson = if (content.imageUris.isNotEmpty()) {
        Json.encodeToString(content.imageUris)
    } else null

    return MessageEntity(
        id = id,
        content = content.text,
        imageUrisJson = imageUrisJson,
        role = role,
        chatId = requireNotNull(chatId) { "Cannot persist Message(id=${id.value}) with null chatId — must go through CreateUserMessageUseCase" },
        messageState = messageState,
        createdAt = createdAt,
        modelType = modelType,
        pipelineStep = content.pipelineStep,
        artifactsJson = null, // Moved to message_file table
        generatedMediaUrisJson = mediaUrisJson,
        generatedMediaAssetIdsJson = mediaAssetIdsJson,
        generatedMediaTypesJson = mediaTypesJson,
        generatedMediaPromptsJson = mediaPromptsJson,
        pendingSync = 1
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
    content = content,
    mimeType = MimeType.PLAIN_TEXT,
    fileSource = FileSource.ATTACHMENT,
    targetFormat = null,
    syncStatus = "PENDING_UPLOAD",
    pendingSync = 0 // Will be set to 1 by the worker after binary upload
)

fun WebSearchSourceEntity.toDomain(): WebSearchSource = WebSearchSource(
    id = id,
    messageId = messageId,
    title = title,
    url = url,
    content = content,
    score = score,
    extracted = extracted,
)

fun WebSearchSource.toEntity(): WebSearchSourceEntity = WebSearchSourceEntity(
    id = id,
    messageId = messageId,
    title = title,
    url = url,
    content = content,
    score = score,
    extracted = extracted,
    pendingSync = 1
)
