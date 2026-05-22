package com.browntowndev.enclave.core.data.security

import com.browntowndev.enclave.core.data.local.ApiCredentialsEntity
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.ChatSummaryEntity
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.MediaProviderEntity
import com.browntowndev.enclave.core.data.local.MemoriesEntity
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageFileEntity
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentEntity
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisEntity
import com.browntowndev.enclave.core.data.local.StudioAlbumEntity
import com.browntowndev.enclave.core.data.local.StudioMediaEntity
import com.browntowndev.enclave.core.data.local.TtsProviderEntity
import com.browntowndev.enclave.core.data.local.WebSearchSourceEntity

@Suppress("TooManyFunctions")
object SensitiveEntityFieldEncryptors {
    fun encrypt(
        entity: ChatEntity,
        healer: SensitivePersistenceSelfHealer,
    ): ChatEntity = entity.copy(name = healer.repair("ChatEntity", "name", entity.id.value, entity.name))

    fun decrypt(
        entity: ChatEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): ChatEntity = entity.copy(name = healer.decrypt("ChatEntity", "name", entity.id.value, entity.name, readRepairs))

    fun encrypt(
        entity: MessageEntity,
        healer: SensitivePersistenceSelfHealer,
    ): MessageEntity =
        entity.copy(
            content = healer.repair("MessageEntity", "content", entity.id.value, entity.content),
            imageUrisJson = healer.repairNullable("MessageEntity", "imageUrisJson", entity.id.value, entity.imageUrisJson),
            thinkingRaw = healer.repairNullable("MessageEntity", "thinkingRaw", entity.id.value, entity.thinkingRaw),
            artifactsJson = healer.repairNullable("MessageEntity", "artifactsJson", entity.id.value, entity.artifactsJson),
            generatedMediaUrisJson =
                healer.repairNullable("MessageEntity", "generatedMediaUrisJson", entity.id.value, entity.generatedMediaUrisJson),
            generatedMediaAssetIdsJson =
                healer.repairNullable(
                    "MessageEntity",
                    "generatedMediaAssetIdsJson",
                    entity.id.value,
                    entity.generatedMediaAssetIdsJson,
                ),
            generatedMediaPromptsJson =
                healer.repairNullable("MessageEntity", "generatedMediaPromptsJson", entity.id.value, entity.generatedMediaPromptsJson),
        )

    fun decrypt(
        entity: MessageEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): MessageEntity =
        entity.copy(
            content = healer.decrypt("MessageEntity", "content", entity.id.value, entity.content, readRepairs),
            imageUrisJson = healer.decryptNullable("MessageEntity", "imageUrisJson", entity.id.value, entity.imageUrisJson, readRepairs),
            thinkingRaw = healer.decryptNullable("MessageEntity", "thinkingRaw", entity.id.value, entity.thinkingRaw, readRepairs),
            artifactsJson = healer.decryptNullable("MessageEntity", "artifactsJson", entity.id.value, entity.artifactsJson, readRepairs),
            generatedMediaUrisJson =
                healer.decryptNullable(
                    "MessageEntity",
                    "generatedMediaUrisJson",
                    entity.id.value,
                    entity.generatedMediaUrisJson,
                    readRepairs,
                ),
            generatedMediaAssetIdsJson =
                healer.decryptNullable(
                    "MessageEntity",
                    "generatedMediaAssetIdsJson",
                    entity.id.value,
                    entity.generatedMediaAssetIdsJson,
                    readRepairs,
                ),
            generatedMediaPromptsJson =
                healer.decryptNullable(
                    "MessageEntity",
                    "generatedMediaPromptsJson",
                    entity.id.value,
                    entity.generatedMediaPromptsJson,
                    readRepairs,
                ),
        )

    fun encrypt(
        entity: ChatSummaryEntity,
        healer: SensitivePersistenceSelfHealer,
    ): ChatSummaryEntity = entity.copy(content = healer.repair("ChatSummaryEntity", "content", entity.chatId.value, entity.content))

    fun decrypt(
        entity: ChatSummaryEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): ChatSummaryEntity =
        entity.copy(content = healer.decrypt("ChatSummaryEntity", "content", entity.chatId.value, entity.content, readRepairs))

    fun encrypt(
        entity: MemoriesEntity,
        healer: SensitivePersistenceSelfHealer,
    ): MemoriesEntity = entity.copy(content = healer.repair("MemoriesEntity", "content", entity.id, entity.content))

    fun decrypt(
        entity: MemoriesEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): MemoriesEntity = entity.copy(content = healer.decrypt("MemoriesEntity", "content", entity.id, entity.content, readRepairs))

