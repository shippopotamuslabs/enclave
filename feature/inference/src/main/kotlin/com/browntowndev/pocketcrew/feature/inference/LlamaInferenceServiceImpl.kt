package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase.SegmentKind
import com.browntowndev.pocketcrew.feature.inference.llama.ChatMessage
import com.browntowndev.pocketcrew.feature.inference.llama.ChatRole
import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.LlamaSamplingConfig
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Implementation of LlmInferencePort using llama.cpp via JNI.
 * Bridges the llama.cpp GenerationEvent to the domain's InferenceEvent.
 * Resolves the active model from [LocalModelRepositoryPort] at runtime, eliminating
 * the need for explicit configure() calls at provision time.
 */
class LlamaInferenceServiceImpl @Inject constructor(
    private val sessionManager: LlamaChatSessionManager,
    private val processThinkingTokens: ProcessThinkingTokensUseCase,
    private val loggingPort: LoggingPort,
    private val localModelRepository: LocalModelRepositoryPort,
    private val activeModelProvider: ActiveModelProviderPort,
    @ApplicationContext private val context: Context,
    private val modelType: ModelType,
) : LlmInferencePort {

    companion object {
        private const val TAG = "LlamaInferenceService"
    }

    private data class SessionSignature(
        val modelId: Long,
        val configId: Long?,
        val reasoningBudget: Int,
        val temperature: Float,
        val topK: Int,
        val topP: Float,
        val maxTokens: Int,
    )

    private var currentSignature: SessionSignature? = null
    private var history: List<DomainChatMessage> = emptyList()
    private var isInitialized = false
    private var hasTriedCpuFallback = false

    private fun getModelPath(filename: String): String {
        // Use getExternalFilesDir to match ModelFileScanner's directory choice
        val modelsDir = java.io.File(context.getExternalFilesDir(null), "models")
        return java.io.File(modelsDir, filename).absolutePath
    }

    /**
     * Ensures the engine is loaded with the current model from registry.
     * If the model or config has changed since last load, tears down and reloads.
     */
    private suspend fun ensureModelLoaded(modelType: ModelType, options: GenerationOptions) {
        val activeConfig = activeModelProvider.getActiveConfiguration(modelType)
            ?: throw IllegalStateException("No active configuration for $modelType")

        if (!activeConfig.isLocal) {
            throw IllegalStateException("LlamaInferenceService cannot run API models. ModelType $modelType is mapped to an API configuration.")
        }

        val asset = localModelRepository.getAssetByConfigId(activeConfig.id)
            ?: throw IllegalStateException("No registered asset for config ${activeConfig.id}. Download a model first.")

        val targetReasoningBudget = options.reasoningBudget
        val targetTemperature = options.temperature ?: activeConfig.temperature?.toFloat() ?: 0.7f
        val targetTopK = options.topK ?: activeConfig.topK ?: 40
        val targetTopP = options.topP ?: activeConfig.topP?.toFloat() ?: 0.9f
        val targetMaxTokens = options.maxTokens ?: activeConfig.maxTokens ?: 4096

        val newSignature = SessionSignature(
            modelId = asset.metadata.id,
            configId = activeConfig.id,
            reasoningBudget = targetReasoningBudget,
            temperature = targetTemperature,
            topK = targetTopK,
            topP = targetTopP,
            maxTokens = targetMaxTokens,
        )

        if (isInitialized && currentSignature == newSignature) {
            // Only update history if the model/options haven't changed
            sessionManager.setHistory(history)
            return
        }

        // Model, config, options, or history changed - need to reinitialize
        sessionManager.shutdown()
        isInitialized = false
        hasTriedCpuFallback = false

        val modelPath = getModelPath(asset.metadata.localFileName)
        val systemPrompt = activeConfig.systemPrompt ?: "You are a helpful assistant."
        val samplingConfig = LlamaSamplingConfig(
            temperature = targetTemperature,
            topP = targetTopP,
            topK = targetTopK,
            minP = activeConfig.minP?.toFloat() ?: 0.0f,
            maxTokens = targetMaxTokens,
            contextWindow = activeConfig.contextWindow ?: 4096,
            batchSize = 256,
            gpuLayers = 32, // Will be adjusted by GpuConfig.forDevice
            thinkingEnabled = targetReasoningBudget > 0,
            repeatPenalty = activeConfig.repetitionPenalty?.toFloat() ?: 1.1f
        )

        try {
            sessionManager.initializeEngine(
                LlamaModelConfig(
                    modelPath = modelPath,
                    systemPrompt = systemPrompt,
                    sampling = samplingConfig
                )
            )
            sessionManager.setHistory(history)
            sessionManager.startNewConversation()
            isInitialized = true
            currentSignature = newSignature
        } catch (e: Exception) {
            // GPU initialization failed, try falling back to CPU
            if (!hasTriedCpuFallback && samplingConfig.gpuLayers > 0) {
                Log.w(TAG, "GPU initialization failed, falling back to CPU: ${e.message}")
                hasTriedCpuFallback = true
                val cpuConfig = samplingConfig.copy(gpuLayers = 0)
                sessionManager.initializeEngine(
                    LlamaModelConfig(
                        modelPath = modelPath,
                        systemPrompt = systemPrompt,
                        sampling = cpuConfig
                    )
                )
                sessionManager.setHistory(history)
                sessionManager.startNewConversation()
                isInitialized = true
                currentSignature = newSignature
                Log.i(TAG, "Successfully initialized with CPU fallback")
            } else {
                throw e
            }
        }
    }

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
        return sendPrompt(prompt, GenerationOptions(reasoningBudget = 0, modelType = modelType), closeConversation)
    }

    override suspend fun closeSession() {
        // Constitutional Fix: remove runBlocking.
        try {
            sessionManager.shutdown()
            isInitialized = false
            currentSignature = null
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down session", e)
        }
    }

    /**
     * Clear the conversation but keep the model loaded.
     * Used between pipeline steps to avoid reloading the model.
     */
    suspend fun clearConversation() {
        // Constitutional Fix: remove runBlocking.
        try {
            sessionManager.clearConversation()
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing conversation", e)
        }
    }

    override suspend fun setHistory(messages: List<DomainChatMessage>) {
        if (this.history != messages) {
            this.history = messages.toList()
            // Session will be reloaded on next sendPrompt due to signature change
        }
    }

    override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> = flow {
        // Get model type from options (we'll need to add it to GenerationOptions)
        val targetModelType = options.modelType ?: ModelType.FAST

        try {
            ensureModelLoaded(targetModelType, options)
        } catch (e: Exception) {
            emit(InferenceEvent.Error(e, targetModelType))
            return@flow
        }

        var isThinking = false
        var buffer = ""

        try {
            sessionManager.sendUserMessage(prompt)

            // Use options-aware streaming instead of mutating samplingConfig
            sessionManager.streamAssistantResponseWithOptions(options).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        val state = processThinkingTokens(
                            currentBuffer = buffer,
                            newChunk = event.text,
                            isThinking = isThinking
                        )
                        buffer = state.buffer
                        isThinking = state.isThinking

                        state.emittedSegments.forEach { segment ->
                            when (segment.kind) {
                                SegmentKind.THINKING -> {
                                    emit(InferenceEvent.Thinking(segment.text, targetModelType))
                                }
                                SegmentKind.VISIBLE -> {
                                    emit(InferenceEvent.PartialResponse(segment.text, targetModelType))
                                }
                            }
                        }
                    }
                    is GenerationEvent.Completed -> {
                        if (buffer.isNotEmpty()) {
                            if (isThinking) {
                                emit(InferenceEvent.Thinking(buffer, targetModelType))
                            } else {
                                emit(InferenceEvent.PartialResponse(buffer, targetModelType))
                            }
                        }
                        emit(InferenceEvent.Finished(targetModelType))
                    }
                    is GenerationEvent.Error -> {
                        emit(InferenceEvent.Error(event.throwable, targetModelType))
                    }
                }
            }

            if (closeConversation) {
                sessionManager.clearConversation()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            emit(InferenceEvent.Error(e, targetModelType))
        }
    }
}
