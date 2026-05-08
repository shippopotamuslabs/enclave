package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MemoriesDao
import com.browntowndev.pocketcrew.core.data.local.MemoriesEntity
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceEntity
import com.browntowndev.pocketcrew.core.data.local.TtsProviderDao
import com.browntowndev.pocketcrew.core.data.local.TtsProviderEntity
import com.browntowndev.pocketcrew.core.data.local.MediaProviderDao
import com.browntowndev.pocketcrew.core.data.local.MediaProviderEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelEntity
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.DefaultModelEntity
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaEntity
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumDao
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumEntity
import com.browntowndev.pocketcrew.core.data.local.MessageFileDao
import com.browntowndev.pocketcrew.core.data.local.MessageFileEntity
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisEntity
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.qualifier.ApplicationScope
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Blob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Date

@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val memoriesDao: MemoriesDao,
    private val webSearchSourceDao: WebSearchSourceDao,
    private val ttsProviderDao: TtsProviderDao,
    private val mediaProviderDao: MediaProviderDao,
    private val apiModelConfigurationsDao: ApiModelConfigurationsDao,
    private val localModelsDao: LocalModelsDao,
    private val apiCredentialsDao: ApiCredentialsDao,
    private val localModelConfigurationsDao: LocalModelConfigurationsDao,
    private val defaultModelsDao: DefaultModelsDao,
    private val studioMediaDao: StudioMediaDao,
    private val studioAlbumDao: StudioAlbumDao,
    private val messageFileDao: MessageFileDao,
    private val messageVisionAnalysisDao: MessageVisionAnalysisDao,
    private val embeddingDao: com.browntowndev.pocketcrew.core.data.local.EmbeddingDao,
    private val apiKeyManager: com.browntowndev.pocketcrew.core.data.security.ApiKeyManager,
    private val cryptoManager: CryptoManagerPort,
    private val loggingPort: LoggingPort,
    @ApplicationScope private val externalScope: CoroutineScope,
    private val userIdProvider: () -> String?,
    private val embeddingCoordinator: EmbeddingRegenerationCoordinator
) : CloudSyncPort {

    companion object {
        private const val TAG = "FirestoreDataSource"
    }

    private val listenerRegistrations = mutableListOf<ListenerRegistration>()
    private val listenerMutex = Mutex()

    private interface EntitySyncAdapter<RoomT> {
        val collectionPath: String
        suspend fun claimPending()
        suspend fun getClaimedForSync(): List<RoomT>
        suspend fun markSynced(entities: List<RoomT>)
        suspend fun toFirestore(local: RoomT): Map<String, Any?>
        fun fromFirestore(doc: DocumentSnapshot): RoomT?
        suspend fun upsertLocal(entity: RoomT, doc: DocumentSnapshot, overwrite: Boolean = false)
        suspend fun deleteLocal(id: String)
        fun getEntityId(entity: RoomT): String
        fun shouldDelete(entity: RoomT): Boolean = false
    }

    private val adapters: List<EntitySyncAdapter<*>> by lazy {
        listOf(
            chatSyncAdapter,
            messageSyncAdapter,
            memoriesSyncAdapter,
            visionAnalysisSyncAdapter,
            messageFileSyncAdapter,
            webSearchSourceSyncAdapter,
            apiModelConfigSyncAdapter,
            localModelConfigSyncAdapter,
            defaultModelSyncAdapter,
            localModelSyncAdapter,
            ttsProviderSyncAdapter,
            mediaProviderSyncAdapter,
            apiCredentialsSyncAdapter,
            ApiKeySyncAdapter(),
            studioAlbumSyncAdapter,
            studioMediaSyncAdapter
        )
    }

    private fun String.encrypt(): Blob = Blob.fromBytes(cryptoManager.encrypt(this).getOrThrow())
    private fun Blob.decrypt(): String? = cryptoManager.decrypt(this.toBytes()).getOrNull()

    private suspend fun ensurePlaceholderMessage(messageId: MessageId, chatId: ChatId) {
        if (chatId.value.isBlank()) return
        if (messageId.value.isBlank()) return
        val chat = chatDao.getChatById(chatId)
        if (chat == null) {
            chatDao.insertIfAbsent(ChatEntity(
                id = chatId,
                name = "Syncing...",
                created = java.util.Date(0),
                lastModified = java.util.Date(0),
                pinned = false,
                updatedAt = 0L,
                deleted = false,
                pendingSync = 0
            ))
        }

        messageDao.insertIfAbsent(
            MessageEntity(
                id = messageId,
                chatId = chatId,
                content = "Syncing...",
                role = Role.USER,
                messageState = MessageState.COMPLETE,
                createdAt = 0L,
                pendingSync = 0
            )
        )
    }

    private val chatSyncAdapter = object : EntitySyncAdapter<ChatEntity> {
        override val collectionPath: String = "chats"
        override suspend fun claimPending() = chatDao.claimPending()
        override suspend fun getClaimedForSync() = chatDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<ChatEntity>) = chatDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: ChatEntity): String = entity.id.value
        
        override suspend fun toFirestore(local: ChatEntity): Map<String, Any?> {
            val encryptedName = cryptoManager.encrypt(local.name).getOrThrow()
            return mapOf(
                "name" to Blob.fromBytes(encryptedName),
                "created_at" to local.created.time,
                "last_modified" to local.lastModified.time,
                "pinned" to local.pinned,
                "updated_at" to local.updatedAt,
                "deleted" to local.deleted
            )
        }

        override fun fromFirestore(doc: DocumentSnapshot): ChatEntity? = chatEntityFromDoc(doc)
        
        override suspend fun upsertLocal(entity: ChatEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val existing = chatDao.getChatById(entity.id)
            if (existing == null || entity.updatedAt >= existing.updatedAt) {
                chatDao.insert(entity.copy(pendingSync = 0)) // 0 = clean
            } else {
                loggingPort.debug(TAG, "Chat ${entity.id.value} local is newer or same age, skipping remote update")
            }
        }

        override suspend fun deleteLocal(id: String) {
            chatDao.softDeleteById(ChatId(id))
        }
    }

    private val messageSyncAdapter = object : EntitySyncAdapter<MessageEntity> {
        override val collectionPath: String = "messages"
        override suspend fun claimPending() = messageDao.claimPending()
        override suspend fun getClaimedForSync() = messageDao.getClaimedForSync().map { it.message }
        override suspend fun markSynced(entities: List<MessageEntity>) = messageDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: MessageEntity): String = entity.id.value

        override suspend fun toFirestore(local: MessageEntity): Map<String, Any?> {
            val encryptedContent = cryptoManager.encrypt(local.content).getOrThrow()
            val msgData = mutableMapOf<String, Any?>(
                "chat_id" to local.chatId.value,
                "content" to Blob.fromBytes(encryptedContent),
                "role" to local.role.name,
                "message_state" to local.messageState.name,
                "updated_at" to local.updatedAt,
                "deleted" to local.deleted
            )
            local.createdAt?.let { msgData["created_at"] = it }
            local.modelType?.let { msgData["model_type"] = it.name }
            return msgData
        }

        override fun fromFirestore(doc: DocumentSnapshot): MessageEntity? = messageEntityFromDoc(doc)

        override suspend fun upsertLocal(entity: MessageEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val existing = messageDao.getMessageById(entity.id)?.message
            if (existing == null || entity.updatedAt >= existing.updatedAt) {
                ensurePlaceholderMessage(entity.id, entity.chatId)
                messageDao.insert(entity.copy(pendingSync = 0))
                
                val hash = entity.content.sha256()
                val existingMeta = embeddingDao.getEmbeddingMetadata(entity.id.value)
                if (existingMeta?.contentHash != hash) {
                    embeddingCoordinator.enqueueMessage(entity.id.value)
                }
            } else {
                loggingPort.debug(TAG, "Message ${entity.id.value} local is newer or same age, skipping remote update")
            }
        }

        override suspend fun deleteLocal(id: String) = messageDao.softDeleteById(MessageId(id))
    }

    private val memoriesSyncAdapter = object : EntitySyncAdapter<MemoriesEntity> {
        override val collectionPath = "memories"
        override suspend fun claimPending() = memoriesDao.claimPending()
        override suspend fun getClaimedForSync() = memoriesDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<MemoriesEntity>) = memoriesDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: MemoriesEntity) = entity.id
        override suspend fun toFirestore(entity: MemoriesEntity) = mapOf(
            "category" to entity.category.name,
            "content" to entity.content.encrypt(),
            "created_at" to entity.createdAt,
            "updated_at" to entity.updatedAt
        )
        override fun fromFirestore(snapshot: DocumentSnapshot): MemoriesEntity? {
            val content = snapshot.getBlob("content")?.decrypt() ?: return null
            return MemoriesEntity(
                id = snapshot.id,
                category = com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory.valueOf(snapshot.getString("category") ?: "CONVERSATION"),
                content = content,
                createdAt = snapshot.getLong("created_at") ?: 0L,
                updatedAt = snapshot.getLong("updated_at") ?: 0L
            )
        }
        override suspend fun upsertLocal(entity: MemoriesEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val existing = memoriesDao.getMemoryById(entity.id)
            if (existing == null || (overwrite && existing != entity.copy(pendingSync = existing.pendingSync))) {
                memoriesDao.insertMemory(entity.copy(pendingSync = 0))
                
                val hash = entity.content.sha256()
                val existingMeta = embeddingDao.getEmbeddingMetadata(entity.id)
                if (existingMeta?.contentHash != hash) {
                    embeddingCoordinator.enqueueMemory(entity.id)
                }
            }
        }
        override suspend fun deleteLocal(id: String) {
            memoriesDao.softDeleteById(id)
        }
    }

    private val visionAnalysisSyncAdapter = object : EntitySyncAdapter<MessageVisionAnalysisEntity> {
        override val collectionPath = "vision_analysis"
        override suspend fun claimPending() = messageVisionAnalysisDao.claimPending()
        override suspend fun getClaimedForSync() = messageVisionAnalysisDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<MessageVisionAnalysisEntity>) = messageVisionAnalysisDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: MessageVisionAnalysisEntity) = entity.id
        override suspend fun toFirestore(entity: MessageVisionAnalysisEntity): Map<String, Any?> {
            val message = messageDao.getMessageById(entity.userMessageId)?.message ?: return emptyMap()
            val chatId = message.chatId.value
            return mapOf(
                "user_message_id" to entity.userMessageId.value,
                "chat_id" to chatId,
                "image_uri" to entity.imageUri,
                "prompt_text" to entity.promptText.encrypt(),
                "analysis_text" to entity.analysisText.encrypt(),
                "model_type" to entity.modelType.name,
                "created_at" to entity.createdAt,
                "updated_at" to entity.updatedAt
            )
        }
        override fun fromFirestore(snapshot: DocumentSnapshot): MessageVisionAnalysisEntity? {
            val chatIdStr = snapshot.getString("chat_id")
            if (chatIdStr.isNullOrBlank()) return null
            val userMessageIdStr = snapshot.getString("user_message_id")
            if (userMessageIdStr.isNullOrBlank()) return null
            val promptText = snapshot.getBlob("prompt_text")?.decrypt() ?: return null
            val analysisText = snapshot.getBlob("analysis_text")?.decrypt() ?: return null
            return MessageVisionAnalysisEntity(
                id = snapshot.id,
                userMessageId = MessageId(userMessageIdStr),
                imageUri = snapshot.getString("image_uri") ?: "",
                promptText = promptText,
                analysisText = analysisText,
                modelType = ModelType.valueOf(snapshot.getString("model_type") ?: "LLM"),
                createdAt = snapshot.getLong("created_at") ?: 0L,
                updatedAt = snapshot.getLong("updated_at") ?: 0L
            )
        }
        override suspend fun upsertLocal(entity: MessageVisionAnalysisEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val chatId = doc.getString("chat_id")?.let { ChatId(it) }
            if (chatId != null) {
                ensurePlaceholderMessage(entity.userMessageId, chatId)
            }
            val existing = messageVisionAnalysisDao.getById(entity.id)
            if (existing == null || (overwrite && existing != entity.copy(pendingSync = existing.pendingSync))) {
                messageVisionAnalysisDao.upsert(entity.copy(pendingSync = 0))
            }
        }
        override suspend fun deleteLocal(id: String) {
            messageVisionAnalysisDao.softDeleteById(id)
        }
    }

    private val messageFileSyncAdapter = object : EntitySyncAdapter<MessageFileEntity> {
        override val collectionPath = "message_files"
        override suspend fun claimPending() = messageFileDao.claimPending()
        override suspend fun getClaimedForSync() = messageFileDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<MessageFileEntity>) = messageFileDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: MessageFileEntity) = entity.id
        override suspend fun toFirestore(entity: MessageFileEntity): Map<String, Any?> {
            val message = messageDao.getMessageById(MessageId(entity.messageId))?.message ?: return emptyMap()
            val chatId = message.chatId.value
            return mapOf(
                "message_id" to entity.messageId,
                "chat_id" to chatId,
                "name" to entity.name.encrypt(),
                "content" to entity.content.encrypt()
            )
        }
        override fun fromFirestore(snapshot: DocumentSnapshot): MessageFileEntity? {
            val chatIdStr = snapshot.getString("chat_id")
            if (chatIdStr.isNullOrBlank()) return null
            val messageIdStr = snapshot.getString("message_id")
            if (messageIdStr.isNullOrBlank()) return null
            val name = snapshot.getBlob("name")?.decrypt() ?: return null
            val content = snapshot.getBlob("content")?.decrypt() ?: return null
            return MessageFileEntity(
                id = snapshot.id,
                messageId = messageIdStr,
                name = name,
                content = content
            )
        }
        override suspend fun upsertLocal(entity: MessageFileEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val chatId = doc.getString("chat_id")?.let { ChatId(it) }
            if (chatId != null) {
                ensurePlaceholderMessage(MessageId(entity.messageId), chatId)
            }
            val existing = messageFileDao.getById(entity.id)
            if (existing == null || (overwrite && existing != entity.copy(pendingSync = existing.pendingSync))) {
                messageFileDao.insert(entity.copy(pendingSync = 0))
            }
        }
        override suspend fun deleteLocal(id: String) {
            messageFileDao.softDeleteById(id)
        }
    }

    private val webSearchSourceSyncAdapter = object : EntitySyncAdapter<WebSearchSourceEntity> {
        override val collectionPath = "web_search_sources"
        override suspend fun claimPending() = webSearchSourceDao.claimPending()
        override suspend fun getClaimedForSync() = webSearchSourceDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<WebSearchSourceEntity>) = webSearchSourceDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: WebSearchSourceEntity) = entity.id
        override suspend fun toFirestore(entity: WebSearchSourceEntity): Map<String, Any?> {
            val message = messageDao.getMessageById(entity.messageId)?.message ?: return emptyMap()
            val chatId = message.chatId.value
            return mapOf(
                "message_id" to entity.messageId.value,
                "chat_id" to chatId,
                "title" to entity.title.encrypt(),
                "url" to entity.url.encrypt(),
                "content" to entity.content.encrypt(),
                "score" to entity.score,
                "extracted" to entity.extracted
            )
        }
        override fun fromFirestore(snapshot: DocumentSnapshot): WebSearchSourceEntity? {
            val chatIdStr = snapshot.getString("chat_id")
            if (chatIdStr.isNullOrBlank()) return null
            val messageIdStr = snapshot.getString("message_id")
            if (messageIdStr.isNullOrBlank()) return null
            val title = snapshot.getBlob("title")?.decrypt() ?: return null
            val url = snapshot.getBlob("url")?.decrypt() ?: return null
            val content = snapshot.getBlob("content")?.decrypt() ?: return null
            return WebSearchSourceEntity(
                id = snapshot.id,
                messageId = MessageId(messageIdStr),
                title = title,
                url = url,
                content = content,
                score = snapshot.getDouble("score") ?: 0.0,
                extracted = snapshot.getBoolean("extracted") ?: false
            )
        }
        override suspend fun upsertLocal(entity: WebSearchSourceEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val chatId = doc.getString("chat_id")?.let { ChatId(it) }
            if (chatId != null) {
                ensurePlaceholderMessage(entity.messageId, chatId)
            }
            val existing = webSearchSourceDao.getById(entity.id)
            if (existing == null || (overwrite && existing != entity.copy(pendingSync = existing.pendingSync))) {
                webSearchSourceDao.upsert(entity.copy(pendingSync = 0))
            }
        }
        override suspend fun deleteLocal(id: String) {
            webSearchSourceDao.softDeleteById(id)
        }
    }

    private val apiModelConfigSyncAdapter = object : EntitySyncAdapter<ApiModelConfigurationEntity> {
        override val collectionPath = "api_model_configs"
        override suspend fun claimPending() = apiModelConfigurationsDao.claimPending()
        override suspend fun getClaimedForSync() = apiModelConfigurationsDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<ApiModelConfigurationEntity>) = apiModelConfigurationsDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: ApiModelConfigurationEntity) = entity.id.value
        override suspend fun toFirestore(entity: ApiModelConfigurationEntity) = mapOf(
            "api_credentials_id" to entity.apiCredentialsId.value,
            "display_name" to entity.displayName,
            "custom_headers" to entity.customHeaders,
            "max_tokens" to entity.maxTokens,
            "context_window" to entity.contextWindow,
            "temperature" to entity.temperature,
            "top_p" to entity.topP,
            "top_k" to entity.topK,
            "min_p" to entity.minP,
            "frequency_penalty" to entity.frequencyPenalty,
            "presence_penalty" to entity.presencePenalty,
            "system_prompt" to entity.systemPrompt,
            "max_tokens_disabled" to entity.maxTokensDisabled,
            "temperature_disabled" to entity.temperatureDisabled,
            "top_p_disabled" to entity.topPDisabled,
            "top_k_disabled" to entity.topKDisabled,
            "min_p_disabled" to entity.minPDisabled,
            "frequency_penalty_disabled" to entity.frequencyPenaltyDisabled,
            "presence_penalty_disabled" to entity.presencePenaltyDisabled,
            "reasoning_effort" to entity.reasoningEffort,
            "updated_at" to entity.updatedAt
        )
        override fun fromFirestore(snapshot: DocumentSnapshot) = ApiModelConfigurationEntity(
            id = ApiModelConfigurationId(snapshot.id),
            apiCredentialsId = ApiCredentialsId(snapshot.getString("api_credentials_id") ?: ""),
            displayName = snapshot.getString("display_name") ?: "",
            customHeaders = snapshot.getString("custom_headers") ?: "{}",
            maxTokens = snapshot.getLong("max_tokens")?.toInt() ?: 4096,
            contextWindow = snapshot.getLong("context_window")?.toInt() ?: 4096,
            temperature = snapshot.getDouble("temperature") ?: 0.7,
            topP = snapshot.getDouble("top_p") ?: 0.95,
            topK = snapshot.getLong("top_k")?.toInt(),
            minP = snapshot.getDouble("min_p") ?: 0.05,
            frequencyPenalty = snapshot.getDouble("frequency_penalty") ?: 0.0,
            presencePenalty = snapshot.getDouble("presence_penalty") ?: 0.0,
            systemPrompt = snapshot.getString("system_prompt") ?: "",
            maxTokensDisabled = snapshot.getBoolean("max_tokens_disabled") ?: false,
            temperatureDisabled = snapshot.getBoolean("temperature_disabled") ?: false,
            topPDisabled = snapshot.getBoolean("top_p_disabled") ?: false,
            topKDisabled = snapshot.getBoolean("top_k_disabled") ?: false,
            minPDisabled = snapshot.getBoolean("min_p_disabled") ?: false,
            frequencyPenaltyDisabled = snapshot.getBoolean("frequency_penalty_disabled") ?: false,
            presencePenaltyDisabled = snapshot.getBoolean("presence_penalty_disabled") ?: false,
            reasoningEffort = snapshot.getString("reasoning_effort"),
            updatedAt = snapshot.getLong("updated_at") ?: 0L
        )
        override suspend fun upsertLocal(entity: ApiModelConfigurationEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val existing = apiModelConfigurationsDao.getById(entity.id)
            if (existing == null || (overwrite && existing != entity.copy(pendingSync = existing.pendingSync))) {
                apiModelConfigurationsDao.upsert(entity.copy(pendingSync = 0))
            }
        }
        override suspend fun deleteLocal(id: String) {
            apiModelConfigurationsDao.softDeleteById(ApiModelConfigurationId(id))
        }
    }

    private val localModelConfigSyncAdapter = object : EntitySyncAdapter<LocalModelConfigurationEntity> {
        override val collectionPath = "local_model_configs"
        override suspend fun claimPending() = localModelConfigurationsDao.claimPending()
        override suspend fun getClaimedForSync() = localModelConfigurationsDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<LocalModelConfigurationEntity>) = localModelConfigurationsDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: LocalModelConfigurationEntity) = entity.id.value
        override suspend fun toFirestore(entity: LocalModelConfigurationEntity) = mapOf(
            "local_model_id" to entity.localModelId.value,
            "display_name" to entity.displayName,
            "temperature" to entity.temperature,
            "top_k" to entity.topK,
            "top_p" to entity.topP,
            "min_p" to entity.minP,
            "repetition_penalty" to entity.repetitionPenalty,
            "max_tokens" to entity.maxTokens,
            "context_window" to entity.contextWindow,
            "thinking_enabled" to entity.thinkingEnabled,
            "system_prompt" to entity.systemPrompt,
            "updated_at" to entity.updatedAt
        )
        override fun fromFirestore(snapshot: DocumentSnapshot) = LocalModelConfigurationEntity(
            id = LocalModelConfigurationId(snapshot.id),
            localModelId = LocalModelId(snapshot.getString("local_model_id") ?: ""),
            displayName = snapshot.getString("display_name") ?: "",
            temperature = snapshot.getDouble("temperature") ?: 0.7,
            topK = snapshot.getLong("top_k")?.toInt() ?: 40,
            topP = snapshot.getDouble("top_p") ?: 0.95,
            minP = snapshot.getDouble("min_p") ?: 0.05,
            repetitionPenalty = snapshot.getDouble("repetition_penalty") ?: 1.1,
            maxTokens = snapshot.getLong("max_tokens")?.toInt() ?: 4096,
            contextWindow = snapshot.getLong("context_window")?.toInt() ?: 4096,
            thinkingEnabled = snapshot.getBoolean("thinking_enabled") ?: false,
            systemPrompt = snapshot.getString("system_prompt"),
            updatedAt = snapshot.getLong("updated_at") ?: 0L
        )
        override suspend fun upsertLocal(entity: LocalModelConfigurationEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val existingById = localModelConfigurationsDao.getById(entity.id)
            val existingByKey = localModelConfigurationsDao.getByModelIdAndName(entity.localModelId, entity.displayName)
            val existing = existingById ?: existingByKey
            
            val newEntity = entity.copy(id = existing?.id ?: entity.id, pendingSync = 0)
            if (existing == null || existing.id != entity.id || (overwrite && existing != newEntity)) {
                localModelConfigurationsDao.upsert(newEntity)
            }
        }
        override suspend fun deleteLocal(id: String) {
            localModelConfigurationsDao.softDeleteById(LocalModelConfigurationId(id))
        }
    }

    private val defaultModelSyncAdapter = object : EntitySyncAdapter<DefaultModelEntity> {
        override val collectionPath = "default_models"
        override suspend fun claimPending() = defaultModelsDao.claimPending()
        override suspend fun getClaimedForSync() = defaultModelsDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<DefaultModelEntity>) = defaultModelsDao.markSynced(entities.map { it.modelType })
        override fun getEntityId(entity: DefaultModelEntity) = entity.modelType.name
        override suspend fun toFirestore(entity: DefaultModelEntity) = mapOf(
            "local_config_id" to entity.localConfigId?.value,
            "api_config_id" to entity.apiConfigId?.value,
            "tts_provider_id" to entity.ttsProviderId?.value,
            "media_provider_id" to entity.mediaProviderId?.value,
            "updated_at" to entity.updatedAt
        )
        override fun fromFirestore(snapshot: DocumentSnapshot) = DefaultModelEntity(
            modelType = ModelType.valueOf(snapshot.id),
            localConfigId = snapshot.getString("local_config_id")?.let { LocalModelConfigurationId(it) },
            apiConfigId = snapshot.getString("api_config_id")?.let { ApiModelConfigurationId(it) },
            ttsProviderId = snapshot.getString("tts_provider_id")?.let { TtsProviderId(it) },
            mediaProviderId = snapshot.getString("media_provider_id")?.let { MediaProviderId(it) },
            updatedAt = snapshot.getLong("updated_at") ?: 0L
        )
        override suspend fun upsertLocal(entity: DefaultModelEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val existing = defaultModelsDao.getDefault(entity.modelType)
            if (existing == null || (overwrite && existing != entity.copy(pendingSync = existing.pendingSync))) {
                defaultModelsDao.upsert(entity.copy(pendingSync = 0))
            }
        }
        override suspend fun deleteLocal(id: String) {
            defaultModelsDao.softDeleteById(ModelType.valueOf(id))
        }
    }

    private val localModelSyncAdapter = object : EntitySyncAdapter<LocalModelEntity> {
        override val collectionPath = "local_models"
        override suspend fun claimPending() = localModelsDao.claimPending()
        override suspend fun getClaimedForSync() = localModelsDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<LocalModelEntity>) = localModelsDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: LocalModelEntity) = entity.id.value
        override suspend fun toFirestore(entity: LocalModelEntity) = mapOf(
            "model_file_format" to entity.modelFileFormat.name,
            "huggingface_model_name" to entity.huggingFaceModelName,
            "remote_filename" to entity.remoteFilename,
            "local_filename" to entity.localFilename,
            "sha256" to entity.sha256,
            "size_in_bytes" to entity.sizeInBytes,
            "is_multimodal" to entity.isMultimodal,
            "mmproj_remote_filename" to entity.mmprojRemoteFilename,
            "mmproj_local_filename" to entity.mmprojLocalFilename,
            "mmproj_sha256" to entity.mmprojSha256,
            "mmproj_size_in_bytes" to entity.mmprojSizeInBytes,
            "updated_at" to entity.updatedAt
        )
        override fun fromFirestore(snapshot: DocumentSnapshot) = LocalModelEntity(
            id = LocalModelId(snapshot.id),
            modelFileFormat = ModelFileFormat.valueOf(snapshot.getString("model_file_format") ?: "GGUF"),
            huggingFaceModelName = snapshot.getString("huggingface_model_name") ?: "",
            remoteFilename = snapshot.getString("remote_filename") ?: "",
            localFilename = snapshot.getString("local_filename") ?: "",
            sha256 = snapshot.getString("sha256") ?: "",
            sizeInBytes = snapshot.getLong("size_in_bytes") ?: 0L,
            isMultimodal = snapshot.getBoolean("is_multimodal") ?: false,
            mmprojRemoteFilename = snapshot.getString("mmproj_remote_filename"),
            mmprojLocalFilename = snapshot.getString("mmproj_local_filename"),
            mmprojSha256 = snapshot.getString("mmproj_sha256"),
            mmprojSizeInBytes = snapshot.getLong("mmproj_size_in_bytes"),
            updatedAt = snapshot.getLong("updated_at") ?: 0L
        )
        override suspend fun upsertLocal(entity: LocalModelEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val existingById = localModelsDao.getById(entity.id)
            val existingByKey = localModelsDao.getBySha256(entity.sha256)
            val existing = existingById ?: existingByKey

            val newEntity = entity.copy(id = existing?.id ?: entity.id, pendingSync = 0)
            if (existing == null || existing.id != entity.id || (overwrite && existing != newEntity)) {
                localModelsDao.upsert(newEntity)
            }
        }
        override suspend fun deleteLocal(id: String) {
            localModelsDao.softDeleteById(LocalModelId(id))
        }
    }

    private val ttsProviderSyncAdapter = object : EntitySyncAdapter<TtsProviderEntity> {
        override val collectionPath = "tts_providers"
        override suspend fun claimPending() = ttsProviderDao.claimPending()
        override suspend fun getClaimedForSync() = ttsProviderDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<TtsProviderEntity>) = ttsProviderDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: TtsProviderEntity) = entity.id
        override suspend fun toFirestore(entity: TtsProviderEntity) = mapOf(
            "display_name" to entity.displayName,
            "provider" to entity.provider.name,
            "voice_name" to entity.voiceName,
            "credential_alias" to entity.credentialAlias
        )
        override fun fromFirestore(snapshot: DocumentSnapshot) = TtsProviderEntity(
            id = snapshot.id,
            displayName = snapshot.getString("display_name") ?: "",
            provider = com.browntowndev.pocketcrew.domain.model.inference.ApiProvider.valueOf(snapshot.getString("provider") ?: "OPENAI"),
            voiceName = snapshot.getString("voice_name") ?: "",
            credentialAlias = snapshot.getString("credential_alias") ?: ""
        )
        override suspend fun upsertLocal(entity: TtsProviderEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val existing = ttsProviderDao.getTtsProvider(entity.id)
            if (existing == null || (overwrite && existing != entity.copy(pendingSync = existing.pendingSync))) {
                ttsProviderDao.upsertTtsProvider(entity.copy(pendingSync = 0))
            }
        }
        override suspend fun deleteLocal(id: String) {
            ttsProviderDao.softDeleteById(id)
        }
    }

    private val mediaProviderSyncAdapter = object : EntitySyncAdapter<MediaProviderEntity> {
        override val collectionPath = "media_providers"
        override suspend fun claimPending() = mediaProviderDao.claimPending()
        override suspend fun getClaimedForSync() = mediaProviderDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<MediaProviderEntity>) = mediaProviderDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: MediaProviderEntity) = entity.id
        override suspend fun toFirestore(entity: MediaProviderEntity) = mapOf(
            "display_name" to entity.displayName,
            "provider" to entity.provider.name,
            "capability" to entity.capability.name,
            "credential_alias" to entity.credentialAlias,
            "updated_at" to entity.updatedAt
        )
        override fun fromFirestore(snapshot: DocumentSnapshot) = MediaProviderEntity(
            id = snapshot.id,
            displayName = snapshot.getString("display_name") ?: "",
            provider = com.browntowndev.pocketcrew.domain.model.inference.ApiProvider.valueOf(snapshot.getString("provider") ?: "OPENAI"),
            capability = com.browntowndev.pocketcrew.domain.model.config.MediaCapability.valueOf(snapshot.getString("capability") ?: "IMAGE_GENERATION"),
            credentialAlias = snapshot.getString("credential_alias") ?: "",
            updatedAt = snapshot.getLong("updated_at") ?: 0L
        )
        override suspend fun upsertLocal(entity: MediaProviderEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val existing = mediaProviderDao.getMediaProvider(entity.id)
            if (existing == null || (overwrite && existing != entity.copy(pendingSync = existing.pendingSync))) {
                mediaProviderDao.upsert(entity.copy(pendingSync = 0))
            }
        }
        override suspend fun deleteLocal(id: String) {
            mediaProviderDao.softDeleteById(id)
        }
    }

    private val apiCredentialsSyncAdapter = object : EntitySyncAdapter<ApiCredentialsEntity> {
        override val collectionPath = "api_credentials"
        override suspend fun claimPending() = apiCredentialsDao.claimPending()
        override suspend fun getClaimedForSync() = apiCredentialsDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<ApiCredentialsEntity>) = apiCredentialsDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: ApiCredentialsEntity) = entity.id.value
        override suspend fun toFirestore(entity: ApiCredentialsEntity) = mapOf(
            "display_name" to entity.displayName,
            "provider" to entity.provider.name,
            "base_url" to entity.baseUrl,
            "model_id" to entity.modelId,
            "is_multimodal" to entity.isMultimodal,
            "credential_alias" to entity.credentialAlias,
            "encrypted_api_key" to (entity.encryptedApiKey?.let { Blob.fromBytes(it) }),
            "api_key_signature" to entity.apiKeySignature,
            "created_at" to entity.createdAt,
            "updated_at" to entity.updatedAt
        )
        override fun fromFirestore(snapshot: DocumentSnapshot) = ApiCredentialsEntity(
            id = ApiCredentialsId(snapshot.id),
            displayName = snapshot.getString("display_name") ?: "",
            provider = com.browntowndev.pocketcrew.domain.model.inference.ApiProvider.valueOf(snapshot.getString("provider") ?: "OPENAI"),
            baseUrl = snapshot.getString("base_url"),
            modelId = snapshot.getString("model_id") ?: "",
            isMultimodal = snapshot.getBoolean("is_multimodal") ?: false,
            credentialAlias = snapshot.getString("credential_alias") ?: "",
            encryptedApiKey = snapshot.getBlob("encrypted_api_key")?.toBytes(),
            apiKeySignature = snapshot.getString("api_key_signature"),
            createdAt = snapshot.getLong("created_at") ?: 0L,
            updatedAt = snapshot.getLong("updated_at") ?: 0L
        )
        override suspend fun upsertLocal(entity: ApiCredentialsEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val existing = apiCredentialsDao.getById(entity.id)
            if (existing == null || (overwrite && existing != entity.copy(pendingSync = existing.pendingSync))) {
                apiCredentialsDao.upsert(entity.copy(pendingSync = 0))
            }
        }
        override suspend fun deleteLocal(id: String) {
            apiCredentialsDao.softDeleteById(ApiCredentialsId(id))
        }
    }

    private val studioAlbumSyncAdapter = object : EntitySyncAdapter<StudioAlbumEntity> {
        override val collectionPath = "studio_albums"
        override suspend fun claimPending() = studioAlbumDao.claimPending()
        override suspend fun getClaimedForSync() = studioAlbumDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<StudioAlbumEntity>) = studioAlbumDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: StudioAlbumEntity) = entity.id
        override suspend fun toFirestore(entity: StudioAlbumEntity) = mapOf(
            "name" to entity.name,
            "updated_at" to System.currentTimeMillis()
        )
        override fun fromFirestore(snapshot: DocumentSnapshot) = StudioAlbumEntity(
            id = snapshot.id,
            name = snapshot.getString("name") ?: "",
            pendingSync = 0
        )
        override suspend fun upsertLocal(entity: StudioAlbumEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val existing = studioAlbumDao.getAlbumById(entity.id)
            if (existing == null || overwrite) {
                studioAlbumDao.insertAlbum(entity.copy(pendingSync = 0))
            }
        }
        override suspend fun deleteLocal(id: String) {
            studioAlbumDao.softDeleteById(id)
        }
    }

    private val studioMediaSyncAdapter = object : EntitySyncAdapter<StudioMediaEntity> {
        override val collectionPath = "studio_gallery"
        override suspend fun claimPending() = studioMediaDao.claimPending()
        override suspend fun getClaimedForSync() = studioMediaDao.getClaimedForSync()
        override suspend fun markSynced(entities: List<StudioMediaEntity>) = studioMediaDao.markSynced(entities.map { it.id })
        override fun getEntityId(entity: StudioMediaEntity) = entity.id
        override suspend fun toFirestore(entity: StudioMediaEntity) = mapOf(
            "prompt" to entity.prompt,
            "media_type" to entity.mediaType,
            "created_at" to entity.createdAt,
            "album_id" to entity.albumId,
            "remote_url" to entity.remoteUrl,
            "remote_thumbnail_url" to entity.remoteThumbnailUrl,
            "updated_at" to System.currentTimeMillis()
        )
        override fun fromFirestore(snapshot: DocumentSnapshot) = StudioMediaEntity(
            id = snapshot.id,
            prompt = snapshot.getString("prompt") ?: "",
            mediaUri = snapshot.getString("remote_url") ?: "", 
            mediaType = snapshot.getString("media_type") ?: "IMAGE",
            createdAt = snapshot.getLong("created_at") ?: System.currentTimeMillis(),
            albumId = snapshot.getString("album_id"),
            remoteUrl = snapshot.getString("remote_url"),
            remoteThumbnailUrl = snapshot.getString("remote_thumbnail_url"),
            syncStatus = if (snapshot.getString("remote_url") != null) "UPLOADED" else "PENDING_UPLOAD",
            pendingSync = 0
        )
        override suspend fun upsertLocal(entity: StudioMediaEntity, doc: DocumentSnapshot, overwrite: Boolean) {
            val existing = studioMediaDao.getMediaById(entity.id)
            if (existing == null || overwrite) {
                studioMediaDao.insertMedia(entity.copy(pendingSync = 0))
            }
        }
        override suspend fun deleteLocal(id: String) {
            studioMediaDao.softDeleteById(id)
        }
    }

    private inner class ApiKeySyncAdapter : EntitySyncAdapter<Pair<String, String>> {
        override val collectionPath: String = "api_keys"

        override suspend fun claimPending() {
            apiKeyManager.claimPending()
        }

        override suspend fun getClaimedForSync(): List<Pair<String, String>> {
            val syncAliases = apiKeyManager.getClaimedSyncAliases()
            val deleteAliases = apiKeyManager.getClaimedDeleteAliases()
            
            val syncList = syncAliases.mapNotNull { alias ->
                apiKeyManager.get(alias)?.let { key -> alias to key }
            }
            val deleteList = deleteAliases.map { it to "DELETE_SENTINEL" }
            
            return syncList + deleteList
        }

        override suspend fun markSynced(entities: List<Pair<String, String>>) {
            val (toDelete, toSync) = entities.partition { it.second == "DELETE_SENTINEL" }
            apiKeyManager.clearClaimedSync(toSync.map { it.first })
            apiKeyManager.clearClaimedDelete(toDelete.map { it.first })
        }

        override fun getEntityId(entity: Pair<String, String>) = entity.first
        override fun shouldDelete(entity: Pair<String, String>): Boolean = entity.second == "DELETE_SENTINEL"

        override suspend fun toFirestore(local: Pair<String, String>): Map<String, Any?> {
            val (alias, key) = local
            if (key == "DELETE_SENTINEL") return emptyMap()
            
            val encrypted = cryptoManager.encrypt(key).getOrThrow()
            return mapOf(
                "alias" to alias,
                "encrypted_key" to Blob.fromBytes(encrypted),
                "updated_at" to com.google.firebase.Timestamp.now()
            )
        }

        override fun fromFirestore(doc: DocumentSnapshot): Pair<String, String>? {
            val alias = doc.id
            val blob = doc.getBlob("encrypted_key") ?: return null
            val decrypted = cryptoManager.decrypt(blob.toBytes()).getOrNull() ?: return null
            return alias to decrypted
        }

        override suspend fun upsertLocal(entity: Pair<String, String>, doc: DocumentSnapshot, overwrite: Boolean) {
            apiKeyManager.saveFromRemote(entity.first, entity.second)
        }

        override suspend fun deleteLocal(id: String) {
            apiKeyManager.deleteFromRemote(id)
        }
    }

    override fun startListening() {
        externalScope.launch {
            listenerMutex.withLock {
                if (listenerRegistrations.isNotEmpty()) {
                    loggingPort.debug(TAG, "startListening: Already listening, skipping")
                    return@withLock
                }

                if (!cryptoManager.hasCachedKey()) {
                    loggingPort.debug(TAG, "startListening: Vault is locked, skipping")
                    return@withLock
                }

                val userId = userIdProvider() ?: return@withLock
                loggingPort.debug(TAG, "startListening: Starting listeners for UID: $userId")

                adapters.forEach { adapter ->
                    val listener = firestore.collection("users/$userId/${adapter.collectionPath}")
                        .addSnapshotListener { snapshots, e ->
                            if (e != null) {
                                loggingPort.error(TAG, "${adapter.collectionPath} listener error", e)
                                return@addSnapshotListener
                            }
                            if (snapshots == null) return@addSnapshotListener

                            externalScope.launch {
                                try {
                                    for (dc in snapshots.documentChanges) {
                                        processDocumentChange(adapter, dc)
                                    }
                                } catch (e: Exception) {
                                    loggingPort.error(TAG, "Error processing ${adapter.collectionPath} snapshot", e)
                                }
                            }
                        }
                    listenerRegistrations.add(listener)
                }
                loggingPort.debug(TAG, "startListening: All listeners started")
            }
        }
    }

    private suspend fun <T> processDocumentChange(adapter: EntitySyncAdapter<T>, dc: DocumentChange) {
        val doc = dc.document
        when (dc.type) {
            DocumentChange.Type.ADDED -> {
                adapter.fromFirestore(doc)?.let { entity ->
                    adapter.upsertLocal(entity, doc, overwrite = false)
                }
            }
            DocumentChange.Type.MODIFIED -> {
                adapter.fromFirestore(doc)?.let { entity ->
                    adapter.upsertLocal(entity, doc, overwrite = true)
                }
            }
            DocumentChange.Type.REMOVED -> {
                adapter.deleteLocal(doc.id)
            }
        }
    }

    override fun stopListening() {
        externalScope.launch {
            listenerMutex.withLock {
                listenerRegistrations.forEach { it.remove() }
                listenerRegistrations.clear()
            }
        }
    }

    override suspend fun syncAllLocalDataToCloud(): Result<Unit> {
        val userId = userIdProvider() ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!cryptoManager.hasCachedKey()) return Result.failure(IllegalStateException("Vault locked"))

        return try {
            adapters.forEach { it.claimPending() }
            syncPendingChanges()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncEntitiesToCloud(
        chats: List<Chat>,
        messages: List<Message>
    ): Result<Unit> {
        val userId = userIdProvider() ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!cryptoManager.hasCachedKey()) return Result.failure(IllegalStateException("Vault locked"))

        return try {
            val allEntities = mutableListOf<Pair<String, Map<String, Any?>>>()
            chats.forEach { chat ->
                if (chat.id.value.isBlank()) {
                    loggingPort.error(TAG, "Empty chat ID in syncEntitiesToCloud. Skipping.")
                    return@forEach
                }
                val data = mapOf(
                    "name" to chat.name.encrypt(),
                    "created_at" to chat.created.time,
                    "last_modified" to chat.lastModified.time,
                    "pinned" to chat.pinned,
                    "updated_at" to System.currentTimeMillis()
                )
                allEntities.add("users/$userId/chats/${chat.id.value}" to data)
            }
            messages.forEach { msg ->
                if (msg.id.value.isBlank()) {
                    loggingPort.error(TAG, "Empty message ID in syncEntitiesToCloud. Skipping.")
                    return@forEach
                }
                val msgChatId = msg.chatId
                if (msgChatId == null) {
                    loggingPort.error(TAG, "Null chat ID for message ${msg.id.value} in syncEntitiesToCloud. Skipping.")
                    return@forEach
                }
                val data = mutableMapOf<String, Any?>(
                    "chat_id" to msgChatId.value,
                    "content" to msg.content.text.encrypt(),
                    "role" to msg.role.name,
                    "message_state" to msg.messageState.name,
                    "created_at" to msg.createdAt,
                    "model_type" to msg.modelType?.name,
                    "updated_at" to System.currentTimeMillis()
                )
                allEntities.add("users/$userId/messages/${msg.id.value}" to data)
            }

            if (allEntities.isNotEmpty()) {
                allEntities.chunked(500).forEach { batchItems ->
                    firestore.runBatch { batch ->
                        batchItems.forEach { (path, data) ->
                            batch.set(firestore.document(path), data, SetOptions.merge())
                        }
                    }.await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            loggingPort.error(TAG, "syncEntitiesToCloud failed", e)
            Result.failure(e)
        }
    }

    override suspend fun syncPendingChanges(): Result<Unit> {
        val userId = userIdProvider() ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!cryptoManager.hasCachedKey()) return Result.failure(IllegalStateException("Vault locked"))

        return try {
            for (adapter in adapters) {
                syncAdapterPendingChanges(adapter, userId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            loggingPort.error(TAG, "syncPendingChanges failed", e)
            Result.failure(e)
        }
    }

    private suspend fun <T> syncAdapterPendingChanges(adapter: EntitySyncAdapter<T>, userId: String) {
        adapter.claimPending()
        val pending = adapter.getClaimedForSync()
        if (pending.isNotEmpty()) {
            loggingPort.debug(TAG, "Syncing ${pending.size} pending changes for ${adapter.collectionPath}")
            pending.chunked(500).forEach { batchItems ->
                val upserts = batchItems.filter { !adapter.shouldDelete(it) }
                    .map { entity -> entity to adapter.toFirestore(entity) }
                val deletes = batchItems.filter { adapter.shouldDelete(it) }
                
                firestore.runBatch { batch ->
                    upserts.forEach { (entity, data) ->
                        if (data.isNotEmpty()) {
                            val entityId = adapter.getEntityId(entity)
                            if (entityId.isBlank()) {
                                loggingPort.error(TAG, "Empty entity ID for ${adapter.collectionPath} in syncAdapterPendingChanges. Skipping.")
                                return@forEach
                            }
                            val path = "users/$userId/${adapter.collectionPath}/$entityId"
                            batch.set(firestore.document(path), data, SetOptions.merge())
                        }
                    }
                    deletes.forEach { entity ->
                        val entityId = adapter.getEntityId(entity)
                        if (entityId.isBlank()) {
                            loggingPort.error(TAG, "Empty entity ID for ${adapter.collectionPath} in syncAdapterPendingChanges. Skipping.")
                            return@forEach
                        }
                        val path = "users/$userId/${adapter.collectionPath}/$entityId"
                        batch.delete(firestore.document(path))
                    }
                }.await()
                adapter.markSynced(batchItems)
            }
        }
    }

    override suspend fun updateVaultSalt(salt: String): Result<Unit> {
        val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
        return try {
            firestore.document("users/$userId")
                .set(mapOf("vaultSalt" to salt), SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVaultSalt(): Result<String?> {
        val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
        return try {
            val snapshot = firestore.document("users/$userId").get().await()
            if (snapshot.exists()) {
                Result.success(snapshot.getString("vaultSalt"))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateVaultVerificationData(ciphertext: String): Result<Unit> {
        val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
        return try {
            firestore.document("users/$userId")
                .set(mapOf("vaultVerification" to ciphertext), SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVaultVerificationData(): Result<String?> {
        val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
        return try {
            val snapshot = firestore.document("users/$userId").get().await()
            if (snapshot.exists()) {
                Result.success(snapshot.getString("vaultVerification"))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun enqueueEmbeddingRegeneration() {
        embeddingCoordinator.enqueueFullRegeneration()
    }

    private fun String.sha256(): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun chatEntityFromDoc(doc: DocumentSnapshot): ChatEntity? {
        val nameBlob = doc.getBlob("name")
        val name = nameBlob?.decrypt() ?: ""
        return ChatEntity(
            id = ChatId(doc.id),
            name = name,
            created = java.util.Date(doc.getLong("created_at") ?: 0L),
            lastModified = java.util.Date(doc.getLong("last_modified") ?: 0L),
            pinned = doc.getBoolean("pinned") ?: false,
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
            pendingSync = 0
        )
    }

    private fun messageEntityFromDoc(doc: DocumentSnapshot): MessageEntity? {
        val chatIdStr = doc.getString("chat_id")
        if (chatIdStr.isNullOrBlank()) return null
        val contentBlob = doc.getBlob("content")
        val content = contentBlob?.decrypt() ?: ""
        return MessageEntity(
            id = MessageId(doc.id),
            chatId = ChatId(chatIdStr),
            content = content,
            role = Role.fromApiValue(doc.getString("role") ?: "user"),
            messageState = doc.getString("message_state")?.uppercase()?.let { 
                try { MessageState.valueOf(it) } catch(e: Exception) { MessageState.COMPLETE }
            } ?: MessageState.COMPLETE,
            createdAt = doc.getLong("created_at") ?: 0L,
            modelType = doc.getString("model_type")?.let { ModelType.fromApiValue(it) },
            updatedAt = doc.getLong("updated_at") ?: 0L,
            deleted = doc.getBoolean("deleted") ?: false,
            pendingSync = 0
        )
    }
}