    fun encrypt(
        entity: MessageVisionAnalysisEntity,
        healer: SensitivePersistenceSelfHealer,
    ): MessageVisionAnalysisEntity =
        entity.copy(
            imageUri = healer.repair("MessageVisionAnalysisEntity", "imageUri", entity.id, entity.imageUri),
            promptText = healer.repair("MessageVisionAnalysisEntity", "promptText", entity.id, entity.promptText),
            analysisText = healer.repair("MessageVisionAnalysisEntity", "analysisText", entity.id, entity.analysisText),
        )

    fun decrypt(
        entity: MessageVisionAnalysisEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): MessageVisionAnalysisEntity =
        entity.copy(
            imageUri = healer.decrypt("MessageVisionAnalysisEntity", "imageUri", entity.id, entity.imageUri, readRepairs),
            promptText = healer.decrypt("MessageVisionAnalysisEntity", "promptText", entity.id, entity.promptText, readRepairs),
            analysisText = healer.decrypt("MessageVisionAnalysisEntity", "analysisText", entity.id, entity.analysisText, readRepairs),
        )

    fun encrypt(
        entity: MessageFileEntity,
        healer: SensitivePersistenceSelfHealer,
    ): MessageFileEntity =
        entity.copy(
            name = healer.repair("MessageFileEntity", "name", entity.id, entity.name),
            content = healer.repair("MessageFileEntity", "content", entity.id, entity.content),
            remoteUrl = healer.repairNullable("MessageFileEntity", "remoteUrl", entity.id, entity.remoteUrl),
            stagingRemoteUrl = healer.repairNullable("MessageFileEntity", "stagingRemoteUrl", entity.id, entity.stagingRemoteUrl),
        )

    fun decrypt(
        entity: MessageFileEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): MessageFileEntity =
        entity.copy(
            name = healer.decrypt("MessageFileEntity", "name", entity.id, entity.name, readRepairs),
            content = healer.decrypt("MessageFileEntity", "content", entity.id, entity.content, readRepairs),
            remoteUrl = healer.decryptNullable("MessageFileEntity", "remoteUrl", entity.id, entity.remoteUrl, readRepairs),
            stagingRemoteUrl =
                healer.decryptNullable(
                    "MessageFileEntity",
                    "stagingRemoteUrl",
                    entity.id,
                    entity.stagingRemoteUrl,
                    readRepairs,
                ),
        )

    fun encrypt(
        entity: WebSearchSourceEntity,
        healer: SensitivePersistenceSelfHealer,
    ): WebSearchSourceEntity =
        entity.copy(
            title = healer.repair("WebSearchSourceEntity", "title", entity.id, entity.title),
            url = healer.repair("WebSearchSourceEntity", "url", entity.id, entity.url),
            content = healer.repair("WebSearchSourceEntity", "content", entity.id, entity.content),
        )

    fun decrypt(
        entity: WebSearchSourceEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): WebSearchSourceEntity =
        entity.copy(
            title = healer.decrypt("WebSearchSourceEntity", "title", entity.id, entity.title, readRepairs),
            url = healer.decrypt("WebSearchSourceEntity", "url", entity.id, entity.url, readRepairs),
            content = healer.decrypt("WebSearchSourceEntity", "content", entity.id, entity.content, readRepairs),
        )

    fun encrypt(
        entity: MessageTranscriptSegmentEntity,
        healer: SensitivePersistenceSelfHealer,
    ): MessageTranscriptSegmentEntity = entity.copy(text = healer.repair("MessageTranscriptSegmentEntity", "text", entity.id, entity.text))

    fun decrypt(
        entity: MessageTranscriptSegmentEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): MessageTranscriptSegmentEntity =
        entity.copy(text = healer.decrypt("MessageTranscriptSegmentEntity", "text", entity.id, entity.text, readRepairs))

    fun encrypt(
        entity: ApiModelConfigurationEntity,
        healer: SensitivePersistenceSelfHealer,
    ): ApiModelConfigurationEntity =
        entity.copy(
            displayName = healer.repair("ApiModelConfigurationEntity", "displayName", entity.id.value, entity.displayName),
            customHeaders = healer.repair("ApiModelConfigurationEntity", "customHeaders", entity.id.value, entity.customHeaders),
            systemPrompt = healer.repair("ApiModelConfigurationEntity", "systemPrompt", entity.id.value, entity.systemPrompt),
        )

