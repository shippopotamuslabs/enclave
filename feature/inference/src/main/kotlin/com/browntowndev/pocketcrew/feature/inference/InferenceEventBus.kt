package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
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

    // Pipeline streams (Crew/MOA multi-step inference)
    private val pipelineStreams = ConcurrentHashMap<String, MutableSharedFlow<MessageGenerationState>>()

    fun openChatRequest(key: ChatRequestKey): Flow<MessageGenerationState> {
        val stream = newChatStream()
        chatStreams[key] = stream
        return stream.asSharedFlow().onCompletion {
            if (chatStreams[key] === stream) {
                chatStreams.remove(key)
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

    fun clearChatRequest(key: ChatRequestKey) {
        chatStreams.remove(key)
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

    private companion object {
        private const val CHAT_REPLAY_CACHE_SIZE = 64
        private const val CHAT_EXTRA_BUFFER_CAPACITY = 1024
    }
}
