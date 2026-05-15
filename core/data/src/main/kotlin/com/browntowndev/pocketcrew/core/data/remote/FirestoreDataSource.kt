package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryDao
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.DocumentEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.local.MediaProviderDao
import com.browntowndev.pocketcrew.core.data.local.MediaProviderEntity
import com.browntowndev.pocketcrew.core.data.local.MemoriesDao
import com.browntowndev.pocketcrew.core.data.local.MemoriesEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageFileDao
import com.browntowndev.pocketcrew.core.data.local.MessageFileEntity
import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptSegmentEntity
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisEntity
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumDao
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumEntity
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaEntity
import com.browntowndev.pocketcrew.core.data.local.TtsProviderDao
import com.browntowndev.pocketcrew.core.data.local.TtsProviderEntity
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceEntity
import com.browntowndev.pocketcrew.core.data.security.AadContext
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.security.StaticSensitiveFieldCatalog
import com.browntowndev.pocketcrew.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.security.VaultMetadata
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.qualifier.ApplicationScope
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore ↔ Room sync for opted-in entities.
 *
 * **Transcript segments:** Synced under `message_transcript_segments` with encrypted `text`,
 * mirroring the same pending_sync / tombstone pattern as [WebSearchSourceEntity].
 */
@Singleton
@Suppress("LongParameterList", "LargeClass", "TooManyFunctions", "MaxLineLength", "SwallowedException", "ReturnCount")
class FirestoreDataSource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val chatDao: ChatDao,
        private val messageDao: MessageDao,
        private val messageTranscriptSegmentDao: MessageTranscriptSegmentDao,
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
        private val chatSummaryDao: ChatSummaryDao,
        private val documentDao: com.browntowndev.pocketcrew.core.data.local.DocumentDao,
        private val embeddingDao: com.browntowndev.pocketcrew.core.data.local.EmbeddingDao,
        private val apiKeyManager: com.browntowndev.pocketcrew.core.data.security.ApiKeyManager,
        private val cryptoManager: CryptoManagerPort,
        private val loggingPort: LoggingPort,
        @param:ApplicationScope private val externalScope: CoroutineScope,
        private val userIdProvider: () -> String?,
        private val embeddingCoordinator: EmbeddingRegenerationCoordinator,
        private val localEncryption: LocalDataEncryptionService,
        private val sensitiveSelfHealer: SensitivePersistenceSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort),
        private val firestoreSensitiveFieldRepairer: FirestoreSensitiveFieldRepairer =
            FirestoreSensitiveFieldRepairer(StaticSensitiveFieldCatalog(), sensitiveSelfHealer),
    ) : CloudSyncPort {
        companion object {
            private const val TAG = "FirestoreDataSource"
            private const val FIRESTORE_DELETE_PAGE_SIZE = 400L
        }

        private val listenerRegistrations = mutableListOf<ListenerRegistration>()
        private val listenerMutex = Mutex()
        private val syncMutex = Mutex()

        private interface EntitySyncAdapter<RoomT> {
            val collectionPath: String

            suspend fun claimPending()

            suspend fun getClaimedForSync(): List<RoomT>

            suspend fun markSynced(entities: List<RoomT>)

            suspend fun toFirestore(local: RoomT): Map<String, Any?>

            fun fromFirestore(doc: DocumentSnapshot): RoomT?

            suspend fun upsertLocal(
                entity: RoomT,
                doc: DocumentSnapshot,
                overwrite: Boolean = false,
            )

            suspend fun deleteLocal(id: String)

            fun getEntityId(entity: RoomT): String

            fun shouldDelete(entity: RoomT): Boolean = false
        }

        private val adapters: List<EntitySyncAdapter<*>> by lazy {
            listOf(
                chatSyncAdapter,
                messageSyncAdapter,
                chatSummarySyncAdapter,
                memoriesSyncAdapter,
                visionAnalysisSyncAdapter,
                messageFileSyncAdapter,
                webSearchSourceSyncAdapter,
                messageTranscriptSegmentSyncAdapter,
                apiModelConfigSyncAdapter,
                localModelConfigSyncAdapter,
                defaultModelSyncAdapter,
                localModelSyncAdapter,
                ttsProviderSyncAdapter,
                mediaProviderSyncAdapter,
                apiCredentialsSyncAdapter,
                ApiKeySyncAdapter(),
                studioAlbumSyncAdapter,
                studioMediaSyncAdapter,
                documentSyncAdapter,
            )
        }

        private fun String.encryptedForFirestore(
            collectionPath: String,
            entityId: String,
            firestoreSnakeField: String,
        ): String =
            sensitiveSelfHealer.requireEncrypted(
                ref = FirestoreSensitiveFieldRefMapping.sensitiveFieldRef(collectionPath, firestoreSnakeField),
                entityId = entityId,
                value = this,
                sourceSide = "ROOM",
            ) ?: this

        private fun DocumentSnapshot.getEncryptedField(field: String): String? {
            val collectionPath = reference.parent.id
            val stringValue = getString(field)
            if (!stringValue.isNullOrEmpty()) {
                return sensitiveSelfHealer.requireEncrypted(
                    ref = FirestoreSensitiveFieldRefMapping.sensitiveFieldRef(collectionPath, field),
                    entityId = id,
                    value = stringValue,
                    sourceSide = "FIRESTORE",
                )
            }
            return getBlob(field)?.toBytes()?.takeIf { it.isNotEmpty() }?.decodeToString()
        }

        private suspend fun ensurePlaceholderMessage(
            messageId: MessageId,
            chatId: ChatId,
        ) {
            if (chatId.value.isBlank()) return
            if (messageId.value.isBlank()) return
            val chat = chatDao.getChatById(chatId)
            if (chat == null) {
                chatDao.insertIfAbsent(
                    ChatEntity(
                        id = chatId,
                        name =
                            localEncryption.encryptString(
                                "Syncing...",
                                AadContext("ChatEntity", "name", chatId.value).toBytes(),
                            ),
                        created = java.util.Date(0),
                        lastModified = java.util.Date(0),
                        pinned = false,
                        updatedAt = 0L,
                        deleted = false,
                        pendingSync = 0,
                    ),
                )
            }

            messageDao.insertIfAbsent(
                MessageEntity(
                    id = messageId,
                    chatId = chatId,
                    content =
                        localEncryption.encryptString(
                            "Syncing...",
                            AadContext("MessageEntity", "content", messageId.value).toBytes(),
                        ),
                    role = Role.USER,
                    messageState = MessageState.COMPLETE,
                    createdAt = 0L,
                    pendingSync = 0,
                ),
            )
        }

        private val chatSyncAdapter =
            object : EntitySyncAdapter<ChatEntity> {
                override val collectionPath: String = "chats"

                override suspend fun claimPending() = chatDao.claimPending()

                override suspend fun getClaimedForSync() = chatDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<ChatEntity>) = chatDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: ChatEntity): String = entity.id.value

                override suspend fun toFirestore(local: ChatEntity): Map<String, Any?> =
                    mapOf(
                        "name" to local.name.encryptedForFirestore(collectionPath, local.id.value, "name"),
                        "created_at" to local.created.time,
                        "last_modified" to local.lastModified.time,
                        "pinned" to local.pinned,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot): ChatEntity? = chatEntityFromDoc(doc)

                override suspend fun upsertLocal(
                    entity: ChatEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existing = chatDao.getChatById(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        chatDao.upsert(entity.copy(pendingSync = 0)) // 0 = clean
                    } else {
                        loggingPort.debug(TAG, "Chat ${entity.id.value} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    chatDao.softDeleteById(ChatId(id))
                    // Soft-delete linked documents too (documents are tied to messages via message_id).
                    documentDao.softDeleteByChatId(ChatId(id))
                }
            }

        private val messageSyncAdapter =
            object : EntitySyncAdapter<MessageEntity> {
                override val collectionPath: String = "messages"

                override suspend fun claimPending() = messageDao.claimPending()

                override suspend fun getClaimedForSync() = messageDao.getClaimedForSync().map { it.message }

                override suspend fun markSynced(entities: List<MessageEntity>) = messageDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: MessageEntity): String = entity.id.value

                override suspend fun toFirestore(local: MessageEntity): Map<String, Any?> {
                    val msgData =
                        mutableMapOf<String, Any?>(
                            "chat_id" to local.chatId.value,
                            "content" to local.content.encryptedForFirestore(collectionPath, local.id.value, "content"),
                            "role" to local.role.apiValue,
                            "message_state" to local.messageState.apiValue,
                            "updated_at" to local.updatedAt,
                            "deleted" to local.deleted,
                        )
                    local.createdAt?.let { msgData["created_at"] = it }
                    local.modelType?.let { msgData["model_type"] = it.name }
                    local.userMessageId?.let { msgData["user_message_id"] = it.value }
                    local.imageUrisJson?.let {
                        msgData["image_uris_json"] = it.encryptedForFirestore(collectionPath, local.id.value, "image_uris_json")
                    }
                    local.thinkingDurationSeconds?.let { msgData["thinking_duration_seconds"] = it }
                    local.thinkingRaw?.let {
                        msgData["thinking_raw"] = it.encryptedForFirestore(collectionPath, local.id.value, "thinking_raw")
                    }
                    local.thinkingStartTime?.let { msgData["thinking_start_time"] = it }
                    local.thinkingEndTime?.let { msgData["thinking_end_time"] = it }
                    local.pipelineStep?.let { msgData["pipeline_step"] = it.name }
                    local.artifactsJson?.let {
                        msgData["artifacts_json"] = it.encryptedForFirestore(collectionPath, local.id.value, "artifacts_json")
                    }
                    local.generatedMediaUrisJson?.let {
                        msgData["generated_media_uris_json"] =
                            it.encryptedForFirestore(collectionPath, local.id.value, "generated_media_uris_json")
                    }
                    local.generatedMediaAssetIdsJson?.let {
                        msgData["generated_media_asset_ids_json"] =
                            it.encryptedForFirestore(collectionPath, local.id.value, "generated_media_asset_ids_json")
                    }
                    local.generatedMediaTypesJson?.let { msgData["generated_media_types_json"] = it }
                    local.generatedMediaPromptsJson?.let {
                        msgData["generated_media_prompts_json"] =
                            it.encryptedForFirestore(collectionPath, local.id.value, "generated_media_prompts_json")
                    }

                    return msgData
                }

                override fun fromFirestore(doc: DocumentSnapshot): MessageEntity? = messageEntityFromDoc(doc)

                override suspend fun upsertLocal(
                    entity: MessageEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existing = messageDao.getMessageById(entity.id)?.message
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        ensurePlaceholderMessage(entity.id, entity.chatId)

                        // Preserve local artifacts_json until Task 4 migration is complete
                        val merged =
                            entity.copy(
                                artifactsJson = if (entity.artifactsJson.isNullOrBlank()) existing?.artifactsJson else entity.artifactsJson,
                                pendingSync = 0,
                            )
                        messageDao.upsert(merged)

                        val hash = entity.content.sha256()
                        val existingMeta = embeddingDao.getEmbeddingMetadata(entity.id.value)
                        if (existingMeta?.contentHash != hash) {
                            embeddingCoordinator.enqueueMessage(entity.id.value)
                        }
                    } else {
                        loggingPort.debug(TAG, "Message ${entity.id.value} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    val messageId = MessageId(id)
                    messageDao.softDeleteById(messageId)
                    // Soft-delete linked documents too (documents are tied to messages via message_id).
                    documentDao.softDeleteByMessageId(messageId)
                }
            }

        private val chatSummarySyncAdapter =
            object : EntitySyncAdapter<ChatSummaryEntity> {
                override val collectionPath: String = "chat_summaries"

                override suspend fun claimPending() = chatSummaryDao.claimPending()

                override suspend fun getClaimedForSync() = chatSummaryDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<ChatSummaryEntity>) = chatSummaryDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: ChatSummaryEntity): String = entity.chatId.value

                override suspend fun toFirestore(local: ChatSummaryEntity): Map<String, Any?> =
                    mapOf(
                        "chat_id" to local.chatId.value,
                        "content" to local.content.encryptedForFirestore(collectionPath, local.chatId.value, "content"),
                        "last_summarized_message_id" to local.lastSummarizedMessageId?.value,
                        "created_at" to local.createdAt,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot): ChatSummaryEntity? {
                    val chatIdStr = doc.getString("chat_id") ?: return null
                    val content = doc.getEncryptedField("content") ?: return null
                    return ChatSummaryEntity(
                        chatId = ChatId(chatIdStr),
                        content = content,
                        lastSummarizedMessageId = doc.getString("last_summarized_message_id")?.let { MessageId(it) },
                        createdAt = doc.getLong("created_at") ?: 0L,
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                        pendingSync = 0,
                    )
                }

                override suspend fun upsertLocal(
                    entity: ChatSummaryEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existing = chatSummaryDao.getSummaryByChatIdIncludingDeletedSync(entity.chatId)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        val merged =
                            if (existing != null) {
                                entity.copy(id = existing.id, pendingSync = 0)
                            } else {
                                entity.copy(pendingSync = 0)
                            }
                        chatSummaryDao.insertOrUpdateSummary(merged)
                    } else {
                        loggingPort.debug(TAG, "ChatSummary ${entity.chatId.value} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    chatSummaryDao.deleteSummaryForChat(ChatId(id))
                }
            }

        private val memoriesSyncAdapter =
            object : EntitySyncAdapter<MemoriesEntity> {
                override val collectionPath = "memories"

                override suspend fun claimPending() = memoriesDao.claimPending()

                override suspend fun getClaimedForSync() = memoriesDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<MemoriesEntity>) = memoriesDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: MemoriesEntity) = entity.id

                override suspend fun toFirestore(local: MemoriesEntity) =
                    mapOf(
                        "category" to local.category.name,
                        "content" to local.content.encryptedForFirestore(collectionPath, local.id, "content"),
                        "created_at" to local.createdAt,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot): MemoriesEntity? {
                    val content = doc.getEncryptedField("content") ?: return null
                    return MemoriesEntity(
                        id = doc.id,
                        category =
                            com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory.valueOf(
                                doc.getString("category") ?: "CONVERSATION",
                            ),
                        content = content,
                        createdAt = doc.getLong("created_at") ?: 0L,
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                    )
                }

                override suspend fun upsertLocal(
                    entity: MemoriesEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existing = memoriesDao.getMemoryById(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        memoriesDao.insertMemory(entity.copy(pendingSync = 0))

                        val hash = entity.content.sha256()
                        val existingMeta = embeddingDao.getEmbeddingMetadata(entity.id)
                        if (existingMeta?.contentHash != hash) {
                            embeddingCoordinator.enqueueMemory(entity.id)
                        }
                    } else {
                        loggingPort.debug(TAG, "Memory ${entity.id} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    memoriesDao.softDeleteById(id)
                }
            }

        private val visionAnalysisSyncAdapter =
            object : EntitySyncAdapter<MessageVisionAnalysisEntity> {
                override val collectionPath = "vision_analysis"

                override suspend fun claimPending() = messageVisionAnalysisDao.claimPending()

                override suspend fun getClaimedForSync() = messageVisionAnalysisDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<MessageVisionAnalysisEntity>) =
                    messageVisionAnalysisDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: MessageVisionAnalysisEntity) = entity.id

                override suspend fun toFirestore(local: MessageVisionAnalysisEntity): Map<String, Any?> {
                    val message = messageDao.getMessageById(local.userMessageId)?.message ?: return emptyMap()
                    val chatId = message.chatId.value
                    return mapOf(
                        "user_message_id" to local.userMessageId.value,
                        "chat_id" to chatId,
                        "image_uri" to local.imageUri.encryptedForFirestore(collectionPath, local.id, "image_uri"),
                        "prompt_text" to local.promptText.encryptedForFirestore(collectionPath, local.id, "prompt_text"),
                        "analysis_text" to local.analysisText.encryptedForFirestore(collectionPath, local.id, "analysis_text"),
                        "model_type" to local.modelType.name,
                        "created_at" to local.createdAt,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )
                }

                override fun fromFirestore(doc: DocumentSnapshot): MessageVisionAnalysisEntity? {
                    val chatIdStr = doc.getString("chat_id")
                    if (chatIdStr.isNullOrBlank()) return null
                    val userMessageIdStr = doc.getString("user_message_id")
                    if (userMessageIdStr.isNullOrBlank()) return null
                    val promptText = doc.getEncryptedField("prompt_text") ?: return null
                    val analysisText = doc.getEncryptedField("analysis_text") ?: return null
                    return MessageVisionAnalysisEntity(
                        id = doc.id,
                        userMessageId = MessageId(userMessageIdStr),
                        imageUri = doc.getEncryptedField("image_uri") ?: "",
                        promptText = promptText,
                        analysisText = analysisText,
                        modelType = ModelType.valueOf(doc.getString("model_type") ?: "LLM"),
                        createdAt = doc.getLong("created_at") ?: 0L,
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                    )
                }

                override suspend fun upsertLocal(
                    entity: MessageVisionAnalysisEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val chatId = doc.getString("chat_id")?.let { ChatId(it) }
                    if (chatId != null) {
                        ensurePlaceholderMessage(entity.userMessageId, chatId)
                    }
                    val existing = messageVisionAnalysisDao.getById(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        messageVisionAnalysisDao.upsert(entity.copy(pendingSync = 0))
                    } else {
                        loggingPort.debug(TAG, "VisionAnalysis ${entity.id} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    messageVisionAnalysisDao.softDeleteById(id)
                }
            }

        private val messageFileSyncAdapter =
            object : EntitySyncAdapter<MessageFileEntity> {
                override val collectionPath = "message_files"

                override suspend fun claimPending() = messageFileDao.claimPending()

                override suspend fun getClaimedForSync() = messageFileDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<MessageFileEntity>) = messageFileDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: MessageFileEntity) = entity.id

                override suspend fun toFirestore(local: MessageFileEntity): Map<String, Any?> {
                    val message = messageDao.getMessageById(MessageId(local.messageId))?.message ?: return emptyMap()
                    val chatId = message.chatId.value
                    return mapOf(
                        "message_id" to local.messageId,
                        "name" to local.name.encryptedForFirestore(collectionPath, local.id, "name"),
                        "mime_type" to local.mimeType.name,
                        "remote_url" to local.remoteUrl?.let { it.encryptedForFirestore(collectionPath, local.id, "remote_url") },
                        "sync_status" to local.syncStatus,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )
                }

                override fun fromFirestore(doc: DocumentSnapshot): MessageFileEntity? {
                    val chatIdStr = doc.getString("chat_id")
                    if (chatIdStr.isNullOrBlank()) return null
                    val messageIdStr = doc.getString("message_id")
                    if (messageIdStr.isNullOrBlank()) return null
                    val name = doc.getEncryptedField("name") ?: return null
                    val remoteUrl = doc.getEncryptedField("remote_url")

                    val mimeTypeStr = doc.getString("mime_type") ?: MimeType.PLAIN_TEXT.name
                    val mimeType =
                        try {
                            MimeType.valueOf(mimeTypeStr)
                        } catch (e: Exception) {
                            MimeType.PLAIN_TEXT
                        }

                    return MessageFileEntity(
                        id = doc.id,
                        messageId = messageIdStr,
                        name = name,
                        content = "", // Content will be downloaded from Storage if remoteUrl exists
                        mimeType = mimeType,
                        remoteUrl = remoteUrl,
                        syncStatus = doc.getString("sync_status") ?: "PENDING_UPLOAD",
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                        pendingSync = 0,
                    )
                }

                override suspend fun upsertLocal(
                    entity: MessageFileEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val chatId = doc.getString("chat_id")?.let { ChatId(it) }
                    if (chatId != null) {
                        ensurePlaceholderMessage(MessageId(entity.messageId), chatId)
                    }
                    val existing = messageFileDao.getById(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        // Preserve local content if we already have it
                        val merged =
                            if (existing != null) {
                                entity.copy(
                                    content = if (entity.content.isBlank()) existing.content else entity.content,
                                    pendingSync = 0,
                                )
                            } else {
                                entity.copy(pendingSync = 0)
                            }
                        messageFileDao.insert(merged)
                    } else {
                        loggingPort.debug(TAG, "MessageFile ${entity.id} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    messageFileDao.softDeleteById(id)
                }
            }

        private val webSearchSourceSyncAdapter =
            object : EntitySyncAdapter<WebSearchSourceEntity> {
                override val collectionPath = "web_search_sources"

                override suspend fun claimPending() = webSearchSourceDao.claimPending()

                override suspend fun getClaimedForSync() = webSearchSourceDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<WebSearchSourceEntity>) =
                    webSearchSourceDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: WebSearchSourceEntity) = entity.id

                override suspend fun toFirestore(local: WebSearchSourceEntity): Map<String, Any?> {
                    val message = messageDao.getMessageById(local.messageId)?.message ?: return emptyMap()
                    val chatId = message.chatId.value
                    return mapOf(
                        "message_id" to local.messageId.value,
                        "chat_id" to chatId,
                        "title" to local.title.encryptedForFirestore(collectionPath, local.id, "title"),
                        "url" to local.url.encryptedForFirestore(collectionPath, local.id, "url"),
                        "content" to local.content.encryptedForFirestore(collectionPath, local.id, "content"),
                        "score" to local.score,
                        "extracted" to local.extracted,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )
                }

                override fun fromFirestore(doc: DocumentSnapshot): WebSearchSourceEntity? {
                    val chatIdStr = doc.getString("chat_id")
                    if (chatIdStr.isNullOrBlank()) return null
                    val messageIdStr = doc.getString("message_id")
                    if (messageIdStr.isNullOrBlank()) return null
                    val title = doc.getEncryptedField("title") ?: return null
                    val url = doc.getEncryptedField("url") ?: return null
                    val content = doc.getEncryptedField("content") ?: return null
                    return WebSearchSourceEntity(
                        id = doc.id,
                        messageId = MessageId(messageIdStr),
                        title = title,
                        url = url,
                        content = content,
                        score = doc.getDouble("score") ?: 0.0,
                        extracted = doc.getBoolean("extracted") ?: false,
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                    )
                }

                override suspend fun upsertLocal(
                    entity: WebSearchSourceEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val chatId = doc.getString("chat_id")?.let { ChatId(it) }
                    if (chatId != null) {
                        ensurePlaceholderMessage(entity.messageId, chatId)
                    }
                    val existing = webSearchSourceDao.getById(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        webSearchSourceDao.upsert(entity.copy(pendingSync = 0))
                    } else {
                        loggingPort.debug(TAG, "WebSearchSource ${entity.id} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    webSearchSourceDao.softDeleteById(id)
                }
            }

        private val messageTranscriptSegmentSyncAdapter =
            object : EntitySyncAdapter<MessageTranscriptSegmentEntity> {
                override val collectionPath = "message_transcript_segments"

                override suspend fun claimPending() = messageTranscriptSegmentDao.claimPending()

                override suspend fun getClaimedForSync() = messageTranscriptSegmentDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<MessageTranscriptSegmentEntity>) =
                    messageTranscriptSegmentDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: MessageTranscriptSegmentEntity) = entity.id

                override suspend fun toFirestore(local: MessageTranscriptSegmentEntity): Map<String, Any?> {
                    val message = messageDao.getMessageById(local.messageId)?.message ?: return emptyMap()
                    val chatId = message.chatId.value
                    return messageTranscriptSegmentToFirestoreMap(
                        local,
                        chatId,
                        local.text.encryptedForFirestore(collectionPath, local.id, "text"),
                    )
                }

                override fun fromFirestore(doc: DocumentSnapshot): MessageTranscriptSegmentEntity? =
                    messageTranscriptSegmentFromFirestore(doc) { doc.getEncryptedField("text") }

                override suspend fun upsertLocal(
                    entity: MessageTranscriptSegmentEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val chatId = doc.getString("chat_id")?.let { ChatId(it) }
                    if (chatId != null) {
                        ensurePlaceholderMessage(entity.messageId, chatId)
                    }
                    val existing = messageTranscriptSegmentDao.getById(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        messageTranscriptSegmentDao.upsert(entity.copy(pendingSync = 0))
                    } else {
                        loggingPort.debug(
                            TAG,
                            "MessageTranscriptSegment ${entity.id} local is newer or same age, skipping remote update",
                        )
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    messageTranscriptSegmentDao.softDeleteById(id)
                }
            }

        private val apiModelConfigSyncAdapter =
            object : EntitySyncAdapter<ApiModelConfigurationEntity> {
                override val collectionPath = "api_model_configs"

                override suspend fun claimPending() = apiModelConfigurationsDao.claimPending()

                override suspend fun getClaimedForSync() = apiModelConfigurationsDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<ApiModelConfigurationEntity>) =
                    apiModelConfigurationsDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: ApiModelConfigurationEntity) = entity.id.value

                override suspend fun toFirestore(local: ApiModelConfigurationEntity) =
                    mapOf(
                        "api_credentials_id" to local.apiCredentialsId.value,
                        "display_name" to local.displayName.encryptedForFirestore(collectionPath, local.id.value, "display_name"),
                        "custom_headers" to local.customHeaders.encryptedForFirestore(collectionPath, local.id.value, "custom_headers"),
                        "max_tokens" to local.maxTokens,
                        "context_window" to local.contextWindow,
                        "temperature" to local.temperature,
                        "top_p" to local.topP,
                        "top_k" to local.topK,
                        "min_p" to local.minP,
                        "frequency_penalty" to local.frequencyPenalty,
                        "presence_penalty" to local.presencePenalty,
                        "system_prompt" to local.systemPrompt.encryptedForFirestore(collectionPath, local.id.value, "system_prompt"),
                        "max_tokens_disabled" to local.maxTokensDisabled,
                        "temperature_disabled" to local.temperatureDisabled,
                        "top_p_disabled" to local.topPDisabled,
                        "top_k_disabled" to local.topKDisabled,
                        "min_p_disabled" to local.minPDisabled,
                        "frequency_penalty_disabled" to local.frequencyPenaltyDisabled,
                        "presence_penalty_disabled" to local.presencePenaltyDisabled,
                        "reasoning_effort" to local.reasoningEffort,
                        "openrouter_provider_sort" to local.openRouterProviderSort,
                        "openrouter_allow_fallbacks" to local.openRouterAllowFallbacks,
                        "openrouter_require_parameters" to local.openRouterRequireParameters,
                        "openrouter_data_collection" to local.openRouterDataCollectionPolicy,
                        "openrouter_zdr" to local.openRouterZeroDataRetention,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot) =
                    ApiModelConfigurationEntity(
                        id = ApiModelConfigurationId(doc.id),
                        apiCredentialsId = ApiCredentialsId(doc.getString("api_credentials_id") ?: ""),
                        displayName = doc.getEncryptedField("display_name") ?: "",
                        customHeaders = doc.getEncryptedField("custom_headers") ?: "{}",
                        maxTokens = doc.getLong("max_tokens")?.toInt() ?: 4096,
                        contextWindow = doc.getLong("context_window")?.toInt() ?: 4096,
                        temperature = doc.getDouble("temperature") ?: 0.7,
                        topP = doc.getDouble("top_p") ?: 0.95,
                        topK = doc.getLong("top_k")?.toInt(),
                        minP = doc.getDouble("min_p") ?: 0.05,
                        frequencyPenalty = doc.getDouble("frequency_penalty") ?: 0.0,
                        presencePenalty = doc.getDouble("presence_penalty") ?: 0.0,
                        systemPrompt = doc.getEncryptedField("system_prompt") ?: "",
                        maxTokensDisabled = doc.getBoolean("max_tokens_disabled") ?: false,
                        temperatureDisabled = doc.getBoolean("temperature_disabled") ?: false,
                        topPDisabled = doc.getBoolean("top_p_disabled") ?: false,
                        topKDisabled = doc.getBoolean("top_k_disabled") ?: false,
                        minPDisabled = doc.getBoolean("min_p_disabled") ?: false,
                        frequencyPenaltyDisabled = doc.getBoolean("frequency_penalty_disabled") ?: false,
                        presencePenaltyDisabled = doc.getBoolean("presence_penalty_disabled") ?: false,
                        reasoningEffort = doc.getString("reasoning_effort"),
                        openRouterProviderSort = doc.getString("openrouter_provider_sort"),
                        openRouterAllowFallbacks = doc.getBoolean("openrouter_allow_fallbacks"),
                        openRouterRequireParameters = doc.getBoolean("openrouter_require_parameters"),
                        openRouterDataCollectionPolicy = doc.getString("openrouter_data_collection"),
                        openRouterZeroDataRetention = doc.getBoolean("openrouter_zdr"),
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                    )

                override suspend fun upsertLocal(
                    entity: ApiModelConfigurationEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existing = apiModelConfigurationsDao.getById(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        apiModelConfigurationsDao.upsert(entity.copy(pendingSync = 0))
                    } else {
                        loggingPort.debug(TAG, "ApiModelConfig ${entity.id.value} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    apiModelConfigurationsDao.softDeleteById(ApiModelConfigurationId(id))
                }
            }

        private val localModelConfigSyncAdapter =
            object : EntitySyncAdapter<LocalModelConfigurationEntity> {
                override val collectionPath = "local_model_configs"

                override suspend fun claimPending() = localModelConfigurationsDao.claimPending()

                override suspend fun getClaimedForSync() = localModelConfigurationsDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<LocalModelConfigurationEntity>) =
                    localModelConfigurationsDao.markSynced(
                        entities.map {
                            it.id
                        },
                    )

                override fun getEntityId(entity: LocalModelConfigurationEntity) = entity.id.value

                override suspend fun toFirestore(local: LocalModelConfigurationEntity) =
                    mapOf(
                        "local_model_id" to local.localModelId.value,
                        "display_name" to local.displayName.encryptedForFirestore(collectionPath, local.id.value, "display_name"),
                        "temperature" to local.temperature,
                        "top_k" to local.topK,
                        "top_p" to local.topP,
                        "min_p" to local.minP,
                        "repetition_penalty" to local.repetitionPenalty,
                        "max_tokens" to local.maxTokens,
                        "context_window" to local.contextWindow,
                        "thinking_enabled" to local.thinkingEnabled,
                        "is_system_preset" to local.isSystemPreset,
                        "system_prompt" to
                            local.systemPrompt?.encryptedForFirestore(collectionPath, local.id.value, "system_prompt"),
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot) =
                    LocalModelConfigurationEntity(
                        id = LocalModelConfigurationId(doc.id),
                        localModelId = LocalModelId(doc.getString("local_model_id") ?: ""),
                        displayName = doc.getEncryptedField("display_name") ?: "",
                        temperature = doc.getDouble("temperature") ?: 0.7,
                        topK = doc.getLong("top_k")?.toInt() ?: 40,
                        topP = doc.getDouble("top_p") ?: 0.95,
                        minP = doc.getDouble("min_p") ?: 0.05,
                        repetitionPenalty = doc.getDouble("repetition_penalty") ?: 1.1,
                        maxTokens = doc.getLong("max_tokens")?.toInt() ?: 4096,
                        contextWindow = doc.getLong("context_window")?.toInt() ?: 4096,
                        thinkingEnabled = doc.getBoolean("thinking_enabled") ?: false,
                        isSystemPreset = doc.getBoolean("is_system_preset") ?: false,
                        systemPrompt = doc.getEncryptedField("system_prompt"),
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                    )

                override suspend fun upsertLocal(
                    entity: LocalModelConfigurationEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existingById = localModelConfigurationsDao.getById(entity.id)
                    val existingByKey = localModelConfigurationsDao.getByModelIdAndName(entity.localModelId, entity.displayName)
                    val existing = existingById ?: existingByKey

                    if (existing == null || existing.id != entity.id || entity.updatedAt >= existing.updatedAt) {
                        val newEntity = entity.copy(id = existing?.id ?: entity.id, pendingSync = 0)
                        localModelConfigurationsDao.upsert(newEntity)
                    } else {
                        loggingPort.debug(TAG, "LocalModelConfig ${entity.id.value} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    localModelConfigurationsDao.softDeleteById(LocalModelConfigurationId(id))
                }
            }

        private val defaultModelSyncAdapter =
            object : EntitySyncAdapter<DefaultModelEntity> {
                override val collectionPath = "default_models"

                override suspend fun claimPending() = defaultModelsDao.claimPending()

                override suspend fun getClaimedForSync() = defaultModelsDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<DefaultModelEntity>) =
                    defaultModelsDao.markSynced(entities.map { it.modelType })

                override fun getEntityId(entity: DefaultModelEntity) = entity.modelType.name

                override suspend fun toFirestore(local: DefaultModelEntity) =
                    mapOf(
                        "local_config_id" to local.localConfigId?.value,
                        "api_config_id" to local.apiConfigId?.value,
                        "tts_provider_id" to local.ttsProviderId?.value,
                        "media_provider_id" to local.mediaProviderId?.value,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot) =
                    DefaultModelEntity(
                        modelType = ModelType.valueOf(doc.id),
                        localConfigId = doc.getString("local_config_id")?.let { LocalModelConfigurationId(it) },
                        apiConfigId = doc.getString("api_config_id")?.let { ApiModelConfigurationId(it) },
                        ttsProviderId = doc.getString("tts_provider_id")?.let { TtsProviderId(it) },
                        mediaProviderId = doc.getString("media_provider_id")?.let { MediaProviderId(it) },
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                    )

                override suspend fun upsertLocal(
                    entity: DefaultModelEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existing = defaultModelsDao.getDefault(entity.modelType)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        defaultModelsDao.upsert(entity.copy(pendingSync = 0))
                    } else {
                        loggingPort.debug(TAG, "DefaultModel ${entity.modelType.name} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    defaultModelsDao.softDeleteById(ModelType.valueOf(id))
                }
            }

        private val localModelSyncAdapter =
            object : EntitySyncAdapter<LocalModelEntity> {
                override val collectionPath = "local_models"

                override suspend fun claimPending() = localModelsDao.claimPending()

                override suspend fun getClaimedForSync() = localModelsDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<LocalModelEntity>) = localModelsDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: LocalModelEntity) = entity.id.value

                override suspend fun toFirestore(local: LocalModelEntity) =
                    mapOf(
                        "model_file_format" to local.modelFileFormat.name,
                        "huggingface_model_name" to local.huggingFaceModelName,
                        "remote_filename" to local.remoteFilename,
                        "local_filename" to local.localFilename,
                        "sha256" to local.sha256,
                        "size_in_bytes" to local.sizeInBytes,
                        "is_multimodal" to local.isMultimodal,
                        "mmproj_remote_filename" to local.mmprojRemoteFilename,
                        "mmproj_local_filename" to local.mmprojLocalFilename,
                        "mmproj_sha256" to local.mmprojSha256,
                        "mmproj_size_in_bytes" to local.mmprojSizeInBytes,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot) =
                    LocalModelEntity(
                        id = LocalModelId(doc.id),
                        modelFileFormat = ModelFileFormat.valueOf(doc.getString("model_file_format") ?: "GGUF"),
                        huggingFaceModelName = doc.getString("huggingface_model_name") ?: "",
                        remoteFilename = doc.getString("remote_filename") ?: "",
                        localFilename = doc.getString("local_filename") ?: "",
                        sha256 = doc.getString("sha256") ?: "",
                        sizeInBytes = doc.getLong("size_in_bytes") ?: 0L,
                        isMultimodal = doc.getBoolean("is_multimodal") ?: false,
                        mmprojRemoteFilename = doc.getString("mmproj_remote_filename"),
                        mmprojLocalFilename = doc.getString("mmproj_local_filename"),
                        mmprojSha256 = doc.getString("mmproj_sha256"),
                        mmprojSizeInBytes = doc.getLong("mmproj_size_in_bytes"),
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                    )

                override suspend fun upsertLocal(
                    entity: LocalModelEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existingById = localModelsDao.getById(entity.id)
                    val existingByKey = localModelsDao.getBySha256(entity.sha256)
                    val existing = existingById ?: existingByKey

                    if (existing == null || existing.id != entity.id || entity.updatedAt >= existing.updatedAt) {
                        val newEntity = entity.copy(id = existing?.id ?: entity.id, pendingSync = 0)
                        localModelsDao.upsert(newEntity)
                    } else {
                        loggingPort.debug(TAG, "LocalModel ${entity.id.value} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    localModelsDao.softDeleteById(LocalModelId(id))
                }
            }

        private val ttsProviderSyncAdapter =
            object : EntitySyncAdapter<TtsProviderEntity> {
                override val collectionPath = "tts_providers"

                override suspend fun claimPending() = ttsProviderDao.claimPending()

                override suspend fun getClaimedForSync() = ttsProviderDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<TtsProviderEntity>) = ttsProviderDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: TtsProviderEntity) = entity.id

                override suspend fun toFirestore(local: TtsProviderEntity) =
                    mapOf(
                        "display_name" to local.displayName.encryptedForFirestore(collectionPath, local.id, "display_name"),
                        "provider" to local.provider.name,
                        "voice_name" to local.voiceName.encryptedForFirestore(collectionPath, local.id, "voice_name"),
                        "model_name" to local.modelName?.let { it.encryptedForFirestore(collectionPath, local.id, "model_name") },
                        "base_url" to local.baseUrl?.let { it.encryptedForFirestore(collectionPath, local.id, "base_url") },
                        "credential_alias" to local.credentialAlias,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot) =
                    TtsProviderEntity(
                        id = doc.id,
                        displayName = doc.getEncryptedField("display_name") ?: "",
                        provider =
                            com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
                                .valueOf(doc.getString("provider") ?: "OPENAI"),
                        voiceName = doc.getEncryptedField("voice_name") ?: "",
                        modelName = doc.getEncryptedField("model_name"),
                        baseUrl = doc.getEncryptedField("base_url"),
                        credentialAlias = doc.getString("credential_alias") ?: "",
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                    )

                override suspend fun upsertLocal(
                    entity: TtsProviderEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existing = ttsProviderDao.getTtsProvider(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        ttsProviderDao.upsertTtsProvider(entity.copy(pendingSync = 0))
                    } else {
                        loggingPort.debug(TAG, "TtsProvider ${entity.id} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    ttsProviderDao.softDeleteById(id)
                }
            }

        private val mediaProviderSyncAdapter =
            object : EntitySyncAdapter<MediaProviderEntity> {
                override val collectionPath = "media_providers"

                override suspend fun claimPending() = mediaProviderDao.claimPending()

                override suspend fun getClaimedForSync() = mediaProviderDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<MediaProviderEntity>) = mediaProviderDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: MediaProviderEntity) = entity.id

                override suspend fun toFirestore(local: MediaProviderEntity) =
                    mapOf(
                        "display_name" to local.displayName.encryptedForFirestore(collectionPath, local.id, "display_name"),
                        "provider" to local.provider.name,
                        "capability" to local.capability.name,
                        "model_name" to local.modelName?.let { it.encryptedForFirestore(collectionPath, local.id, "model_name") },
                        "base_url" to local.baseUrl?.let { it.encryptedForFirestore(collectionPath, local.id, "base_url") },
                        "credential_alias" to local.credentialAlias,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot) =
                    MediaProviderEntity(
                        id = doc.id,
                        displayName = doc.getEncryptedField("display_name") ?: "",
                        provider =
                            com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
                                .valueOf(doc.getString("provider") ?: "OPENAI"),
                        capability =
                            com.browntowndev.pocketcrew.domain.model.config.MediaCapability
                                .valueOf(doc.getString("capability") ?: "IMAGE"),
                        modelName = doc.getEncryptedField("model_name"),
                        baseUrl = doc.getEncryptedField("base_url"),
                        credentialAlias = doc.getString("credential_alias") ?: "",
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                    )

                override suspend fun upsertLocal(
                    entity: MediaProviderEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existing = mediaProviderDao.getMediaProvider(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        mediaProviderDao.upsert(entity.copy(pendingSync = 0))
                    } else {
                        loggingPort.debug(TAG, "MediaProvider ${entity.id} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    mediaProviderDao.softDeleteById(id)
                }
            }

        private val apiCredentialsSyncAdapter =
            object : EntitySyncAdapter<ApiCredentialsEntity> {
                override val collectionPath = "api_credentials"

                override suspend fun claimPending() = apiCredentialsDao.claimPending()

                override suspend fun getClaimedForSync() = apiCredentialsDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<ApiCredentialsEntity>) = apiCredentialsDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: ApiCredentialsEntity) = entity.id.value

                override suspend fun toFirestore(local: ApiCredentialsEntity) =
                    mapOf(
                        "display_name" to local.displayName.encryptedForFirestore(collectionPath, local.id.value, "display_name"),
                        "provider" to local.provider.name,
                        "base_url" to local.baseUrl?.let { it.encryptedForFirestore(collectionPath, local.id.value, "base_url") },
                        "model_id" to local.modelId,
                        "is_multimodal" to local.isMultimodal,
                        "credential_alias" to local.credentialAlias,
                        "encrypted_api_key" to (local.encryptedApiKey?.let { Blob.fromBytes(it) }),
                        "api_key_signature" to local.apiKeySignature,
                        "created_at" to local.createdAt,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot) =
                    ApiCredentialsEntity(
                        id = ApiCredentialsId(doc.id),
                        displayName = doc.getEncryptedField("display_name") ?: "",
                        provider =
                            com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
                                .valueOf(doc.getString("provider") ?: "OPENAI"),
                        baseUrl = doc.getEncryptedField("base_url"),
                        modelId = doc.getString("model_id") ?: "",
                        isMultimodal = doc.getBoolean("is_multimodal") ?: false,
                        credentialAlias = doc.getString("credential_alias") ?: "",
                        encryptedApiKey = doc.getBlob("encrypted_api_key")?.toBytes(),
                        apiKeySignature = doc.getString("api_key_signature"),
                        createdAt = doc.getLong("created_at") ?: 0L,
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                    )

                override suspend fun upsertLocal(
                    entity: ApiCredentialsEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existing = apiCredentialsDao.getById(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        apiCredentialsDao.upsert(entity.copy(pendingSync = 0))
                    } else {
                        loggingPort.debug(TAG, "ApiCredentials ${entity.id.value} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    apiCredentialsDao.softDeleteById(ApiCredentialsId(id))
                }
            }

        private val studioAlbumSyncAdapter =
            object : EntitySyncAdapter<StudioAlbumEntity> {
                override val collectionPath = "studio_albums"

                override suspend fun claimPending() = studioAlbumDao.claimPending()

                override suspend fun getClaimedForSync() = studioAlbumDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<StudioAlbumEntity>) = studioAlbumDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: StudioAlbumEntity) = entity.id

                override suspend fun toFirestore(local: StudioAlbumEntity) =
                    mapOf(
                        "name" to local.name.encryptedForFirestore(collectionPath, local.id, "name"),
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot) =
                    StudioAlbumEntity(
                        id = doc.id,
                        name = doc.getEncryptedField("name") ?: "",
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                        pendingSync = 0,
                    )

                override suspend fun upsertLocal(
                    entity: StudioAlbumEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existing = studioAlbumDao.getAlbumById(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        studioAlbumDao.insertAlbum(entity.copy(pendingSync = 0))
                    } else {
                        loggingPort.debug(TAG, "StudioAlbum ${entity.id} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    studioAlbumDao.softDeleteById(id)
                }

                override fun shouldDelete(entity: StudioAlbumEntity): Boolean = false
            }

        private val studioMediaSyncAdapter =
            object : EntitySyncAdapter<StudioMediaEntity> {
                override val collectionPath = "studio_gallery"

                override suspend fun claimPending() = studioMediaDao.claimPending()

                override suspend fun getClaimedForSync() = studioMediaDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<StudioMediaEntity>) = studioMediaDao.markSynced(entities.map { it.id })

                override fun getEntityId(entity: StudioMediaEntity) = entity.id

                override suspend fun toFirestore(local: StudioMediaEntity) =
                    mapOf(
                        "prompt" to local.prompt.encryptedForFirestore(collectionPath, local.id, "prompt"),
                        "media_type" to local.mediaType,
                        "created_at" to local.createdAt,
                        "album_id" to local.albumId,
                        "remote_url" to local.remoteUrl?.let { it.encryptedForFirestore(collectionPath, local.id, "remote_url") },
                        "remote_thumbnail_url" to
                            local.remoteThumbnailUrl?.let {
                                it.encryptedForFirestore(collectionPath, local.id, "remote_thumbnail_url")
                            },
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot): StudioMediaEntity {
                    val remoteUrl = doc.getEncryptedField("remote_url")
                    val remoteThumbnailUrl = doc.getEncryptedField("remote_thumbnail_url")
                    val prompt = doc.getEncryptedField("prompt") ?: ""

                    return StudioMediaEntity(
                        id = doc.id,
                        prompt = prompt,
                        mediaUri = remoteUrl ?: "",
                        mediaType = doc.getString("media_type") ?: "IMAGE",
                        createdAt = doc.getLong("created_at") ?: System.currentTimeMillis(),
                        albumId = doc.getString("album_id"),
                        remoteUrl = remoteUrl,
                        remoteThumbnailUrl = remoteThumbnailUrl,
                        syncStatus = if (remoteUrl != null) "UPLOADED" else "PENDING_UPLOAD",
                        updatedAt = doc.getLong("updated_at") ?: 0L,
                        deleted = doc.getBoolean("deleted") ?: false,
                        pendingSync = 0,
                    )
                }

                override suspend fun upsertLocal(
                    entity: StudioMediaEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val existing = studioMediaDao.getMediaById(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        // Preserve local path if remote doesn't have a URL yet
                        val merged =
                            if (existing != null) {
                                entity.copy(
                                    mediaUri = if (entity.mediaUri.isBlank()) existing.mediaUri else entity.mediaUri,
                                    pendingSync = 0,
                                )
                            } else {
                                entity.copy(pendingSync = 0)
                            }
                        studioMediaDao.insertMedia(merged)
                    } else {
                        loggingPort.debug(TAG, "StudioMedia ${entity.id} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    studioMediaDao.softDeleteById(id)
                }

                override fun shouldDelete(entity: StudioMediaEntity): Boolean = false
            }

        private inner class ApiKeySyncAdapter : EntitySyncAdapter<Pair<String, String>> {
            override val collectionPath: String = "api_keys"

            override suspend fun claimPending() {
                apiKeyManager.claimPending()
            }

            override suspend fun getClaimedForSync(): List<Pair<String, String>> {
                val syncAliases = apiKeyManager.getClaimedSyncAliases()
                val deleteAliases = apiKeyManager.getClaimedDeleteAliases()

                val syncList =
                    syncAliases.mapNotNull { alias ->
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
                    "updated_at" to
                        com.google.firebase.Timestamp
                            .now(),
                )
            }

            override fun fromFirestore(doc: DocumentSnapshot): Pair<String, String>? {
                val alias = doc.id
                val blob = doc.getBlob("encrypted_key") ?: return null
                val decrypted = cryptoManager.decrypt(blob.toBytes()).getOrNull() ?: return null
                return alias to decrypted
            }

            override suspend fun upsertLocal(
                entity: Pair<String, String>,
                doc: DocumentSnapshot,
                overwrite: Boolean,
            ) {
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
                        val listener =
                            firestore
                                .collection("users/$userId/${adapter.collectionPath}")
                                .addSnapshotListener { snapshots, e ->
                                    if (e != null) {
                                        loggingPort.error(TAG, "${adapter.collectionPath} listener error", e)
                                        return@addSnapshotListener
                                    }
                                    if (snapshots == null) return@addSnapshotListener

                                    externalScope.launch {
                                        for (dc in snapshots.documentChanges) {
                                            try {
                                                processDocumentChange(adapter, dc)
                                            } catch (e: Exception) {
                                                loggingPort.error(
                                                    TAG,
                                                    "Error processing document ${dc.document.id} in ${adapter.collectionPath}",
                                                    e,
                                                )
                                            }
                                        }
                                    }
                                }
                        listenerRegistrations.add(listener)
                    }
                    loggingPort.debug(TAG, "startListening: All listeners started")
                }
            }
        }

        private suspend fun <T> processDocumentChange(
            adapter: EntitySyncAdapter<T>,
            dc: DocumentChange,
        ) {
            val doc = dc.document
            when (dc.type) {
                DocumentChange.Type.ADDED -> {
                    repairRemoteDocument(adapter.collectionPath, doc)
                    adapter.fromFirestore(doc)?.let { entity ->
                        adapter.upsertLocal(entity, doc, overwrite = false)
                    }
                }
                DocumentChange.Type.MODIFIED -> {
                    repairRemoteDocument(adapter.collectionPath, doc)
                    adapter.fromFirestore(doc)?.let { entity ->
                        adapter.upsertLocal(entity, doc, overwrite = true)
                    }
                }
                DocumentChange.Type.REMOVED -> {
                    adapter.deleteLocal(doc.id)
                }
            }
        }

        private suspend fun repairRemoteDocument(
            collectionPath: String,
            doc: DocumentSnapshot,
        ) {
            val data = doc.data ?: return
            val repaired = firestoreSensitiveFieldRepairer.repairInbound(collectionPath, doc.id, data)
            if (repaired != data) {
                doc.reference.set(repaired, SetOptions.merge()).await()
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

        override suspend fun syncAllLocalDataToCloud(): Result<Unit> =
            syncMutex.withLock {
                val userId = userIdProvider() ?: return Result.failure(IllegalStateException("Not signed in"))
                if (!cryptoManager.hasCachedKey()) return Result.failure(IllegalStateException("Vault locked"))

                return try {
                    adapters.forEach { it.claimPending() }
                    syncPendingChangesInternal(userId)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        override suspend fun syncEntitiesToCloud(
            chats: List<Chat>,
            messages: List<Message>,
        ): Result<Unit> =
            syncMutex.withLock {
                val userId = userIdProvider() ?: return Result.failure(IllegalStateException("Not signed in"))
                if (!cryptoManager.hasCachedKey()) return Result.failure(IllegalStateException("Vault locked"))

                return try {
                    val allEntities = mutableListOf<Pair<String, Map<String, Any?>>>()
                    chats.forEach { chat ->
                        if (chat.id.value.isBlank()) {
                            loggingPort.error(TAG, "Empty chat ID in syncEntitiesToCloud. Skipping.")
                            return@forEach
                        }
                        val data =
                            mapOf(
                                "name" to
                                    localEncryption.encryptString(
                                        chat.name,
                                        AadContext("ChatEntity", "name", chat.id.value).toBytes(),
                                    ),
                                "created_at" to chat.created.time,
                                "last_modified" to chat.lastModified.time,
                                "pinned" to chat.pinned,
                                "updated_at" to System.currentTimeMillis(),
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
                        val data =
                            mutableMapOf<String, Any?>(
                                "chat_id" to msgChatId.value,
                                "content" to
                                    localEncryption.encryptString(
                                        msg.content.text,
                                        AadContext("MessageEntity", "content", msg.id.value).toBytes(),
                                    ),
                                "role" to msg.role.name,
                                "message_state" to msg.messageState.name,
                                "created_at" to msg.createdAt,
                                "model_type" to msg.modelType?.name,
                                "updated_at" to System.currentTimeMillis(),
                            )
                        allEntities.add("users/$userId/messages/${msg.id.value}" to data)
                    }

                    if (allEntities.isNotEmpty()) {
                        allEntities.chunked(500).forEach { batchItems ->
                            firestore
                                .runBatch { batch ->
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

        override suspend fun syncPendingChanges(): Result<Unit> =
            syncMutex.withLock {
                val userId = userIdProvider() ?: return Result.failure(IllegalStateException("Not signed in"))
                if (!cryptoManager.hasCachedKey()) return Result.failure(IllegalStateException("Vault locked"))

                return syncPendingChangesInternal(userId)
            }

        private suspend fun syncPendingChangesInternal(userId: String): Result<Unit> =
            try {
                for (adapter in adapters) {
                    syncAdapterPendingChanges(adapter, userId)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                loggingPort.error(TAG, "syncPendingChanges failed", e)
                Result.failure(e)
            }

        private suspend fun <T> syncAdapterPendingChanges(
            adapter: EntitySyncAdapter<T>,
            userId: String,
        ) {
            adapter.claimPending()
            val pending = adapter.getClaimedForSync()
            if (pending.isNotEmpty()) {
                loggingPort.debug(TAG, "Syncing ${pending.size} pending changes for ${adapter.collectionPath}")
                pending.chunked(500).forEach { batchItems ->
                    val upserts =
                        batchItems
                            .filter { !adapter.shouldDelete(it) }
                            .map { entity ->
                                val repairedEntity = repairLocalEntityForSync(entity)
                                val data =
                                    firestoreSensitiveFieldRepairer.repairOutbound(
                                        collectionPath = adapter.collectionPath,
                                        entityId = adapter.getEntityId(repairedEntity),
                                        data = adapter.toFirestore(repairedEntity),
                                    )
                                repairedEntity to data
                            }
                    val deletes = batchItems.filter { adapter.shouldDelete(it) }

                    firestore
                        .runBatch { batch ->
                            upserts.forEach { (entity, data) ->
                                if (data.isNotEmpty()) {
                                    val entityId = adapter.getEntityId(entity)
                                    if (entityId.isBlank()) {
                                        loggingPort.error(
                                            TAG,
                                            "Empty entity ID for ${adapter.collectionPath} in syncAdapterPendingChanges. Skipping.",
                                        )
                                        return@forEach
                                    }
                                    val path = "users/$userId/${adapter.collectionPath}/$entityId"
                                    batch.set(firestore.document(path), data, SetOptions.merge())
                                }
                            }
                            deletes.forEach { entity ->
                                val entityId = adapter.getEntityId(entity)
                                if (entityId.isBlank()) {
                                    loggingPort.error(
                                        TAG,
                                        "Empty entity ID for ${adapter.collectionPath} in syncAdapterPendingChanges. Skipping.",
                                    )
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

        @Suppress("UNCHECKED_CAST")
        private suspend fun <T> repairLocalEntityForSync(entity: T): T {
            val repaired =
                when (entity) {
                    is ChatEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is MessageEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is ChatSummaryEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is MemoriesEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is MessageVisionAnalysisEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is MessageFileEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is WebSearchSourceEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is MessageTranscriptSegmentEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is ApiModelConfigurationEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is LocalModelConfigurationEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is TtsProviderEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is MediaProviderEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is ApiCredentialsEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is StudioAlbumEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is StudioMediaEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    is DocumentEntity -> SensitiveEntityFieldEncryptors.encrypt(entity, sensitiveSelfHealer)
                    else -> entity
                }

            if (repaired != entity) persistRepairedLocalEntity(repaired)
            return repaired as T
        }

        private suspend fun persistRepairedLocalEntity(entity: Any?) {
            when (entity) {
                is ChatEntity -> chatDao.upsert(entity)
                is MessageEntity -> messageDao.upsert(entity)
                is ChatSummaryEntity -> chatSummaryDao.insertOrUpdateSummary(entity)
                is MemoriesEntity -> memoriesDao.insertMemory(entity)
                is MessageVisionAnalysisEntity -> messageVisionAnalysisDao.upsert(entity)
                is MessageFileEntity -> messageFileDao.insert(entity)
                is WebSearchSourceEntity -> webSearchSourceDao.upsert(entity)
                is MessageTranscriptSegmentEntity -> messageTranscriptSegmentDao.upsert(entity)
                is ApiModelConfigurationEntity -> apiModelConfigurationsDao.upsert(entity)
                is LocalModelConfigurationEntity -> localModelConfigurationsDao.upsert(entity)
                is TtsProviderEntity -> ttsProviderDao.upsertTtsProvider(entity)
                is MediaProviderEntity -> mediaProviderDao.upsert(entity)
                is ApiCredentialsEntity -> apiCredentialsDao.upsert(entity)
                is StudioAlbumEntity -> studioAlbumDao.insertAlbum(entity)
                is StudioMediaEntity -> studioMediaDao.insertMedia(entity)
                is DocumentEntity -> documentDao.insertDocument(entity)
            }
        }

        override suspend fun updateVaultSalt(salt: String): Result<Unit> {
            val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
            return try {
                firestore
                    .document("users/$userId")
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
                firestore
                    .document("users/$userId")
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

        override suspend fun updateWrappedDek(wrappedDek: String?): Result<Unit> {
            val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
            return try {
                val value: Any = wrappedDek ?: FieldValue.delete()
                firestore
                    .document("users/$userId")
                    .set(mapOf("wrappedDek" to value), SetOptions.merge())
                    .await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override suspend fun getWrappedDek(): Result<String?> {
            val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
            return try {
                val snapshot = firestore.document("users/$userId").get().await()
                if (snapshot.exists()) {
                    Result.success(snapshot.getString("wrappedDek"))
                } else {
                    Result.success(null)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override suspend fun uploadVaultMetadata(metadata: VaultMetadata): Result<Unit> {
            val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
            return try {
                val payload =
                    mapOf(
                        "vaultSalt" to metadata.salt,
                        "vaultVerification" to
                            (
                                metadata.verification
                                    ?: FieldValue.delete()
                            ),
                        "wrappedDek" to
                            (
                                metadata.wrappedDek
                                    ?: FieldValue.delete()
                            ),
                    )
                firestore
                    .document("users/$userId")
                    .set(payload, SetOptions.merge())
                    .await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override suspend fun fetchVaultMetadata(): Result<com.browntowndev.pocketcrew.domain.model.security.VaultMetadata?> {
            val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
            return try {
                val snapshot = firestore.document("users/$userId").get().await()
                if (!snapshot.exists()) return Result.success(null)
                val salt = snapshot.getString("vaultSalt") ?: return Result.success(null)
                Result.success(
                    com.browntowndev.pocketcrew.domain.model.security.VaultMetadata(
                        salt = salt,
                        verification = snapshot.getString("vaultVerification"),
                        wrappedDek = snapshot.getString("wrappedDek"),
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        /**
         * Clears documents under each known sync adapter path under `users/{uid}`, then deletes the user doc.
         * Firestore does not cascade-delete subcollections when the parent document is deleted,
         * so omitting the pre-delete sweep would leave orphan collections unless server-side cleanup exists.
         *
         * Snapshot listeners are detached only after remote deletion succeeds so a transient failure
         * (network, permissions, etc.) does not leave the app signed in with sync listeners torn down.
         */
        override suspend fun deleteUserAccount(): Result<Unit> =
            syncMutex.withLock {
                try {
                    val userId = userIdProvider() ?: return Result.failure(IllegalStateException("Not signed in"))
                    val collectionPaths = adapters.map { it.collectionPath }.distinct()
                    for (path in collectionPaths) {
                        deleteAllDocumentsInFirestoreCollection(parentPath = "users/$userId", segment = path)
                    }
                    firestore.document("users/$userId").delete().await()
                    listenerMutex.withLock {
                        listenerRegistrations.forEach { it.remove() }
                        listenerRegistrations.clear()
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        private suspend fun deleteAllDocumentsInFirestoreCollection(
            parentPath: String,
            segment: String,
        ) {
            val collection = firestore.collection("$parentPath/$segment")
            while (true) {
                val snapshot = collection.limit(FIRESTORE_DELETE_PAGE_SIZE).get().await()
                if (snapshot.documents.isEmpty()) break
                val batch = firestore.batch()
                snapshot.documents.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
        }

        override fun enqueueEmbeddingRegeneration() {
            embeddingCoordinator.enqueueFullRegeneration()
        }

        private val documentSyncAdapter =
            object : EntitySyncAdapter<com.browntowndev.pocketcrew.core.data.local.DocumentEntity> {
                override val collectionPath = "documents"

                override suspend fun claimPending() = documentDao.claimPending()

                override suspend fun getClaimedForSync() = documentDao.getClaimedForSync()

                override suspend fun markSynced(entities: List<com.browntowndev.pocketcrew.core.data.local.DocumentEntity>) =
                    documentDao.markSynced(
                        entities.map {
                            it.id
                        },
                    )

                override fun getEntityId(entity: com.browntowndev.pocketcrew.core.data.local.DocumentEntity) = entity.id

                override suspend fun toFirestore(local: com.browntowndev.pocketcrew.core.data.local.DocumentEntity) =
                    mapOf(
                        "message_id" to local.messageId.value,
                        "chat_id" to
                            messageDao
                                .getMessageById(local.messageId)
                                ?.message
                                ?.chatId
                                ?.value,
                        "json" to local.json.encryptedForFirestore(collectionPath, local.id, "json"),
                        "title" to local.title.encryptedForFirestore(collectionPath, local.id, "title"),
                        "author" to local.author?.let { it.encryptedForFirestore(collectionPath, local.id, "author") },
                        "target_format" to local.targetFormat?.name,
                        "created_at" to local.createdAt,
                        "updated_at" to local.updatedAt,
                        "deleted" to local.deleted,
                    )

                override fun fromFirestore(doc: DocumentSnapshot) = documentEntityFromDoc(doc)

                override suspend fun upsertLocal(
                    entity: com.browntowndev.pocketcrew.core.data.local.DocumentEntity,
                    doc: DocumentSnapshot,
                    overwrite: Boolean,
                ) {
                    val chatIdStr = doc.getString("chat_id")
                    val chatId = chatIdStr?.takeIf { it.isNotBlank() }?.let { ChatId(it) }
                    if (chatId != null) {
                        ensurePlaceholderMessage(entity.messageId, chatId)
                    }
                    val existing = documentDao.getDocumentById(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        documentDao.insertDocument(entity.copy(pendingSync = 0))

                        // Enqueue re-embedding for all blocks if content changed
                        if (existing?.json != entity.json) {
                            embeddingCoordinator.enqueueDocument(entity.id)
                        }
                    } else {
                        loggingPort.debug(TAG, "Document ${entity.id} local is newer or same age, skipping remote update")
                    }
                }

                override suspend fun deleteLocal(id: String) {
                    documentDao.deleteDocument(id)
                }
            }

        private fun documentEntityFromDoc(doc: DocumentSnapshot): com.browntowndev.pocketcrew.core.data.local.DocumentEntity? {
            val json = doc.getEncryptedField("json") ?: return null
            val title = doc.getEncryptedField("title") ?: return null
            val messageIdStr = doc.getString("message_id") ?: return null
            val messageId = MessageId(messageIdStr)
            val targetFormatStr = doc.getString("target_format")
            val targetFormat =
                targetFormatStr?.let {
                    runCatching { MimeType.valueOf(it) }.getOrNull()
                }
            return com.browntowndev.pocketcrew.core.data.local.DocumentEntity(
                id = doc.id,
                messageId = messageId,
                json = json,
                title = title,
                author = doc.getEncryptedField("author"),
                targetFormat = targetFormat,
                createdAt = doc.getLong("created_at") ?: 0L,
                updatedAt = doc.getLong("updated_at") ?: 0L,
                deleted = doc.getBoolean("deleted") ?: false,
                pendingSync = 0,
            )
        }

        private fun chatEntityFromDoc(doc: DocumentSnapshot): ChatEntity? {
            val name = doc.getEncryptedField("name") ?: ""
            return ChatEntity(
                id = ChatId(doc.id),
                name = name,
                created = java.util.Date(doc.getLong("created_at") ?: 0L),
                lastModified = java.util.Date(doc.getLong("last_modified") ?: 0L),
                pinned = doc.getBoolean("pinned") ?: false,
                updatedAt = doc.getLong("updated_at") ?: 0L,
                deleted = doc.getBoolean("deleted") ?: false,
                pendingSync = 0,
            )
        }

        private fun messageEntityFromDoc(doc: DocumentSnapshot): MessageEntity? {
            val chatIdStr = doc.getString("chat_id")
            if (chatIdStr.isNullOrBlank()) return null
            val content = doc.getEncryptedField("content") ?: ""

            return MessageEntity(
                id = MessageId(doc.id),
                chatId = ChatId(chatIdStr),
                content = content,
                role = Role.fromApiValue(doc.getString("role") ?: "user"),
                userMessageId = doc.getString("user_message_id")?.takeIf { it.isNotBlank() }?.let { MessageId(it) },
                imageUrisJson = doc.getEncryptedField("image_uris_json"),
                messageState = doc.getString("message_state")?.let { MessageState.fromApiValue(it) } ?: MessageState.COMPLETE,
                createdAt = doc.getLong("created_at") ?: 0L,
                modelType = doc.getString("model_type")?.let { ModelType.fromApiValue(it) },
                thinkingDurationSeconds = doc.getLong("thinking_duration_seconds"),
                thinkingRaw = doc.getEncryptedField("thinking_raw"),
                thinkingStartTime = doc.getLong("thinking_start_time"),
                thinkingEndTime = doc.getLong("thinking_end_time"),
                pipelineStep =
                    doc.getString("pipeline_step")?.let { stepStr ->
                        com.browntowndev.pocketcrew.domain.model.inference.PipelineStep.entries
                            .find { it.name == stepStr }
                    },
                artifactsJson = doc.getEncryptedField("artifacts_json"),
                generatedMediaUrisJson = doc.getEncryptedField("generated_media_uris_json"),
                generatedMediaAssetIdsJson = doc.getEncryptedField("generated_media_asset_ids_json"),
                generatedMediaTypesJson = doc.getString("generated_media_types_json"),
                generatedMediaPromptsJson = doc.getEncryptedField("generated_media_prompts_json"),
                updatedAt = doc.getLong("updated_at") ?: 0L,
                deleted = doc.getBoolean("deleted") ?: false,
                pendingSync = 0,
            )
        }
    }
