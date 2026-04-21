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

@Singleton
class InferenceEventBus @Inject constructor() {
    data class ChatRequestKey(
        val chatId: ChatId,
        val assistantMessageId: MessageId,
    )

    private val chatStreams = ConcurrentHashMap<ChatRequestKey, MutableSharedFlow<MessageGenerationState>>()
    private val chatSnapshots = ConcurrentHashMap<ChatRequestKey, MutableSharedFlow<AccumulatedMessages>>()

    fun openChatRequest(key: ChatRequestKey): Flow<MessageGenerationState> {
        val stream = newChatStream()
        chatStreams[key] = stream
        chatSnapshots.remove(key)
        return stream.asSharedFlow()
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

    private fun streamFor(key: ChatRequestKey): MutableSharedFlow<MessageGenerationState> {
        return chatStreams.computeIfAbsent(key) { newChatStream() }
    }

    private fun snapshotStreamFor(key: ChatRequestKey): MutableSharedFlow<AccumulatedMessages> {
        return chatSnapshots.computeIfAbsent(key) { newSnapshotStream() }
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
