package com.browntowndev.enclave.core.data.mapper.message

import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageFileEntity
import com.browntowndev.enclave.core.data.local.WebSearchSourceEntity
import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SYNC_STATE_PENDING = 1

fun MessageEntity.toDomain(
    webSearchSources: List<WebSearchSourceEntity> = emptyList(),
    files: List<MessageFileEntity> = emptyList(),
    documents: List<DocumentEntity> = emptyList(),
    transcriptSegments: List<MessageTranscriptSegment> = emptyList(),
    displayUpdatedAt: Long? = null,
): Message {
    val mediaUris = decodeJsonListOrEmpty<String>(generatedMediaUrisJson)
    val mediaAssetIds = decodeJsonListOrEmpty<String>(generatedMediaAssetIdsJson)
    val mediaTypes = decodeJsonListOrEmpty<String>(generatedMediaTypesJson)
    val mediaPrompts = decodeJsonListOrEmpty<String>(generatedMediaPromptsJson)
    val imageUris = decodeJsonListOrEmpty<String>(imageUrisJson)

    return Message(
        id = id,
        content =
            Content(
                text = content,
                pipelineStep = pipelineStep,
                imageUris = imageUris,
                webSearchSources = webSearchSources.map { it.toDomain() },
                artifacts =
                    documents.map {
                        ArtifactGenerationRequest(
                            artifactId = it.id,
                            targetFormat = it.targetFormat,
                            title = it.title,
                            content = it.json,
                        )
                    },
                fileAttachments = files.map { it.toDomain() },
                generatedMediaUris = mediaUris,
                generatedMediaAssetIds = mediaAssetIds,
                generatedMediaTypes = mediaTypes,
                generatedMediaPrompts = mediaPrompts,
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
        webSearchSources = webSearchSources.map { it.toDomain() },
        transcriptSegments = transcriptSegments,
        updatedAt =
            displayUpdatedAt ?: maxDisplayUpdatedAt(
                messageUpdatedAt = updatedAt,
                webSearchSources = webSearchSources,
                files = files,
                documents = documents,
            ),
    )
}

private fun MessageEntity.maxDisplayUpdatedAt(
    messageUpdatedAt: Long,
    webSearchSources: List<WebSearchSourceEntity>,
    files: List<MessageFileEntity>,
    documents: List<DocumentEntity>,
): Long {
    val childUpdatedAt =
        sequenceOf(
            webSearchSources.asSequence().map { it.updatedAt },
            files.asSequence().map { it.updatedAt },
            documents.asSequence().map { it.updatedAt },
        ).flatten()
    return childUpdatedAt.fold(messageUpdatedAt) { max, updatedAt -> maxOf(max, updatedAt) }
}

fun Message.toEntity(): MessageEntity {
    val mediaUrisJson =
        if (content.generatedMediaUris.isNotEmpty()) {
            Json.encodeToString(content.generatedMediaUris)
        } else {
            null
        }

    val mediaAssetIdsJson =
        if (content.generatedMediaAssetIds.isNotEmpty()) {
            Json.encodeToString(content.generatedMediaAssetIds)
        } else {
            null
        }

    val mediaTypesJson =
        if (content.generatedMediaTypes.isNotEmpty()) {
            Json.encodeToString(content.generatedMediaTypes)
        } else {
            null
        }

    val mediaPromptsJson =
        if (content.generatedMediaPrompts.isNotEmpty()) {
            Json.encodeToString(content.generatedMediaPrompts)
        } else {
            null
        }

    val imageUrisJson =
        if (content.imageUris.isNotEmpty()) {
            Json.encodeToString(content.imageUris)
        } else {
            null
        }

    return MessageEntity(
        id = id,
        content = content.text,
        imageUrisJson = imageUrisJson,
        role = role,
        chatId =
            requireNotNull(
                chatId,
            ) { "Cannot persist Message(id=${id.value}) with null chatId — must go through CreateUserMessageUseCase" },
        messageState = messageState,
        createdAt = createdAt,
        modelType = modelType,
        pipelineStep = content.pipelineStep,
        artifactsJson = null,
        generatedMediaUrisJson = mediaUrisJson,
        generatedMediaAssetIdsJson = mediaAssetIdsJson,
        generatedMediaTypesJson = mediaTypesJson,
        generatedMediaPromptsJson = mediaPromptsJson,
        pendingSync = SYNC_STATE_PENDING,
        updatedAt = updatedAt ?: System.currentTimeMillis(),
    )
}
