package com.browntowndev.pocketcrew.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.inference.llama.GenerationEvent
import com.browntowndev.pocketcrew.inference.llama.LlamaChatSessionManager
import com.browntowndev.pocketcrew.inference.llama.LlamaModelConfig
import com.browntowndev.pocketcrew.inference.llama.LlamaSamplingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Implementation of LlmInferencePort using llama.cpp via JNI.
 * Bridges the llama.cpp GenerationEvent to the domain's InferenceEvent.
 */
class LlamaInferenceServiceImpl @Inject constructor(
    private val sessionManager: LlamaChatSessionManager
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

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
        val path = modelPath
        if (path == null) {
            return kotlinx.coroutines.flow.flow {
                emit(InferenceEvent.Error(IllegalStateException("Model not configured. Call configure() first.")))
            }
        }

        return kotlinx.coroutines.flow.flow {
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

            // Send user message
            sessionManager.sendUserMessage(prompt)

            // Stream the response and map to InferenceEvent
            sessionManager.streamAssistantResponse().collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        // Emit as partial response for streaming
                        emit(InferenceEvent.PartialResponse(event.text))
                    }
                    is GenerationEvent.Completed -> {
                        // Emit completed with the full response
                        emit(InferenceEvent.Completed(
                            finalResponse = event.fullText,
                            rawFullThought = null
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
        }.catch { e ->
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