    fun encrypt(
        entity: LocalModelConfigurationEntity,
        healer: SensitivePersistenceSelfHealer,
    ): LocalModelConfigurationEntity =
        entity.copy(
            displayName = healer.repair("LocalModelConfigurationEntity", "displayName", entity.id.value, entity.displayName),
            systemPrompt = healer.repairNullable("LocalModelConfigurationEntity", "systemPrompt", entity.id.value, entity.systemPrompt),
        )

    fun encrypt(
        entity: TtsProviderEntity,
        healer: SensitivePersistenceSelfHealer,
    ): TtsProviderEntity =
        entity.copy(
            displayName = healer.repair("TtsProviderEntity", "displayName", entity.id, entity.displayName),
            voiceName = healer.repair("TtsProviderEntity", "voiceName", entity.id, entity.voiceName),
            modelName = healer.repairNullable("TtsProviderEntity", "modelName", entity.id, entity.modelName),
            baseUrl = healer.repairNullable("TtsProviderEntity", "baseUrl", entity.id, entity.baseUrl),
        )

    fun decrypt(
        entity: TtsProviderEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): TtsProviderEntity =
        entity.copy(
            displayName = healer.decrypt("TtsProviderEntity", "displayName", entity.id, entity.displayName, readRepairs),
            voiceName = healer.decrypt("TtsProviderEntity", "voiceName", entity.id, entity.voiceName, readRepairs),
            modelName = healer.decryptNullable("TtsProviderEntity", "modelName", entity.id, entity.modelName, readRepairs),
            baseUrl = healer.decryptNullable("TtsProviderEntity", "baseUrl", entity.id, entity.baseUrl, readRepairs),
        )

    fun encrypt(
        entity: MediaProviderEntity,
        healer: SensitivePersistenceSelfHealer,
    ): MediaProviderEntity =
        entity.copy(
            displayName = healer.repair("MediaProviderEntity", "displayName", entity.id, entity.displayName),
            modelName = healer.repairNullable("MediaProviderEntity", "modelName", entity.id, entity.modelName),
            baseUrl = healer.repairNullable("MediaProviderEntity", "baseUrl", entity.id, entity.baseUrl),
        )

    fun decrypt(
        entity: MediaProviderEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): MediaProviderEntity =
        entity.copy(
            displayName = healer.decrypt("MediaProviderEntity", "displayName", entity.id, entity.displayName, readRepairs),
            modelName = healer.decryptNullable("MediaProviderEntity", "modelName", entity.id, entity.modelName, readRepairs),
            baseUrl = healer.decryptNullable("MediaProviderEntity", "baseUrl", entity.id, entity.baseUrl, readRepairs),
        )

    fun encrypt(
        entity: ApiCredentialsEntity,
        healer: SensitivePersistenceSelfHealer,
    ): ApiCredentialsEntity =
        entity.copy(
            displayName = healer.repair("ApiCredentialsEntity", "displayName", entity.id.value, entity.displayName),
            baseUrl = healer.repairNullable("ApiCredentialsEntity", "baseUrl", entity.id.value, entity.baseUrl),
        )

    fun decrypt(
        entity: ApiCredentialsEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): ApiCredentialsEntity =
        entity.copy(
            displayName = healer.decrypt("ApiCredentialsEntity", "displayName", entity.id.value, entity.displayName, readRepairs),
            baseUrl = healer.decryptNullable("ApiCredentialsEntity", "baseUrl", entity.id.value, entity.baseUrl, readRepairs),
        )

    fun decrypt(
        entity: ApiModelConfigurationEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): ApiModelConfigurationEntity =
        entity.copy(
            displayName =
                healer.decrypt(
                    "ApiModelConfigurationEntity",
                    "displayName",
                    entity.id.value,
                    entity.displayName,
                    readRepairs,
                ),
            customHeaders =
                healer.decrypt(
                    "ApiModelConfigurationEntity",
                    "customHeaders",
                    entity.id.value,
                    entity.customHeaders,
                    readRepairs,
                ),
            systemPrompt =
                healer.decrypt(
                    "ApiModelConfigurationEntity",
                    "systemPrompt",
                    entity.id.value,
                    entity.systemPrompt,
                    readRepairs,
                ),
        )

    fun decrypt(
        entity: LocalModelConfigurationEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): LocalModelConfigurationEntity =
        entity.copy(
            displayName = healer.decrypt("LocalModelConfigurationEntity", "displayName", entity.id.value, entity.displayName, readRepairs),
            systemPrompt =
                healer.decryptNullable(
                    "LocalModelConfigurationEntity",
                    "systemPrompt",
                    entity.id.value,
                    entity.systemPrompt,
                    readRepairs,
                ),
        )

