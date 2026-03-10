package com.browntowndev.pocketcrew.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase.SegmentKind
import com.browntowndev.pocketcrew.inference.llama.GenerationEvent
import com.browntowndev.pocketcrew.inference.llama.LlamaChatSessionManager
import com.browntowndev.pocketcrew.inference.llama.LlamaModelConfig
import com.browntowndev.pocketcrew.inference.llama.LlamaSamplingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Implementation of LlmInferencePort using llama.cpp via JNI.
 * Bridges the llama.cpp GenerationEvent to the domain's InferenceEvent.
 */
class LlamaInferenceServiceImpl @Inject constructor(
    private val sessionManager: LlamaChatSessionManager,
    private val processThinkingTokens: ProcessThinkingTokensUseCase,
) : LlmInferencePort {

    companion object {
        private const val TAG = "LlamaInferenceService"
    }

    private var modelPath: String? = null
    private var systemPrompt: String = "You are a helpful assistant."
    private var samplingConfig: LlamaSamplingConfig = LlamaSamplingConfig()
    private var isInitialized = false

    /**
     * Configure the service with model path and optional parameters.
     * Must be called before sending prompts.
     */
    fun configure(
        modelPath: String,
        systemPrompt: String = "You are a helpful assistant.",
        samplingConfig: LlamaSamplingConfig = LlamaSamplingConfig()
    ) {
        this.modelPath = modelPath
        this.systemPrompt = systemPrompt
        this.samplingConfig = samplingConfig
    }

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> = flow {
        val path = modelPath
        if (path == null) {
            emit(InferenceEvent.Error(IllegalStateException("Model not configured. Call configure() first.")))
            return@flow
        }

        // Initialize engine if not yet done
        if (!isInitialized) {
            sessionManager.initializeEngine(
                LlamaModelConfig(
                    modelPath = path,
                    systemPrompt = systemPrompt,
                    sampling = samplingConfig
                )
            )
            sessionManager.startNewConversation()
            isInitialized = true
        }

        var isThinking = false
        val accumulatedThought = StringBuilder()
        val accumulatedText = StringBuilder()
        var buffer = ""

        try {
            // Send user message
            sessionManager.sendUserMessage(prompt)

            // Stream the response and map to InferenceEvent
            sessionManager.streamAssistantResponse().collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        // Process thinking tokens to separate thinking vs answering states
                        val state = processThinkingTokens(buffer, event.text, isThinking)
                        buffer = state.buffer
                        isThinking = state.isThinking

                        // Process each emitted segment with its proper type
                        state.emittedSegments.forEach { segment ->
                            when (segment.kind) {
                                SegmentKind.THINKING -> {
                                    accumulatedThought.append(segment.text)
                                    emit(InferenceEvent.Thinking(segment.text, accumulatedThought.toString()))
                                }
                                SegmentKind.VISIBLE -> {
                                    accumulatedText.append(segment.text)
                                    emit(InferenceEvent.PartialResponse(segment.text))
                                }
                            }
                        }
                    }
                    is GenerationEvent.Completed -> {
                        // Handle remaining buffer
                        if (buffer.isNotEmpty()) {
                            if (isThinking) {
                                accumulatedThought.append(buffer)
                                emit(InferenceEvent.Thinking(buffer, accumulatedThought.toString()))
                            } else {
                                accumulatedText.append(buffer)
                                emit(InferenceEvent.PartialResponse(buffer))
                            }
                        }

                        // Emit completed with the full response
                        emit(InferenceEvent.Completed(
                            finalResponse = accumulatedText.toString(),
                            rawFullThought = accumulatedThought.toString().takeIf { it.isNotEmpty() }
                        ))
                    }
                    is GenerationEvent.Error -> {
                        emit(InferenceEvent.Error(event.throwable))
                    }
                }
            }

            // Close conversation if requested
            if (closeConversation) {
                sessionManager.clearConversation()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            emit(InferenceEvent.Error(e))
        }
    }

    override fun closeSession() {
        runBlocking {
            try {
                sessionManager.shutdown()
                isInitialized = false
            } catch (e: Exception) {
                Log.w(TAG, "Error shutting down session", e)
            }
        }
    }

    /**
     * Clear the conversation but keep the model loaded.
     * Used between pipeline steps to avoid reloading the model.
     */
    fun clearConversation() {
        runBlocking {
            try {
                sessionManager.clearConversation()
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing conversation", e)
            }
        }
    }
}
