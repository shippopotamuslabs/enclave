package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase.SegmentKind
import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.LlamaSamplingConfig
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.inference.ContextExceededResult
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.util.ChatHistoryCompressor
import com.browntowndev.pocketcrew.domain.util.ContextWindowPlanner
import com.browntowndev.pocketcrew.domain.util.ToolContextBudget
import com.browntowndev.pocketcrew.domain.util.NativeToolResultFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val orchestrator: LlmToolingOrchestrator,
) : LlmInferencePort {

    companion object {
        private const val TAG = "LlamaInferenceService"
    }

    private data class SessionSignature(
        val modelId: LocalModelId,
        val configId: LocalModelConfigurationId,
        val reasoningBudget: Int,
        val temperature: Float,
        val topK: Int,
        val topP: Float,
        val maxTokens: Int,
    )

    private data class BufferedGeneration(
        val fullText: String,
    )

    private data class BufferedToolPass(
        val fullText: String,
    )

    private val mutex = Mutex()
    private var currentSignature: SessionSignature? = null
    private var currentContextWindow: Int = 4096
    private var currentSystemPrompt: String = ""
    private var history: List<DomainChatMessage> = emptyList()
    private var isInitialized = false
    private var hasTriedCpuFallback = false
    private var transientToolResultTokens: Int = 0

    private fun getModelPath(filename: String): String {
        // Use getExternalFilesDir to match ModelFileScanner's directory choice
        val modelsDir = java.io.File(context.getExternalFilesDir(null), "models")
        return java.io.File(modelsDir, filename).absolutePath
    }

    /**
     * Ensures the engine is loaded with the current model from registry.
     * If the model or config has changed since last load, tears down and reloads.
     */
    private suspend fun ensureModelLoaded(
        modelType: ModelType, 
        options: GenerationOptions,
        onLoading: suspend () -> Unit = {}
    ) {
        val activeConfig = activeModelProvider.getActiveConfiguration(modelType)
            ?: throw IllegalStateException("No active configuration for $modelType")

        if (!activeConfig.isLocal) {
            throw IllegalStateException("LlamaInferenceService cannot run API models. ModelType $modelType is mapped to an API configuration.")
        }

        val configId = activeConfig.id as LocalModelConfigurationId
        val asset = localModelRepository.getAssetByConfigId(configId)
            ?: throw IllegalStateException("No registered asset for config ${activeConfig.id}. Download a model first.")

        val targetReasoningBudget = options.reasoningBudget
        val targetTemperature = options.temperature ?: activeConfig.temperature?.toFloat() ?: 0.7f
        val targetTopK = options.topK ?: activeConfig.topK ?: 40
        val targetTopP = options.topP ?: activeConfig.topP?.toFloat() ?: 0.9f
        val targetMaxTokens = options.maxTokens ?: activeConfig.maxTokens ?: 4096

        val newSignature = SessionSignature(
            modelId = asset.metadata.id,
            configId = configId,
            reasoningBudget = targetReasoningBudget,
            temperature = targetTemperature,
            topK = targetTopK,
            topP = targetTopP,
            maxTokens = targetMaxTokens,
        )

        mutex.withLock {
            if (isInitialized && currentSignature == newSignature) {
                // Only update history if the model/options haven't changed
                sessionManager.setHistory(history)
                return@withLock
            }

            // Model, config, options, or history changed - need to reinitialize
            onLoading()
            sessionManager.shutdown()
            isInitialized = false
            hasTriedCpuFallback = false

            val modelPath = getModelPath(asset.metadata.localFileName)
            val mmprojPath = asset.metadata.mmprojLocalFileName?.let(::getModelPath)
            if (options.imageUris.isNotEmpty() &&
                asset.metadata.isMultimodal &&
                asset.metadata.modelFileFormat == com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.GGUF &&
                mmprojPath.isNullOrBlank()
            ) {
                throw IllegalStateException("Vision-capable GGUF model requires an mmproj companion file.")
            }
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
                        mmprojPath = mmprojPath,
                        systemPrompt = systemPrompt,
                        sampling = samplingConfig
                    )
                )

                val compressedHistory = ChatHistoryCompressor.compressHistory(
                    history = history,
                    systemPrompt = systemPrompt,
                    contextWindowTokens = samplingConfig.contextWindow,
                    bufferTokens = 1000
                )

                if (compressedHistory.size < history.size) {
                    Log.d(TAG, "Compressed Llama history from ${history.size} to ${compressedHistory.size} messages to fit initial context window")
                }

                sessionManager.setHistory(compressedHistory)
                sessionManager.startNewConversation()
                isInitialized = true
                currentSignature = newSignature
                currentContextWindow = samplingConfig.contextWindow
                currentSystemPrompt = systemPrompt
            } catch (e: Exception) {
                // GPU initialization failed, try falling back to CPU
                if (!hasTriedCpuFallback && samplingConfig.gpuLayers > 0) {
                    Log.w(TAG, "GPU initialization failed, falling back to CPU: ${e.message}")
                    hasTriedCpuFallback = true
                    val cpuConfig = samplingConfig.copy(gpuLayers = 0)
                    sessionManager.initializeEngine(
                        LlamaModelConfig(
                            modelPath = modelPath,
                            mmprojPath = mmprojPath,
                            systemPrompt = systemPrompt,
                            sampling = cpuConfig
                        )
                    )

                    val compressedHistory = ChatHistoryCompressor.compressHistory(
                        history = history,
                        systemPrompt = systemPrompt,
                        contextWindowTokens = cpuConfig.contextWindow,
                        bufferTokens = 1000
                    )

                    if (compressedHistory.size < history.size) {
                        Log.d(TAG, "Compressed Llama history (CPU fallback) from ${history.size} to ${compressedHistory.size} messages to fit initial context window")
                    }

                    sessionManager.setHistory(compressedHistory)
                    sessionManager.startNewConversation()
                    isInitialized = true
                    currentSignature = newSignature
                    currentContextWindow = cpuConfig.contextWindow
                    currentSystemPrompt = systemPrompt
                    Log.i(TAG, "Successfully initialized with CPU fallback")
                } else {
                    throw e
                }
            }
        }
    }

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
        return sendPrompt(prompt, GenerationOptions(reasoningBudget = 0, modelType = modelType), closeConversation)
    }

    override suspend fun closeSession() {
        // Constitutional Fix: remove runBlocking.
        mutex.withLock {
            try {
                sessionManager.shutdown()
                isInitialized = false
                currentSignature = null
            } catch (e: Exception) {
                Log.w(TAG, "Error shutting down session", e)
            }
        }
    }

    /**
     * Clear the conversation but keep the model loaded.
     * Used between pipeline steps to avoid reloading the model.
     */
    suspend fun clearConversation() {
        // Constitutional Fix: remove runBlocking.
        mutex.withLock {
            try {
                sessionManager.clearConversation()
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing conversation", e)
            }
        }
    }

    override suspend fun setHistory(messages: List<DomainChatMessage>) {
        mutex.withLock {
            if (this.history != messages) {
                this.history = messages.toList()
                // Session will be reloaded on next sendPrompt due to signature change
            }
        }
    }

    override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> = channelFlow {
        // Get model type from options (we'll need to add it to GenerationOptions)
        val targetModelType = options.modelType ?: ModelType.FAST
        transientToolResultTokens = 0

        // Downscale images and save them to temporary files for the JNI engine
        val processedOptions = if (options.imageUris.isNotEmpty()) {
            val downscaledUris = options.imageUris.map { uri ->
                try {
                    "file://" + ImageDownscaler.downscaleToTempFile(context, uri)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to downscale image $uri", e)
                    uri // fallback to original if it fails
                }
            }
            options.copy(imageUris = downscaledUris)
        } else {
            options
        }

        try {
            ensureModelLoaded(targetModelType, processedOptions) {
                send(InferenceEvent.EngineLoading(targetModelType))
            }
            send(InferenceEvent.Processing(targetModelType))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            send(InferenceEvent.Error(e, targetModelType))
            return@channelFlow
        }

        var isThinking = false
        var buffer = ""

        try {
            sessionManager.sendUserMessage(prompt)

            if (ToolEnvelopeParser.hasLocalToolContract(processedOptions.systemPrompt)) {
                executeToolingPrompt(processedOptions, targetModelType) { send(it) }
                if (closeConversation) {
                    sessionManager.clearConversation()
                }
                return@channelFlow
            }

            // Use options-aware streaming instead of mutating samplingConfig
            sessionManager.streamAssistantResponseWithOptions(processedOptions).collect { event ->
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
                                    send(InferenceEvent.Thinking(segment.text, targetModelType))
                                }
                                SegmentKind.VISIBLE -> {
                                    send(InferenceEvent.PartialResponse(segment.text, targetModelType))
                                }
                            }
                        }
                    }
                    is GenerationEvent.Completed -> {
                        if (buffer.isNotEmpty()) {
                            if (isThinking) {
                                send(InferenceEvent.Thinking(buffer, targetModelType))
                            } else {
                                send(InferenceEvent.PartialResponse(buffer, targetModelType))
                            }
                        }
                        send(InferenceEvent.Finished(targetModelType))
                    }
                    is GenerationEvent.Error -> {
                        send(InferenceEvent.Error(event.throwable, targetModelType))
                    }
                }
            }

            if (closeConversation) {
                sessionManager.clearConversation()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            send(InferenceEvent.Error(e, targetModelType))
        } finally {
            // Stop the native llama.cpp engine if still generating
            try {
                sessionManager.stopCurrentGeneration()
            } catch (_: Exception) {
                // Best-effort stop; engine may already be idle or shut down
            }
            // Clean up temp files created for this prompt
            if (options.imageUris.isNotEmpty()) {
                processedOptions.imageUris.forEach { uriString ->
                    if (uriString.startsWith("file://")) {
                        try {
                            val path = uriString.removePrefix("file://")
                            val file = java.io.File(path)
                            if (file.exists() && file.parentFile?.name == context.cacheDir.name) {
                                file.delete()
                                Log.d(TAG, "Cleaned up temp image: $path")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to clean up temp image $uriString", e)
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun executeToolingPrompt(
        options: GenerationOptions,
        targetModelType: ModelType,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ) {
        orchestrator.execute(
            providerName = "LLAMA",
            initialParams = options,
            tag = TAG,
            maxToolCalls = options.maxToolCalls,
            onInferencePass = { params, allowToolCall ->
                collectToolPreparationPass(
                    events = sessionManager.streamAssistantResponseWithOptions(params),
                    targetModelType = targetModelType,
                    emitEvent = emitEvent,
                )
            },
            onToolCallDetected = { pass ->
                ToolEnvelopeParser.extractLocalToolEnvelope(pass.fullText)?.let { envelope ->
                    listOf(
                        ToolCallRequest(
                            toolName = envelope.toolName,
                            argumentsJson = envelope.argumentsJson,
                            provider = "LLAMA",
                            modelType = targetModelType,
                            chatId = options.chatId,
                            userMessageId = options.userMessageId,
                        )
                    )
                } ?: emptyList()
            },
            onToolResultsMapped = { params, _, results ->
                val tokenCounter = com.browntowndev.pocketcrew.domain.util.JTokkitTokenCounter
                val modelId = currentSignature?.modelId?.value
                val systemPromptTokens = ToolContextBudget.countSystemPromptTokens(currentSystemPrompt, modelId, tokenCounter)
                val historyTokens = ToolContextBudget.countHistoryTokens(history, modelId, tokenCounter)

                results.forEach { (_, resultJson) ->
                    val evaluation = ToolContextBudget.evaluate(
                        contextWindowTokens = currentContextWindow,
                        systemPromptTokens = systemPromptTokens,
                        historyTokens = historyTokens,
                        transientToolResultTokens = transientToolResultTokens,
                        options = GenerationOptions(reasoningBudget = 0),
                        modelId = modelId,
                        tokenCounter = tokenCounter,
                    )
                    val truncatedResult = NativeToolResultFormatter.truncateToolResult(
                        resultJson = resultJson,
                        contextWindowTokens = currentContextWindow,
                        estimatedUsedTokens = evaluation.totalTokens,
                        bufferTokens = ContextWindowPlanner.LOCAL_TOOL_RESULT_BUFFER_TOKENS,
                        tokenCounter = tokenCounter,
                        modelId = modelId,
                    )
                    if (!truncatedResult.contains("\"error\"")) {
                        transientToolResultTokens += tokenCounter.countTokens(truncatedResult, modelId) + 30
                    }
                    val toolResultMessage = if (evaluation.contextFull) {
                        ToolEnvelopeParser.buildLocalToolResultMessage(truncatedResult) +
                            "\n${ContextWindowPlanner.STOP_TOOLS_WARNING}"
                    } else {
                        ToolEnvelopeParser.buildLocalToolResultMessage(truncatedResult)
                    }
                    sessionManager.sendUserMessage(toolResultMessage)
                }
                params
            },
            onContextExceeded = { _, _ ->
                // Llama uses local KV cache — mid-loop compaction isn't feasible.
                // Detect whether context is exceeded and report to orchestrator
                // so the contextFullWarned hard-error logic kicks in.
                val tokenCounter = com.browntowndev.pocketcrew.domain.util.JTokkitTokenCounter
                val modelId = currentSignature?.modelId?.value
                val systemPromptTokens = ToolContextBudget.countSystemPromptTokens(currentSystemPrompt, modelId, tokenCounter)
                val historyTokens = ToolContextBudget.countHistoryTokens(history, modelId, tokenCounter)
                val evaluation = ToolContextBudget.evaluate(
                    contextWindowTokens = currentContextWindow,
                    systemPromptTokens = systemPromptTokens,
                    historyTokens = historyTokens,
                    transientToolResultTokens = transientToolResultTokens,
                    options = GenerationOptions(reasoningBudget = 0),
                    modelId = modelId,
                    tokenCounter = tokenCounter,
                )
                if (evaluation.contextFull) {
                    loggingPort.warning(TAG, "Llama context exceeded mid-loop: estimatedTokens=${evaluation.totalTokens} window=$currentContextWindow")
                }
                ContextExceededResult(evaluation.contextFull)
            },
            onToolResult = { toolCall, resultJson ->
                if (toolCall.toolName == com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition.TAVILY_WEB_SEARCH.name) {
                    val assistantMessageId = options.assistantMessageId
                    if (assistantMessageId != null) {
                        val sources = com.browntowndev.pocketcrew.domain.util.TavilyResultParser.parse(assistantMessageId, resultJson)
                        if (sources.isNotEmpty()) {
                            emitEvent(InferenceEvent.TavilyResults(sources, targetModelType))
                        }
                    }
                }
            },
            onNoToolCallOnFirstPass = { pass ->
                emitProcessedText(pass.fullText, targetModelType, emitEvent)
            },
            onFinished = { _, toolCallCount, lastResponse ->
                if (toolCallCount > 0 && lastResponse != null) {
                    emitProcessedText(lastResponse.fullText, targetModelType, emitEvent)
                }
                emitEvent(InferenceEvent.Finished(targetModelType))
            }
        )
    }

    private suspend fun collectToolPreparationPass(
        events: Flow<GenerationEvent>,
        targetModelType: ModelType,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): BufferedToolPass {
        val buffered = streamBufferedGeneration(
            events = events,
            targetModelType = targetModelType,
            emitVisible = false,
            emitEvent = emitEvent,
        )
        return BufferedToolPass(fullText = buffered.fullText)
    }

    private suspend fun streamBufferedGeneration(
        events: Flow<GenerationEvent>,
        targetModelType: ModelType,
        emitVisible: Boolean,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): BufferedGeneration {
        var completedText: String? = null
        var isThinking = false
        var buffer = ""
        var sawToken = false

        events.catch { throw it }.collect { event ->
            when (event) {
                is GenerationEvent.Token -> {
                    sawToken = true
                    val state = processThinkingTokens(
                        currentBuffer = buffer,
                        newChunk = event.text,
                        isThinking = isThinking
                    )
                    buffer = state.buffer
                    isThinking = state.isThinking

                    state.emittedSegments.forEach { segment ->
                        when (segment.kind) {
                            SegmentKind.THINKING -> emitEvent(InferenceEvent.Thinking(segment.text, targetModelType))
                            SegmentKind.VISIBLE -> if (emitVisible) {
                                emitEvent(InferenceEvent.PartialResponse(segment.text, targetModelType))
                            }
                        }
                    }
                }
                is GenerationEvent.Completed -> {
                    if (sawToken) {
                        if (buffer.isNotEmpty()) {
                            if (isThinking) {
                                emitEvent(InferenceEvent.Thinking(buffer, targetModelType))
                            } else if (emitVisible) {
                                emitEvent(InferenceEvent.PartialResponse(buffer, targetModelType))
                            }
                        }
                    } else if (event.fullText.isNotBlank()) {
                        emitProcessedText(
                            rawText = event.fullText,
                            targetModelType = targetModelType,
                            emitEvent = { streamedEvent ->
                                when (streamedEvent) {
                                    is InferenceEvent.Thinking -> emitEvent(streamedEvent)
                                    is InferenceEvent.PartialResponse -> if (emitVisible) emitEvent(streamedEvent)
                                    else -> Unit
                                }
                            }
                        )
                    }
                    completedText = event.fullText
                }
                is GenerationEvent.Error -> {
                    throw event.throwable
                }
            }
        }

        return BufferedGeneration(
            fullText = completedText.orEmpty(),
        )
    }

    private suspend fun emitProcessedText(
        rawText: String,
        targetModelType: ModelType,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ) {
        var isThinking = false
        var buffer = ""
        val state = processThinkingTokens(
            currentBuffer = buffer,
            newChunk = rawText,
            isThinking = isThinking,
        )
        buffer = state.buffer
        isThinking = state.isThinking

        state.emittedSegments.forEach { segment ->
            when (segment.kind) {
                SegmentKind.THINKING -> emitEvent(InferenceEvent.Thinking(segment.text, targetModelType))
                SegmentKind.VISIBLE -> emitEvent(InferenceEvent.PartialResponse(segment.text, targetModelType))
            }
        }

        if (buffer.isNotEmpty()) {
            if (isThinking) {
                emitEvent(InferenceEvent.Thinking(buffer, targetModelType))
            } else {
                emitEvent(InferenceEvent.PartialResponse(buffer, targetModelType))
            }
        }
    }
}
