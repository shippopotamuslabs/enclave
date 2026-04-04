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
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Implementation of LlmInferencePort using llama.cpp via JNI.
 * Bridges the llama.cpp GenerationEvent to the domain's InferenceEvent.
 * Resolves the active model from [ModelRegistryPort] at runtime, eliminating
 * the need for explicit configure() calls at provision time.
 */
class LlamaInferenceServiceImpl @Inject constructor(
    private val sessionManager: LlamaChatSessionManager,
    private val processThinkingTokens: ProcessThinkingTokensUseCase,
    private val loggingPort: LoggingPort,
    private val modelRegistry: ModelRegistryPort,
    @ApplicationContext private val context: Context
) : LlmInferencePort {

    companion object {
        private const val TAG = "LlamaInferenceService"
    }

    private var systemPrompt: String = "You are a helpful assistant."
    private var samplingConfig: LlamaSamplingConfig = LlamaSamplingConfig(minP = 0.0f)
    private var isInitialized = false
    private var hasTriedCpuFallback = false
    private var currentModelId: Long? = null
    private var currentConfigId: Long? = null

    private fun getModelPath(filename: String): String {
        // Use getExternalFilesDir to match ModelFileScanner's directory choice
        val modelsDir = java.io.File(context.getExternalFilesDir(null), "models")
        return java.io.File(modelsDir, filename).absolutePath
    }

    /**
     * Ensures the engine is loaded with the current model from registry.
     * If the model or config has changed since last load, tears down and reloads.
     */
    private suspend fun ensureModelLoaded(modelType: ModelType) {
        val asset = modelRegistry.getRegisteredAsset(modelType)
        val config = modelRegistry.getRegisteredConfiguration(modelType)

        if (asset == null) {
            throw IllegalStateException("No registered asset for $modelType. Download a model first.")
        }

        val modelId = asset.metadata.id
        val configId = config?.id

        val modelChanged = currentModelId != modelId
        val configChanged = currentConfigId != configId

        if (!modelChanged && !configChanged && isInitialized) {
            return
        }

        // Model or config changed - need to reinitialize
        sessionManager.shutdown()
        isInitialized = false
        hasTriedCpuFallback = false

        val modelPath = getModelPath(asset.metadata.localFileName)
        systemPrompt = config?.systemPrompt ?: "You are a helpful assistant."
        samplingConfig = LlamaSamplingConfig(
            temperature = config?.temperature?.toFloat() ?: 0.7f,
            topP = config?.topP?.toFloat() ?: 0.9f,
            topK = config?.topK ?: 40,
            minP = config?.minP?.toFloat() ?: 0.0f,
            maxTokens = config?.maxTokens ?: 4096,
            contextWindow = config?.contextWindow ?: 4096,
            batchSize = 256,
            gpuLayers = 32, // Will be adjusted by GpuConfig.forDevice
            thinkingEnabled = config?.thinkingEnabled ?: false,
            repeatPenalty = config?.repetitionPenalty?.toFloat() ?: 1.1f
        )

        try {
            sessionManager.initializeEngine(
                LlamaModelConfig(
                    modelPath = modelPath,
                    systemPrompt = systemPrompt,
                    sampling = samplingConfig
                )
            )
            sessionManager.startNewConversation()
            isInitialized = true
            currentModelId = modelId
            currentConfigId = configId
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
                sessionManager.startNewConversation()
                isInitialized = true
                currentModelId = modelId
                currentConfigId = configId
                Log.i(TAG, "Successfully initialized with CPU fallback")
            } else {
                throw e
            }
        }
    }

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> = flow {
        // Fallback for cases where targetModelType is not provided
        val modelType = ModelType.FAST 
        
        try {
            ensureModelLoaded(modelType)
        } catch (e: Exception) {
            emit(InferenceEvent.Error(e, modelType))
            return@flow
        }

        var isThinking = false
        val accumulatedThought = StringBuilder()
        val accumulatedText = StringBuilder()
        var buffer = ""

        try {
            // Send user message
            sessionManager.sendUserMessage(prompt)

            var loggedThinking: Boolean = false

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
                                    if (!loggedThinking) {
                                        loggingPort.debug(
                                            TAG,
                                            "Model Type: $modelType Thinking chunk: ${segment.text}"
                                        )
                                        loggedThinking = true
                                    }
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
                    is GenerationEvent.Completed -> {
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
                    }
                    is GenerationEvent.Error -> {
                        emit(InferenceEvent.Error(event.throwable, modelType))
                    }
                }
            }

            // Close conversation if requested
            if (closeConversation) {
                sessionManager.clearConversation()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            emit(InferenceEvent.Error(e, modelType))
        }
    }

    override suspend fun closeSession() {
        // Constitutional Fix: remove runBlocking.
        try {
            sessionManager.shutdown()
            isInitialized = false
            currentModelId = null
            currentConfigId = null
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
        sessionManager.setHistory(messages)
    }

    override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> = flow {
        // Get model type from options (we'll need to add it to GenerationOptions)
        val targetModelType = options.modelType ?: ModelType.FAST

        try {
            ensureModelLoaded(targetModelType)
        } catch (e: Exception) {
            emit(InferenceEvent.Error(e, targetModelType))
            return@flow
        }

        var isThinking = false
        val accumulatedThought = StringBuilder()
        val accumulatedText = StringBuilder()
        var buffer = ""

        try {
            sessionManager.sendUserMessage(prompt)

            // Use options-aware streaming instead of mutating samplingConfig
            sessionManager.streamAssistantResponseWithOptions(options).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        val state = processThinkingTokens(buffer, event.text, isThinking)
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
                    is GenerationEvent.Completed -> {
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
