package com.browntowndev.pocketcrew.core.data.repository
import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.DocumentDao
import com.browntowndev.pocketcrew.core.data.local.DocumentEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageFileEntity
import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptSegmentEntity
import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.pocketcrew.core.data.local.MessageWithFiles
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceEntity
import com.browntowndev.pocketcrew.core.data.mapper.toDomain
import com.browntowndev.pocketcrew.core.data.mapper.toEntity
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.model.chat.WebSearchSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
        private val transactionProvider: com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider,
        private val localEncryption: LocalDataEncryptionService,
    ) : ChatRepository {
        private fun triggerSync() = syncTrigger.trigger()

        override fun getAllChats(): Flow<List<Chat>> =
            chatDao.getAllChats().map { entities ->
                entities.map { it.decryptSensitiveFields().toDomain() }
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
                val sourcesByMessageId = webSearchSources.groupBy { it.messageId }
                val documentsByMessageId = documents.groupBy { it.messageId }
                val segmentsByMessageId =
                    segmentRows
                        .groupBy { it.messageId }
                        .mapValues { (_, rows) ->
                            rows.sortedBy { it.ordinal }.map { it.decryptSensitiveFields().toDomain() }
                        }
                messages.map {
                    it.decryptSensitiveFields().toDomain(
                        webSearchSources =
                            sourcesByMessageId[it.message.id]
                                ?.map { source -> source.decryptSensitiveFields() }
                                ?: emptyList(),
                        documents =
                            documentsByMessageId[it.message.id]
                                ?.map { document -> document.decryptSensitiveFields() }
                                ?: emptyList(),
                        transcriptSegments = segmentsByMessageId[it.message.id] ?: emptyList(),
                    )
                }
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
            messageDao.updateMessageContentText(messageId, localEncryption.encryptString(content))
            triggerSync()
        }

        override suspend fun appendMessageContent(
            messageId: MessageId,
            content: String,
        ) {
            val existing = messageDao.getMessageById(messageId)?.message
                ?: throw IllegalArgumentException("Message not found with id: $messageId")
            val merged = localEncryption.decryptString(existing.content) + content
            messageDao.updateMessageContentText(messageId, localEncryption.encryptString(merged))
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
            val existing = messageDao.getMessageById(messageId)?.message
                ?: throw IllegalArgumentException("Message not found with id: $messageId")
            val merged = existing.thinkingRaw?.let(localEncryption::decryptString).orEmpty() + thinkingText
            messageDao.updateThinkingRaw(messageId, localEncryption.encryptString(merged))
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
            messageDao.upsert(entity.encryptSensitiveFields())
            triggerSync()
            return entity.id
        }

        override suspend fun createChat(chat: Chat): ChatId {
            val entity = chat.toEntity().encryptSensitiveFields()
            chatDao.upsert(entity)
            triggerSync()
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
                content = localEncryption.encryptString(content),
                thinkingDuration = duration,
                thinkingRaw = thinkingData?.rawFullThought?.let(localEncryption::encryptString),
            )
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
            artifacts: List<com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest>,
            generatedMediaUris: List<String>,
            generatedMediaAssetIds: List<String>,
            generatedMediaTypes: List<String>,
            generatedMediaPrompts: List<String>,
            transcriptSegments: List<MessageTranscriptSegment>,
        ) {
            val documentEntities =
                artifacts.map { artifact ->
                    DocumentEntity(
                        id = artifact.artifactId,
                        messageId = messageId,
                        json = localEncryption.encryptString(artifact.content),
                        title = localEncryption.encryptString(artifact.title),
                        author = null,
                        targetFormat = artifact.targetFormat,
                        pendingSync = 1,
                    )
                }

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
                    messageId = messageId,
                    modelType = modelType,
                    thinkingStartTime = thinkingStartTime,
                    thinkingEndTime = thinkingEndTime,
                    thinkingDuration = thinkingDuration,
                    thinkingRaw = thinkingRaw?.let(localEncryption::encryptString),
                    content = localEncryption.encryptString(content),
                    messageState = messageState,
                    pipelineStep = pipelineStep,
                    webSearchSources = webSearchSources.map { it.toEntity().encryptSensitiveFields() },
                    mediaUrisJson = mediaUrisJson?.let(localEncryption::encryptString),
                    mediaAssetIdsJson = mediaAssetIdsJson?.let(localEncryption::encryptString),
                    mediaTypesJson = mediaTypesJson,
                    mediaPromptsJson = mediaPromptsJson?.let(localEncryption::encryptString),
                )

                // Soft-delete all previous artifacts attached to this assistant message,
                // then upsert the current set by stable `artifactId`.
                documentDao.softDeleteByMessageId(messageId)
                documentEntities.forEach { documentDao.insertDocument(it) }

                val segmentEntities =
                    transcriptSegments.map { segment ->
                        segment.toEntity(messageId, UUID.randomUUID().toString()).encryptSensitiveFields()
                    }
                messageTranscriptSegmentDao.replaceSegmentsForMessage(messageId, segmentEntities)
            }
            triggerSync()
        }

        override suspend fun updateArtifactExportPreference(
            artifactId: String,
            format: MimeType,
        ) {
            documentDao.updateTargetFormat(artifactId = artifactId, targetFormat = format)
            triggerSync()
        }

        /**
         * Returns messages that have incomplete state for CREW pipeline resume.
         * Filters for PROCESSING, THINKING, or GENERATING state messages.
         */
        override suspend fun getIncompleteCrewMessages(chatId: ChatId): List<Message> {
            val incompleteStates =
                listOf(
                    MessageState.PROCESSING,
                    MessageState.THINKING,
                    MessageState.GENERATING,
                )
            val entities = messageDao.getMessagesByStates(chatId, incompleteStates)
            return entities.map { it.decryptSensitiveFields().toDomain() }
        }

        override suspend fun deleteChat(chatId: ChatId) {
            val rowsAffected = chatDao.softDeleteById(chatId)
            require(rowsAffected > 0) { "Chat not found with id: $chatId" }
            // Soft-delete related generated documents so sync doesn't resurrect them later.
            documentDao.softDeleteByChatId(chatId)
            triggerSync()
        }

        override suspend fun renameChat(
            chatId: ChatId,
            newName: String,
        ) {
            val rowsAffected = chatDao.updateName(chatId, localEncryption.encryptString(newName))
            require(rowsAffected > 0) { "Chat not found with id: $chatId" }
            triggerSync()
        }

        override fun searchChats(
            query: String,
            messageIds: List<MessageId>,
        ): Flow<List<Chat>> =
            chatDao.searchChats(query, messageIds).map { entities ->
                entities.map { it.decryptSensitiveFields().toDomain() }
            }

        override suspend fun getChatsByIds(ids: List<ChatId>): Map<ChatId, Chat> {
            if (ids.isEmpty()) return emptyMap()
            return chatDao.getChatsByIds(ids).associate { it.id to it.decryptSensitiveFields().toDomain() }
        }

        override suspend fun markWebSearchSourceExtracted(url: String) {
            webSearchSourceDao.markExtracted(url)
            triggerSync()
        }

        override suspend fun markWebSearchSourcesExtracted(urls: List<String>) {
            urls.forEach { webSearchSourceDao.markExtracted(it) }
            triggerSync()
        }

        private fun com.browntowndev.pocketcrew.core.data.local.ChatEntity.encryptSensitiveFields() =
            copy(name = localEncryption.encryptString(name))

        private fun com.browntowndev.pocketcrew.core.data.local.ChatEntity.decryptSensitiveFields() =
            copy(name = localEncryption.decryptString(name))

        private fun MessageEntity.encryptSensitiveFields() =
            copy(
                content = localEncryption.encryptString(content),
                imageUrisJson = imageUrisJson?.let(localEncryption::encryptString),
                thinkingRaw = thinkingRaw?.let(localEncryption::encryptString),
                artifactsJson = artifactsJson?.let(localEncryption::encryptString),
                generatedMediaUrisJson = generatedMediaUrisJson?.let(localEncryption::encryptString),
                generatedMediaAssetIdsJson = generatedMediaAssetIdsJson?.let(localEncryption::encryptString),
                generatedMediaPromptsJson = generatedMediaPromptsJson?.let(localEncryption::encryptString),
            )

        private fun MessageEntity.decryptSensitiveFields() =
            copy(
                content = localEncryption.decryptString(content),
                imageUrisJson = imageUrisJson?.let(localEncryption::decryptString),
                thinkingRaw = thinkingRaw?.let(localEncryption::decryptString),
                artifactsJson = artifactsJson?.let(localEncryption::decryptString),
                generatedMediaUrisJson = generatedMediaUrisJson?.let(localEncryption::decryptString),
                generatedMediaAssetIdsJson = generatedMediaAssetIdsJson?.let(localEncryption::decryptString),
                generatedMediaPromptsJson = generatedMediaPromptsJson?.let(localEncryption::decryptString),
            )

        private fun MessageWithFiles.decryptSensitiveFields() =
            copy(
                message = message.decryptSensitiveFields(),
                files = files.map { it.decryptSensitiveFields() },
            )

        private fun MessageFileEntity.decryptSensitiveFields() =
            copy(
                name = localEncryption.decryptString(name),
                content = localEncryption.decryptString(content),
                remoteUrl = remoteUrl?.let(localEncryption::decryptString),
                stagingRemoteUrl = stagingRemoteUrl?.let(localEncryption::decryptString),
            )

        private fun WebSearchSourceEntity.encryptSensitiveFields() =
            copy(
                title = localEncryption.encryptString(title),
                url = localEncryption.encryptString(url),
                content = localEncryption.encryptString(content),
            )

        private fun WebSearchSourceEntity.decryptSensitiveFields() =
            copy(
                title = localEncryption.decryptString(title),
                url = localEncryption.decryptString(url),
                content = localEncryption.decryptString(content),
            )

        private fun DocumentEntity.decryptSensitiveFields() =
            copy(
                json = localEncryption.decryptString(json),
                title = localEncryption.decryptString(title),
                author = author?.let(localEncryption::decryptString),
            )

        private fun MessageTranscriptSegmentEntity.encryptSensitiveFields() =
            copy(text = localEncryption.encryptString(text))

        private fun MessageTranscriptSegmentEntity.decryptSensitiveFields() =
            copy(text = localEncryption.decryptString(text))
    }
