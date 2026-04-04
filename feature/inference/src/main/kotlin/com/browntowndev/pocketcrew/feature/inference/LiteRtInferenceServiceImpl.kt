package com.browntowndev.pocketcrew.feature.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase.SegmentKind
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

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
        return sendPrompt(prompt, GenerationOptions(reasoningBudget = 0, modelType = modelType), closeConversation)
    }

    override suspend fun closeSession() {
        conversationManager.closeConversation()
        conversationManager.closeEngine()
    }

    override suspend fun setHistory(messages: List<DomainChatMessage>) {
        conversationManager.setHistory(messages)
    }

    override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> = flow {
        val targetModelType = options.modelType ?: ModelType.FAST
        var isThinking = false
        val accumulatedThought = StringBuilder()
        val accumulatedText = StringBuilder()
        var buffer = ""

        try {
            val conversation = conversationManager.getConversation(targetModelType, options)
            Log.d(TAG, "Sending prompt with options: $prompt")

            conversation.sendMessageAsync(prompt, options).collect { response ->
                if (response.thought.isNotEmpty()) {
                    emit(InferenceEvent.Thinking(response.thought, targetModelType))
                }
                
                if (response.text.isNotEmpty()) {
                    val state = processThinkingTokens(buffer, response.text, isThinking)
                    buffer = state.buffer
                    isThinking = state.isThinking

                    state.emittedSegments.forEach { segment ->
                        when (segment.kind) {
                            SegmentKind.THINKING -> {
                                accumulatedThought.append(segment.text)
                                emit(InferenceEvent.Thinking(segment.text, targetModelType))
                            }
                            SegmentKind.VISIBLE -> {
                                accumulatedText.append(segment.text)
                                emit(InferenceEvent.PartialResponse(segment.text, targetModelType))
                            }
                        }
                    }
                }
            }

            if (buffer.isNotEmpty()) {
                if (isThinking) {
                    accumulatedThought.append(buffer)
                    emit(InferenceEvent.Thinking(buffer, targetModelType))
                } else {
                    accumulatedText.append(buffer)
                    emit(InferenceEvent.PartialResponse(buffer, targetModelType))
                }
            }

            emit(InferenceEvent.Finished(targetModelType))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending prompt", e)
            emit(InferenceEvent.Error(e, targetModelType))
        } finally {
            if (closeConversation) {
                conversationManager.closeConversation()
            }
        }
    }
}
