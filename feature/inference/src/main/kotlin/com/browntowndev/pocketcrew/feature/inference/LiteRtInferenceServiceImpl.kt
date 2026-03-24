package com.browntowndev.pocketcrew.feature.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase.SegmentKind
import com.browntowndev.pocketcrew.feature.inference.llama.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LiteRtInferenceServiceImpl @Inject constructor(
    private val conversationManager: ConversationManagerPort,
    private val processThinkingTokens: ProcessThinkingTokensUseCase,
    private val modelType: ModelType,
) : LlmInferencePort {
    companion object {
        private const val TAG = "LiteRtInferenceService"
    }

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> = flow {
        var isThinking = false
        val accumulatedThought = StringBuilder()
        val accumulatedText = StringBuilder()
        var buffer = ""

        try {
            val conversation = conversationManager.getConversation()
            Log.d(TAG, "Sending prompt: $prompt")

            conversation.sendMessageAsync(prompt).collect { chunk ->
                val state = processThinkingTokens(buffer, chunk, isThinking)
                buffer = state.buffer
                isThinking = state.isThinking

                // Process each emitted segment with its proper type
                state.emittedSegments.forEach { segment ->
                    when (segment.kind) {
                        SegmentKind.THINKING -> {
                            accumulatedThought.append(segment.text)
                            emit(InferenceEvent.Thinking(segment.text, modelType))
                        }
                        SegmentKind.VISIBLE -> {
                            accumulatedText.append(segment.text)
                            emit(InferenceEvent.PartialResponse(segment.text, modelType))
                        }
                    }
                }
            }

            // Handle remaining buffer
            if (buffer.isNotEmpty()) {
                if (isThinking) {
                    accumulatedThought.append(buffer)
                    emit(InferenceEvent.Thinking(buffer, modelType))
                } else {
                    accumulatedText.append(buffer)
                    emit(InferenceEvent.PartialResponse(buffer, modelType))
                }
            }

            emit(InferenceEvent.Finished(modelType))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending prompt", e)
            emit(InferenceEvent.Error(e, modelType))
        } finally {
            if (closeConversation) {
                conversationManager.closeConversation()
            }
        }
    }

    override fun closeSession() {
        conversationManager.closeConversation()
        conversationManager.closeEngine()
    }

    override suspend fun setHistory(messages: List<DomainChatMessage>) {
        // LiteRT uses ConversationManager which handles its own history
        // This is a no-op for now - LiteRT may have different persistence needs
        Log.d(TAG, "setHistory called with ${messages.size} messages - not implemented for LiteRT")
    }
}
