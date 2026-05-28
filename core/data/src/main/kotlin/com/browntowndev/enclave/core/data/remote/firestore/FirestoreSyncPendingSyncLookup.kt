package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.inference.ModelType

/**
 * Reads [pendingSync] for allowlisted entity collections during event-feed apply.
 */
internal class FirestoreSyncPendingSyncLookup(
    private val database: EnclaveDatabase,
) {
    suspend fun pendingSync(
        collectionPath: String,
        entityId: String,
    ): Int =
        chatPending(collectionPath, entityId)
            ?: messagePending(collectionPath, entityId)
            ?: configPending(collectionPath, entityId)
            ?: studioPending(collectionPath, entityId)
            ?: 0

    private suspend fun chatPending(
        collectionPath: String,
        entityId: String,
    ): Int? =
        when (collectionPath) {
            "chats" -> database.chatDao().getChatById(ChatId(entityId))?.pendingSync
            "chat_summaries" ->
                database.chatSummaryDao().getSummaryForChatSync(ChatId(entityId))?.pendingSync
            else -> null
        }

    private suspend fun messagePending(
        collectionPath: String,
        entityId: String,
    ): Int? =
        when (collectionPath) {
            "messages" ->
                database
                    .messageQueryDao()
                    .getMessageById(MessageId(entityId))
                    ?.message
                    ?.pendingSync
            "message_files" -> database.messageFileDao().getById(entityId)?.pendingSync
            "web_search_sources" -> database.webSearchSourceDao().getById(entityId)?.pendingSync
            "message_transcript_segments" ->
                database.messageTranscriptSegmentDao().getById(entityId)?.pendingSync
            "message_inference_telemetry" ->
                database.messageInferenceTelemetryDao().getById(entityId)?.pendingSync
            "vision_analysis" -> database.messageVisionAnalysisDao().getById(entityId)?.pendingSync
            "documents" -> database.documentDao().getDocumentById(entityId)?.pendingSync
            else -> null
        }

    private suspend fun configPending(
        collectionPath: String,
        entityId: String,
    ): Int? =
        when (collectionPath) {
            "memories" -> database.memoriesDao().getMemoryById(entityId)?.pendingSync
            "api_credentials" ->
                database.apiCredentialsDao().getById(ApiCredentialsId(entityId))?.pendingSync
            "api_model_configs" ->
                database.apiModelConfigurationsDao().getById(ApiModelConfigurationId(entityId))?.pendingSync
            "local_models" -> database.localModelsDao().getById(LocalModelId(entityId))?.pendingSync
            "utility_models" -> database.utilityModelsDao().getById(UtilityModelId(entityId))?.pendingSync
            "local_model_configs" ->
                database.localModelConfigurationsDao().getById(LocalModelConfigurationId(entityId))?.pendingSync
            "default_models" -> defaultModelPending(entityId)
            "tts_providers" -> database.ttsProviderDao().getTtsProvider(entityId)?.pendingSync
            "media_providers" -> database.mediaProviderDao().getMediaProvider(entityId)?.pendingSync
            else -> null
        }

    private suspend fun studioPending(
        collectionPath: String,
        entityId: String,
    ): Int? =
        when (collectionPath) {
            "studio_albums" -> database.studioAlbumDao().getAlbumById(entityId)?.pendingSync
            "studio_gallery" -> database.studioMediaDao().getMediaById(entityId)?.pendingSync
            else -> null
        }

    private suspend fun defaultModelPending(entityId: String): Int? {
        val modelType = runCatching { ModelType.valueOf(entityId) }.getOrNull() ?: return 0
        return database.defaultModelsDao().getDefault(modelType)?.pendingSync
    }
}
