package com.browntowndev.enclave.core.data.local.dao.message

import androidx.room.withTransaction
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.domain.model.chat.MessageState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageDaoPersist
    @Inject
    constructor(
        private val database: EnclaveDatabase,
        private val commands: MessageCommandDao,
        private val syncCommands: MessageSyncCommandDao,
    ) {
        suspend fun persist(payload: MessagePersistPayload) {
            database.withTransaction {
                persistInTransaction(payload)
            }
        }

        private suspend fun persistInTransaction(payload: MessagePersistPayload) {
            val messageId = payload.messageId
            val timestamp = payload.timestamp
            commands.updateMessageModelType(messageId, payload.modelType, timestamp)
            if (payload.thinkingStartTime > 0) {
                commands.updateThinkingStartTime(messageId, payload.thinkingStartTime, timestamp)
            }
            if (payload.thinkingEndTime > 0) {
                commands.updateThinkingEndTime(messageId, payload.thinkingEndTime, timestamp)
            }
            if (payload.thinkingDuration != null) {
                commands.updateThinkingDuration(messageId, payload.thinkingDuration, timestamp)
            }
            commands.updateThinkingRaw(messageId, payload.thinkingRaw, timestamp)
            commands.updateMessageContentText(messageId, payload.content, timestamp)
            commands.updateMessagePipelineStep(messageId, payload.pipelineStep, timestamp)
            commands.updateMessageState(messageId, payload.messageState, timestamp)
            commands.updateMessageMedia(
                id = messageId,
                mediaUrisJson = payload.mediaUrisJson,
                mediaAssetIdsJson = payload.mediaAssetIdsJson,
                mediaTypesJson = payload.mediaTypesJson,
                media_prompts_json = payload.mediaPromptsJson,
                timestamp = timestamp,
            )
            if (payload.webSearchSources.isNotEmpty()) {
                syncCommands.insertWebSearchSources(payload.webSearchSources)
            }
            if (payload.messageState == MessageState.COMPLETE) {
                commands.updateMessageCreatedAt(messageId, System.currentTimeMillis())
            }
        }
    }
