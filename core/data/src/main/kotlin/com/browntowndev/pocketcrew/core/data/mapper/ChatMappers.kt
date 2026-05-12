@file:Suppress(
    "MaxLineLength",
    "MagicNumber",
    "UnusedPrivateProperty",
    "UnusedParameter",
    "TooManyFunctions",
    "ComplexCondition",
    "LargeClass",
    "CyclomaticComplexMethod",
    "SwallowedException",
    "InstanceOfCheckForException",
    "ThrowsCount",
    "NestedBlockDepth",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "FunctionNaming",
    "LongMethod",
    "MatchingDeclarationName",
    "UnusedPrivateMember",
    "LongParameterList",
)

package com.browntowndev.pocketcrew.core.data.mapper

import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.DocumentEntity
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageFileEntity
import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptKindEntity
import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptSegmentEntity
import com.browntowndev.pocketcrew.core.data.local.MessageWithFiles
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceEntity
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.FileAttachment
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.pocketcrew.domain.model.chat.WebSearchSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SYNC_STATE_NONE = 0
private const val SYNC_STATE_PENDING = 1
private const val UNINITIALIZED_TIME = 0L

fun ChatEntity.toDomain(): Chat =
    Chat(
        id = id,
        name = name,
        created = created,
        lastModified = lastModified,
        pinned = pinned,
        isSyncing = created.time == UNINITIALIZED_TIME,
    )

fun Chat.toEntity(): ChatEntity =
    ChatEntity(
        id = id,
        name = name,
        created = created,
        lastModified = lastModified,
        pinned = pinned,
        pendingSync = SYNC_STATE_PENDING,
    )

fun MessageWithFiles.toDomain(
    webSearchSources: List<WebSearchSourceEntity> = emptyList(),
    documents: List<DocumentEntity> = emptyList(),
    transcriptSegments: List<MessageTranscriptSegment> = emptyList(),
): Message =
    message.toDomain(
        webSearchSources = webSearchSources,
        files = files,
        documents = documents,
        transcriptSegments = transcriptSegments,
    )

fun MessageEntity.toDomain(
    webSearchSources: List<WebSearchSourceEntity> = emptyList(),
    files: List<MessageFileEntity> = emptyList(),
    documents: List<DocumentEntity> = emptyList(),
    transcriptSegments: List<MessageTranscriptSegment> = emptyList(),
): Message {
    val mediaUris =
        try {
            generatedMediaUrisJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    val mediaAssetIds =
        try {
            generatedMediaAssetIdsJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    val mediaTypes =
        try {
            generatedMediaTypesJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    val mediaPrompts =
        try {
            generatedMediaPromptsJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    val imageUris =
        try {
            imageUrisJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

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
    )
}

fun MessageTranscriptSegmentEntity.toDomain(): MessageTranscriptSegment =
    when (kind) {
        MessageTranscriptKindEntity.THINKING ->
            MessageTranscriptSegment.Thinking(
                ordinal = ordinal,
                text = text,
                thinkingDurationSeconds = thinkingDurationSeconds,
                thinkingStartTime = thinkingStartTime,
                thinkingEndTime = thinkingEndTime,
            )
        else ->
            MessageTranscriptSegment.AssistantText(
                ordinal = ordinal,
                text = text,
            )
    }

fun MessageTranscriptSegment.toEntity(
    messageId: MessageId,
    id: String,
): MessageTranscriptSegmentEntity =
    when (this) {
        is MessageTranscriptSegment.Thinking ->
            MessageTranscriptSegmentEntity(
                id = id,
                messageId = messageId,
                ordinal = ordinal,
                kind = MessageTranscriptKindEntity.THINKING,
                text = text,
                thinkingStartTime = thinkingStartTime,
                thinkingEndTime = thinkingEndTime,
                thinkingDurationSeconds = thinkingDurationSeconds,
                pendingSync = 0,
                updatedAt = 0L,
                deleted = false,
            )
        is MessageTranscriptSegment.AssistantText ->
            MessageTranscriptSegmentEntity(
                id = id,
                messageId = messageId,
                ordinal = ordinal,
                kind = MessageTranscriptKindEntity.ASSISTANT_TEXT,
                text = text,
                thinkingStartTime = null,
                thinkingEndTime = null,
                thinkingDurationSeconds = null,
                pendingSync = 0,
                updatedAt = 0L,
                deleted = false,
            )
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
        artifactsJson = null, // Moved to message_file table
        generatedMediaUrisJson = mediaUrisJson,
        generatedMediaAssetIdsJson = mediaAssetIdsJson,
        generatedMediaTypesJson = mediaTypesJson,
        generatedMediaPromptsJson = mediaPromptsJson,
        pendingSync = SYNC_STATE_PENDING,
    )
}

fun MessageFileEntity.toDomain(): FileAttachment =
    FileAttachment(
        name = name,
        content = content,
    )

fun FileAttachment.toEntity(
    id: String,
    messageId: String,
): MessageFileEntity =
    MessageFileEntity(
        id = id,
        messageId = messageId,
        name = name,
        content = content,
        mimeType = MimeType.PLAIN_TEXT,
        syncStatus = "PENDING_UPLOAD",
        pendingSync = SYNC_STATE_NONE, // Will be set to 1 by the worker after binary upload
    )

fun WebSearchSourceEntity.toDomain(): WebSearchSource =
    WebSearchSource(
        id = id,
        messageId = messageId,
        title = title,
        url = url,
        content = content,
        score = score,
        extracted = extracted,
    )

fun WebSearchSource.toEntity(): WebSearchSourceEntity =
    WebSearchSourceEntity(
        id = id,
        messageId = messageId,
        title = title,
        url = url,
        content = content,
        score = score,
        extracted = extracted,
        pendingSync = SYNC_STATE_PENDING,
    )
