package com.browntowndev.enclave.core.data.repository
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.CleanupDao
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageFileDao
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentEntity
import com.browntowndev.enclave.core.data.local.MessageWithFiles
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceEntity
import com.browntowndev.enclave.core.data.local.dao.message.MessagePersistPayload
import com.browntowndev.enclave.core.data.mapper.chat.toDomain
import com.browntowndev.enclave.core.data.mapper.chat.toEntity
import com.browntowndev.enclave.core.data.mapper.message.toDomain
import com.browntowndev.enclave.core.data.mapper.message.toEntity
import com.browntowndev.enclave.core.data.mapper.telemetry.toEntity
import com.browntowndev.enclave.core.data.security.PersistenceBoundary
import com.browntowndev.enclave.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.enclave.core.data.security.SensitiveFieldRef
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepair
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.chat.ThinkingData
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
class ChatRepositoryImpl
    @Inject
    constructor(
        private val syncTrigger: CloudSyncTrigger,
        private val chatDao: ChatDao,
        private val messageDao: MessageDao,
        private val messageTranscriptSegmentDao: MessageTranscriptSegmentDao,
        private val webSearchSourceDao: WebSearchSourceDao,
        private val documentDao: DocumentDao,
        private val transactionProvider: com.browntowndev.enclave.domain.port.repository.TransactionProvider,
        private val cleanupDao: CleanupDao,
        private val messageInferenceTelemetryDao: MessageInferenceTelemetryDao,
        private val messageFileDao: MessageFileDao,
        private val healer: SensitivePersistenceSelfHealer,
        private val readRepairApplier: SensitiveRoomReadRepairApplier,
    ) : ChatRepository {
        private fun triggerSync() = syncTrigger.trigger()

        override fun getAllChats(): Flow<List<Chat>> =
            chatDao.getAllChats().mapLatest { entities ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val out = entities.map { SensitiveEntityFieldEncryptors.decrypt(it, healer, repairs).toDomain() }
                readRepairApplier.apply(repairs)
                out
            }

        override suspend fun togglePinStatus(chatId: ChatId) {
            val rowsAffected = chatDao.updatePinStatus(chatId)
            require(rowsAffected > 0) { "Chat not found with id: $chatId" }
            triggerSync()
        }

        override fun getMessagesForChat(chatId: ChatId): Flow<List<Message>> {
            // Combine the message table Flow with a web_search_source table Flow.
            // Without this, markExtracted on web_search_source would not cause the
            // Flow to emit, and the UI wouldn't know when a source was extracted.
            return combine(
                messageDao.getMessagesByChatIdFlow(chatId),
                webSearchSourceDao.getByChatIdFlow(chatId),
                documentDao.getDocumentsByChatIdFlow(chatId),
                messageTranscriptSegmentDao.observeSegmentsForChat(chatId),
            ) { messages, webSearchSources, documents, segmentRows ->
                ChatStreamFrame(messages, webSearchSources, documents, segmentRows)
            }.mapLatest { frame ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val sourcesByMessageId = frame.webSearchSources.groupBy { it.messageId }
                val documentsByMessageId = frame.documents.groupBy { it.messageId }
                val segmentRowsByMessageId = frame.segmentRows.groupBy { it.messageId }
                val segmentsByMessageId =
                    segmentRowsByMessageId
                        .mapValues { (_, rows) ->
                            rows.sortedBy { it.ordinal }.map { seg ->
                                SensitiveEntityFieldEncryptors.decrypt(seg, healer, repairs).toDomain()
                            }
                        }
                val out =
                    frame.messages.map { msgWithFiles ->
                        val decryptedMsg = SensitiveEntityFieldEncryptors.decrypt(msgWithFiles.message, healer, repairs)
                        val decryptedFiles =
                            msgWithFiles.files.map { SensitiveEntityFieldEncryptors.decrypt(it, healer, repairs) }
                        msgWithFiles.copy(message = decryptedMsg, files = decryptedFiles).toDomain(
                            webSearchSources =
                                sourcesByMessageId[msgWithFiles.message.id]
                                    ?.map { source ->
                                        SensitiveEntityFieldEncryptors.decrypt(source, healer, repairs)
                                    }
                                    ?: emptyList(),
                            documents =
                                documentsByMessageId[msgWithFiles.message.id]
                                    ?.map { document -> SensitiveEntityFieldEncryptors.decrypt(document, healer, repairs) }
                                    ?: emptyList(),
                            transcriptSegments = segmentsByMessageId[msgWithFiles.message.id] ?: emptyList(),
                            displayUpdatedAt =
                                maxOf(
                                    decryptedMsg.updatedAt,
                                    decryptedFiles.maxOfOrNull { file -> file.updatedAt } ?: decryptedMsg.updatedAt,
                                    sourcesByMessageId[msgWithFiles.message.id]
                                        ?.maxOfOrNull { source -> source.updatedAt } ?: decryptedMsg.updatedAt,
                                    documentsByMessageId[msgWithFiles.message.id]
                                        ?.maxOfOrNull { document -> document.updatedAt } ?: decryptedMsg.updatedAt,
                                    segmentRowsByMessageId[msgWithFiles.message.id]
                                        ?.maxOfOrNull { segment -> segment.updatedAt } ?: decryptedMsg.updatedAt,
                                ),
                        )
                    }
                readRepairApplier.apply(repairs)
                out
            }
        }

        override suspend fun updateMessageState(
            messageId: MessageId,
            messageState: MessageState,
        ) {
            messageDao.updateMessageState(messageId, messageState)
            if (messageState == MessageState.COMPLETE) {
                messageDao.updateMessageCreatedAt(messageId, System.currentTimeMillis())
            }
            triggerSync()
        }

        override suspend fun updateMessageContent(
            messageId: MessageId,
            content: String,
        ) {
            val encrypted =
                healer.requireEncrypted(
                    SensitiveFieldRef("MessageEntity", "content", PersistenceBoundary.ROOM),
                    messageId.value,
                    content,
                ) ?: content
            messageDao.updateMessageContentText(messageId, encrypted)
            triggerSync()
        }

        override suspend fun appendMessageContent(
            messageId: MessageId,
            content: String,
        ) {
            val existing =
                messageDao.getMessageById(messageId)?.message
                    ?: throw IllegalArgumentException("Message not found with id: $messageId")
            val decryptedExisting =
                healer.decryptForRead(
                    SensitiveFieldRef("MessageEntity", "content", PersistenceBoundary.ROOM),
                    messageId.value,
                    existing.content,
                ) ?: ""
            val merged = decryptedExisting + content
            val encrypted =
                healer.requireEncrypted(
                    SensitiveFieldRef("MessageEntity", "content", PersistenceBoundary.ROOM),
                    messageId.value,
                    merged,
                ) ?: merged
            messageDao.updateMessageContentText(messageId, encrypted)
            triggerSync()
        }

        override suspend fun setThinkingStartTime(messageId: MessageId) {
            messageDao.updateThinkingStartTime(messageId, System.currentTimeMillis())
            messageDao.updateMessageState(messageId, MessageState.THINKING)
            triggerSync()
        }

        override suspend fun setThinkingEndTime(messageId: MessageId) {
            messageDao.updateThinkingEndTime(messageId, System.currentTimeMillis())
            triggerSync()
        }

        override suspend fun appendThinkingRaw(
            messageId: MessageId,
            thinkingText: String,
        ) {
            val existing =
                messageDao.getMessageById(messageId)?.message
                    ?: throw IllegalArgumentException("Message not found with id: $messageId")
            val decryptedExisting =
                healer
                    .decryptForRead(
                        SensitiveFieldRef("MessageEntity", "thinkingRaw", PersistenceBoundary.ROOM),
                        messageId.value,
                        existing.thinkingRaw,
                    ).orEmpty()
            val merged = decryptedExisting + thinkingText
            val encrypted =
                healer.requireEncrypted(
                    SensitiveFieldRef("MessageEntity", "thinkingRaw", PersistenceBoundary.ROOM),
                    messageId.value,
                    merged,
                ) ?: merged
            messageDao.updateThinkingRaw(messageId, encrypted)
            triggerSync()
        }

        override suspend fun clearThinking(messageId: MessageId) {
            messageDao.updateThinkingRaw(messageId, null)
            messageDao.updateThinkingStartTime(messageId, 0)
            messageDao.updateThinkingEndTime(messageId, 0)
            messageDao.updateMessageState(messageId, MessageState.COMPLETE)
            messageDao.updateMessageCreatedAt(messageId, System.currentTimeMillis())
            triggerSync()
        }

        override suspend fun updateMessageModelType(
            messageId: MessageId,
            modelType: ModelType,
        ) {
            messageDao.updateMessageModelType(messageId, modelType)
            triggerSync()
        }

        override suspend fun createAssistantMessage(
            chatId: ChatId,
            userMessageId: MessageId,
            modelType: ModelType,
            pipelineStep: PipelineStep?,
        ): MessageId {
            val entity =
                MessageEntity(
                    id = MessageId(UUID.randomUUID().toString()),
                    chatId = chatId,
                    content = "",
                    role = Role.ASSISTANT,
                    userMessageId = userMessageId,
                    messageState = MessageState.PROCESSING,
                    modelType = modelType,
                    pipelineStep = pipelineStep,
                    pendingSync = 1,
                )
            messageDao.upsert(SensitiveEntityFieldEncryptors.encrypt(entity, healer))
            triggerSync()
            return entity.id
        }

        override suspend fun createChat(
            chat: Chat,
            scheduleCloudSync: Boolean,
        ): ChatId {
            val entity = SensitiveEntityFieldEncryptors.encrypt(chat.toEntity(), healer)
            chatDao.upsert(entity)
            if (scheduleCloudSync) {
                triggerSync()
            }
            return entity.id
        }

        override suspend fun saveAssistantMessage(
            messageId: MessageId,
            content: String,
            thinkingData: ThinkingData?,
        ) {
            val duration = thinkingData?.thinkingDurationSeconds

            messageDao.updateMessageContent(
                id = messageId,
                content =
                    healer.requireEncrypted(
                        SensitiveFieldRef("MessageEntity", "content", PersistenceBoundary.ROOM),
                        messageId.value,
                        content,
                    ) ?: content,
                thinkingDuration = duration,
                thinkingRaw =
                    thinkingData?.rawFullThought?.let { raw ->
                        healer.requireEncrypted(
                            SensitiveFieldRef("MessageEntity", "thinkingRaw", PersistenceBoundary.ROOM),
                            messageId.value,
                            raw,
                        )
                    },
            )
            triggerSync()
        }

        override suspend fun persistWebSearchSources(
            messageId: MessageId,
            sources: List<WebSearchSource>,
        ) {
            val scopedSources = sources.filter { it.messageId == messageId }
            if (scopedSources.isEmpty()) return
            transactionProvider.runInTransaction {
                webSearchSourceDao.insertAll(
                    scopedSources.map { source ->
                        SensitiveEntityFieldEncryptors.encrypt(source.toEntity(), healer)
                    },
                )
            }
            triggerSync()
        }

        /**
         * Persists all message data atomically in a single transaction.
         * Updates model type, thinking timestamps, thinking content, content, pipeline step, and state.
         */
        override suspend fun persistAllMessageData(
            messageId: MessageId,
            modelType: ModelType,
            thinkingStartTime: Long,
            thinkingEndTime: Long,
            thinkingDuration: Int?,
            thinkingRaw: String?,
            content: String,
            messageState: MessageState,
            pipelineStep: PipelineStep?,
            webSearchSources: List<WebSearchSource>,
            artifacts: List<com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest>,
            generatedMediaUris: List<String>,
            generatedMediaAssetIds: List<String>,
            generatedMediaTypes: List<String>,
            generatedMediaPrompts: List<String>,
            transcriptSegments: List<MessageTranscriptSegment>,
            telemetry: List<com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry>,
        ) {
            val mediaUrisJson =
                if (generatedMediaUris.isNotEmpty()) {
                    Json.encodeToString(generatedMediaUris)
                } else {
                    null
                }

            val mediaAssetIdsJson =
                if (generatedMediaAssetIds.isNotEmpty()) {
                    Json.encodeToString(generatedMediaAssetIds)
                } else {
                    null
                }

            val mediaTypesJson =
                if (generatedMediaTypes.isNotEmpty()) {
                    Json.encodeToString(generatedMediaTypes)
                } else {
                    null
                }

            val mediaPromptsJson =
                if (generatedMediaPrompts.isNotEmpty()) {
                    Json.encodeToString(generatedMediaPrompts)
                } else {
                    null
                }

            transactionProvider.runInTransaction {
                messageDao.persistAllMessageData(
                    MessagePersistPayload(
                        messageId = messageId,
                        modelType = modelType,
                        thinkingStartTime = thinkingStartTime,
                        thinkingEndTime = thinkingEndTime,
                        thinkingDuration = thinkingDuration,
                        thinkingRaw =
                            thinkingRaw?.let { raw ->
                                healer.requireEncrypted(
                                    SensitiveFieldRef("MessageEntity", "thinkingRaw", PersistenceBoundary.ROOM),
                                    messageId.value,
                                    raw,
                                )
                            },
                        content =
                            healer.requireEncrypted(
                                SensitiveFieldRef("MessageEntity", "content", PersistenceBoundary.ROOM),
                                messageId.value,
                                content,
                            ) ?: content,
                        messageState = messageState,
                        pipelineStep = pipelineStep,
                        webSearchSources = webSearchSources.map { SensitiveEntityFieldEncryptors.encrypt(it.toEntity(), healer) },
                        mediaUrisJson =
                            mediaUrisJson?.let { json ->
                                healer.requireEncrypted(
                                    SensitiveFieldRef("MessageEntity", "generatedMediaUrisJson", PersistenceBoundary.ROOM),
                                    messageId.value,
                                    json,
                                )
                            },
                        mediaAssetIdsJson =
                            mediaAssetIdsJson?.let { json ->
                                healer.requireEncrypted(
                                    SensitiveFieldRef("MessageEntity", "generatedMediaAssetIdsJson", PersistenceBoundary.ROOM),
                                    messageId.value,
                                    json,
                                )
                            },
                        mediaTypesJson = mediaTypesJson,
                        mediaPromptsJson =
                            mediaPromptsJson?.let { json ->
                                healer.requireEncrypted(
                                    SensitiveFieldRef("MessageEntity", "generatedMediaPromptsJson", PersistenceBoundary.ROOM),
                                    messageId.value,
                                    json,
                                )
                            },
                    ),
                )

                artifacts.forEach { artifact ->
                    val documentEntity = documentEntityForPersist(messageId, artifact)
                    if (documentEntity != null) {
                        documentDao.insertDocument(documentEntity)
                    }
                }

                val segmentEntities =
                    transcriptSegments.map { segment ->
                        SensitiveEntityFieldEncryptors.encrypt(
                            segment.toEntity(messageId, UUID.randomUUID().toString()),
                            healer,
                        )
                    }
                messageTranscriptSegmentDao.replaceSegmentsForMessage(messageId, segmentEntities)

                messageInferenceTelemetryDao.replaceTelemetryForMessage(
                    messageId = messageId.value,
                    rows = telemetry.map(TurnTelemetry::toEntity),
                )
            }
            triggerSync()
        }

        private suspend fun documentEntityForPersist(
            messageId: MessageId,
            artifact: com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest,
        ): DocumentEntity? {
            if (artifact.content.isBlank()) {
                val existing = documentDao.getDocumentById(artifact.artifactId) ?: return null
                return existing.takeIf { document -> document.json.length <= PRESERVED_BLANK_ARTIFACT_REINSERT_MAX_CHARS }
            }
            return SensitiveEntityFieldEncryptors.encrypt(
                DocumentEntity(
                    id = artifact.artifactId,
                    messageId = messageId,
                    json = artifact.content,
                    title = artifact.title,
                    author = null,
                    targetFormat = artifact.targetFormat,
                    pendingSync = 1,
                ),
                healer,
            )
        }

        override suspend fun updateArtifactExportPreference(
            artifactId: String,
            format: MimeType,
        ) {
            documentDao.updateTargetFormat(artifactId = artifactId, targetFormat = format)
            triggerSync()
        }

        /**
         * Returns messages that have incomplete state for COUNCIL pipeline resume.
         * Filters for PROCESSING, THINKING, or GENERATING state messages.
         */
        override suspend fun getIncompleteCouncilMessages(chatId: ChatId): List<Message> {
            val incompleteStates =
                listOf(
                    MessageState.PROCESSING,
                    MessageState.THINKING,
                    MessageState.GENERATING,
                )
            val entities = messageDao.getMessagesByStates(chatId, incompleteStates)
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out =
                entities.map { msgWithFiles ->
                    val decryptedMsg = SensitiveEntityFieldEncryptors.decrypt(msgWithFiles.message, healer, repairs)
                    val decryptedFiles =
                        msgWithFiles.files.map { SensitiveEntityFieldEncryptors.decrypt(it, healer, repairs) }
                    msgWithFiles.copy(message = decryptedMsg, files = decryptedFiles).toDomain()
                }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun deleteChat(chatId: ChatId) {
            transactionProvider.runInTransaction {
                messageDao.softDeleteAllActiveByChatId(chatId)
                messageFileDao.softDeleteByChatId(chatId)
                val rowsAffected = chatDao.softDeleteById(chatId)
                require(rowsAffected > 0) { "Chat not found with id: $chatId" }
                // Soft-delete related generated documents so sync doesn't resurrect them later.
                documentDao.softDeleteByChatId(chatId)
            }
            triggerSync()
        }

        override suspend fun deleteAllChats() {
            transactionProvider.runInTransaction {
                cleanupDao.deleteChats()
                cleanupDao.deleteMessages()
                cleanupDao.deleteMessageTranscriptSegments()
                cleanupDao.deleteChatSummaries()
                cleanupDao.deleteMessageFiles()
                cleanupDao.deleteDocuments()
                cleanupDao.deleteVisionAnalysis()
                cleanupDao.deleteWebSearchSources()
                cleanupDao.deleteEmbeddings()
            }
            syncTrigger.triggerCloudChatsPurge()
        }

        override suspend fun renameChat(
            chatId: ChatId,
            newName: String,
        ) {
            val encryptedName =
                healer.requireEncrypted(
                    SensitiveFieldRef("ChatEntity", "name", PersistenceBoundary.ROOM),
                    chatId.value,
                    newName,
                ) ?: newName
            val rowsAffected = chatDao.updateName(chatId, encryptedName)
            require(rowsAffected > 0) { "Chat not found with id: $chatId" }
            triggerSync()
        }

        override fun searchChats(
            query: String,
            messageIds: List<MessageId>,
        ): Flow<List<Chat>> =
            chatDao.searchChats(query, messageIds).mapLatest { entities ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val out = entities.map { SensitiveEntityFieldEncryptors.decrypt(it, healer, repairs).toDomain() }
                readRepairApplier.apply(repairs)
                out
            }

        override suspend fun getChatsByIds(ids: List<ChatId>): Map<ChatId, Chat> {
            if (ids.isEmpty()) return emptyMap()
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out =
                chatDao.getChatsByIds(ids).associate {
                    it.id to SensitiveEntityFieldEncryptors.decrypt(it, healer, repairs).toDomain()
                }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun markWebSearchSourceExtracted(url: String) {
            webSearchSourceDao.markExtracted(url)
            triggerSync()
        }

        override suspend fun markWebSearchSourcesExtracted(urls: List<String>) {
            urls.forEach { webSearchSourceDao.markExtracted(it) }
            triggerSync()
        }

        private data class ChatStreamFrame(
            val messages: List<MessageWithFiles>,
            val webSearchSources: List<WebSearchSourceEntity>,
            val documents: List<DocumentEntity>,
            val segmentRows: List<MessageTranscriptSegmentEntity>,
        )

        private companion object {
            const val PRESERVED_BLANK_ARTIFACT_REINSERT_MAX_CHARS = 50
        }
    }