    fun encrypt(
        entity: StudioAlbumEntity,
        healer: SensitivePersistenceSelfHealer,
    ): StudioAlbumEntity = entity.copy(name = healer.repair("StudioAlbumEntity", "name", entity.id, entity.name))

    fun decrypt(
        entity: StudioAlbumEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): StudioAlbumEntity = entity.copy(name = healer.decrypt("StudioAlbumEntity", "name", entity.id, entity.name, readRepairs))

    fun encrypt(
        entity: StudioMediaEntity,
        healer: SensitivePersistenceSelfHealer,
    ): StudioMediaEntity =
        entity.copy(
            prompt = healer.repair("StudioMediaEntity", "prompt", entity.id, entity.prompt),
            mediaUri = healer.repair("StudioMediaEntity", "mediaUri", entity.id, entity.mediaUri),
            remoteUrl = healer.repairNullable("StudioMediaEntity", "remoteUrl", entity.id, entity.remoteUrl),
            remoteThumbnailUrl = healer.repairNullable("StudioMediaEntity", "remoteThumbnailUrl", entity.id, entity.remoteThumbnailUrl),
            stagingRemoteUrl = healer.repairNullable("StudioMediaEntity", "stagingRemoteUrl", entity.id, entity.stagingRemoteUrl),
        )

    fun decrypt(
        entity: StudioMediaEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): StudioMediaEntity =
        entity.copy(
            prompt = healer.decrypt("StudioMediaEntity", "prompt", entity.id, entity.prompt, readRepairs),
            mediaUri = healer.decrypt("StudioMediaEntity", "mediaUri", entity.id, entity.mediaUri, readRepairs),
            remoteUrl = healer.decryptNullable("StudioMediaEntity", "remoteUrl", entity.id, entity.remoteUrl, readRepairs),
            remoteThumbnailUrl =
                healer.decryptNullable(
                    "StudioMediaEntity",
                    "remoteThumbnailUrl",
                    entity.id,
                    entity.remoteThumbnailUrl,
                    readRepairs,
                ),
            stagingRemoteUrl =
                healer.decryptNullable(
                    "StudioMediaEntity",
                    "stagingRemoteUrl",
                    entity.id,
                    entity.stagingRemoteUrl,
                    readRepairs,
                ),
        )

    fun encrypt(
        entity: DocumentEntity,
        healer: SensitivePersistenceSelfHealer,
    ): DocumentEntity =
        entity.copy(
            json = healer.repair("DocumentEntity", "json", entity.id, entity.json),
            title = healer.repair("DocumentEntity", "title", entity.id, entity.title),
            author = healer.repairNullable("DocumentEntity", "author", entity.id, entity.author),
        )

    fun decrypt(
        entity: DocumentEntity,
        healer: SensitivePersistenceSelfHealer,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): DocumentEntity =
        entity.copy(
            json = healer.decrypt("DocumentEntity", "json", entity.id, entity.json, readRepairs),
            title = healer.decrypt("DocumentEntity", "title", entity.id, entity.title, readRepairs),
            author = healer.decryptNullable("DocumentEntity", "author", entity.id, entity.author, readRepairs),
        )

    private fun SensitivePersistenceSelfHealer.repair(
        owner: String,
        field: String,
        entityId: String,
        value: String,
    ): String =
        requireEncrypted(
            ref = SensitiveFieldRef(owner, field, PersistenceBoundary.ROOM),
            entityId = entityId,
            value = value,
        ) ?: value

    private fun SensitivePersistenceSelfHealer.repairNullable(
        owner: String,
        field: String,
        entityId: String,
        value: String?,
    ): String? =
        requireEncrypted(
            ref = SensitiveFieldRef(owner, field, PersistenceBoundary.ROOM),
            entityId = entityId,
            value = value,
        )

    private fun SensitivePersistenceSelfHealer.decrypt(
        owner: String,
        field: String,
        entityId: String,
        value: String,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): String =
        decryptForRead(
            ref = SensitiveFieldRef(owner, field, PersistenceBoundary.ROOM),
            entityId = entityId,
            value = value,
            readRepairs = readRepairs,
        ) ?: ""

    private fun SensitivePersistenceSelfHealer.decryptNullable(
        owner: String,
        field: String,
        entityId: String,
        value: String?,
        readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
    ): String? =
        decryptForRead(
            ref = SensitiveFieldRef(owner, field, PersistenceBoundary.ROOM),
            entityId = entityId,
            value = value,
            readRepairs = readRepairs,
        )
}
