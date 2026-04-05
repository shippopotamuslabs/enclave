package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.openai.client.OpenAIClient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

class ApiInferenceServiceImpl(
    private val client: OpenAIClient,
    private val modelId: String,
    private val provider: String, // E.g., "OPENAI", "ANTHROPIC"
    private val modelType: ModelType
) : LlmInferencePort {

    private val conversationHistory = mutableListOf<ChatMessage>()

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
        return sendPrompt(prompt, GenerationOptions(reasoningBudget = 0), closeConversation)
    }

    override fun sendPrompt(
        prompt: String,
        options: GenerationOptions,
        closeConversation: Boolean
    ): Flow<InferenceEvent> = flow {
        try {
            val params = OpenAiRequestMapper.mapToResponseParams(
                modelId = modelId,
                prompt = prompt,
                history = conversationHistory,
                options = options
            )

            client.chat().completions().createStreaming(params).use { streamResponse ->
                val iterator = streamResponse.stream().iterator()
                var finishedEmitted = false
                while (iterator.hasNext()) {
                    val event = iterator.next()
                    val choices = event.choices()
                    if (choices.isNotEmpty()) {
                        val choice = choices[0]
                        val delta = choice.delta()
                        
                        if (delta.content().isPresent) {
                            val text = delta.content().get()
                            if (text.isNotEmpty()) {
                                emit(InferenceEvent.PartialResponse(text, modelType))
                            }
                        }
                        
                        if (choice.finishReason().isPresent) {
                            emit(InferenceEvent.Finished(modelType))
                            finishedEmitted = true
                        }
                    }
                }
                
                if (!finishedEmitted) {
                    emit(InferenceEvent.Finished(modelType))
                }
            }
            
            // Append the new messages to history if we're keeping it
            conversationHistory.add(ChatMessage(com.browntowndev.pocketcrew.domain.model.chat.Role.USER, prompt))
            // Note: We'd need to append the assistant's full response if we were fully managing history here, 
            // but the domain usually re-hydrates history via setHistory.
            
            if (closeConversation) {
                closeSession()
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            emit(InferenceEvent.Error(Exception("API Error ($provider): $errorMsg", e), modelType))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun setHistory(messages: List<ChatMessage>) {
        conversationHistory.clear()
        conversationHistory.addAll(messages)
    }

    override suspend fun closeSession() {
        conversationHistory.clear()
    }
}
