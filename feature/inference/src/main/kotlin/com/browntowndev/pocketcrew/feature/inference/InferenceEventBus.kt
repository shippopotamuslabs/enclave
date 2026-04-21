package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.usecase.chat.AccumulatedMessages
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onCompletion

@Singleton
class InferenceEventBus @Inject constructor() {
    data class ChatRequestKey(
        val chatId: ChatId,
        val assistantMessageId: MessageId,
    )

    private val chatStreams = ConcurrentHashMap<ChatRequestKey, MutableSharedFlow<MessageGenerationState>>()
    private val chatSnapshots = ConcurrentHashMap<ChatRequestKey, MutableSharedFlow<AccumulatedMessages>>()

    // Pipeline streams (Crew/MOA multi-step inference)
    private val pipelineStreams = ConcurrentHashMap<String, MutableSharedFlow<MessageGenerationState>>()

    fun openChatRequest(key: ChatRequestKey): Flow<MessageGenerationState> {
        val stream = newChatStream()
        chatStreams[key] = stream
        chatSnapshots.remove(key)
        return stream.asSharedFlow().onCompletion {
            if (chatStreams[key] === stream) {
                chatStreams.remove(key)
                chatSnapshots.remove(key)
            }
        }
    }

    fun observeChatRequest(key: ChatRequestKey): Flow<MessageGenerationState> {
        return streamFor(key).asSharedFlow()
    }

    suspend fun emitChatState(key: ChatRequestKey, state: MessageGenerationState) {
        streamFor(key).emit(state)
    }

    fun tryEmitChatState(key: ChatRequestKey, state: MessageGenerationState): Boolean {
        return streamFor(key).tryEmit(state)
    }

    fun observeChatSnapshot(key: ChatRequestKey): Flow<AccumulatedMessages> {
        return snapshotStreamFor(key).asSharedFlow()
    }

    suspend fun emitChatSnapshot(key: ChatRequestKey, snapshot: AccumulatedMessages) {
        snapshotStreamFor(key).emit(snapshot)
    }

    fun tryEmitChatSnapshot(key: ChatRequestKey, snapshot: AccumulatedMessages): Boolean {
        return snapshotStreamFor(key).tryEmit(snapshot)
    }

    fun clearChatRequest(key: ChatRequestKey) {
        chatStreams.remove(key)
        chatSnapshots.remove(key)
    }

    fun openPipelineRequest(chatId: String): Flow<MessageGenerationState> {
        val stream = newChatStream()
        pipelineStreams[chatId] = stream
        return stream.asSharedFlow().onCompletion {
            if (pipelineStreams[chatId] === stream) {
                pipelineStreams.remove(chatId)
            }
        }
    }

    suspend fun emitPipelineState(chatId: String, state: MessageGenerationState) {
        pipelineStreamFor(chatId).emit(state)
    }

    fun tryEmitPipelineState(chatId: String, state: MessageGenerationState): Boolean {
        return pipelineStreamFor(chatId).tryEmit(state)
    }

    fun clearPipelineRequest(chatId: String) {
        pipelineStreams.remove(chatId)
    }

    /** Returns `true` if a pipeline stream exists for the given [chatId]. Test-only. */
    fun hasPipelineStream(chatId: String): Boolean = pipelineStreams.containsKey(chatId)

    /** Returns `true` if a chat stream exists for the given [key]. Test-only. */
    fun hasChatRequest(key: ChatRequestKey): Boolean = chatStreams.containsKey(key)

    private fun streamFor(key: ChatRequestKey): MutableSharedFlow<MessageGenerationState> {
        return chatStreams.computeIfAbsent(key) { newChatStream() }
    }

    private fun snapshotStreamFor(key: ChatRequestKey): MutableSharedFlow<AccumulatedMessages> {
        return chatSnapshots.computeIfAbsent(key) { newSnapshotStream() }
    }

    private fun pipelineStreamFor(chatId: String): MutableSharedFlow<MessageGenerationState> {
        return pipelineStreams.computeIfAbsent(chatId) { newChatStream() }
    }

    private fun newChatStream(): MutableSharedFlow<MessageGenerationState> {
        return MutableSharedFlow(
            replay = CHAT_REPLAY_CACHE_SIZE,
            extraBufferCapacity = CHAT_EXTRA_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }

    private fun newSnapshotStream(): MutableSharedFlow<AccumulatedMessages> {
        return MutableSharedFlow(
            replay = SNAPSHOT_REPLAY_CACHE_SIZE,
            extraBufferCapacity = SNAPSHOT_EXTRA_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }

    private companion object {
        private const val CHAT_REPLAY_CACHE_SIZE = 64
        private const val CHAT_EXTRA_BUFFER_CAPACITY = 1024
        private const val SNAPSHOT_REPLAY_CACHE_SIZE = 1
        private const val SNAPSHOT_EXTRA_BUFFER_CAPACITY = 64
    }
}
