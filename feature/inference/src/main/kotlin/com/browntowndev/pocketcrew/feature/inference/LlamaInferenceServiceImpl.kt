package com.browntowndev.pocketcrew.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.inference.AgentFinishReason
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.AgentModelTurnResult
import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.LlamaSamplingConfig
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolEnvelopeParserPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolStreamGatePort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase.SegmentKind
import com.browntowndev.pocketcrew.feature.inference.llama.LlamaChatSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage as DomainChatMessage
import java.util.concurrent.CancellationException as JavaCancellationException

/**
 * Implementation of LlmInferencePort using llama.cpp via JNI.
 * Bridges the llama.cpp GenerationEvent to the domain's InferenceEvent.
 * Resolves the active model from [LocalModelRepositoryPort] at runtime, eliminating
 * the need for explicit configure() calls at provision time.
 */
@Suppress("LargeClass")
class LlamaInferenceServiceImpl
    @Inject
    constructor(
        private val sessionManager: LlamaChatSessionManager,
        private val processThinkingTokens: ProcessThinkingTokensUseCase,
        private val loggingPort: LoggingPort,
        private val localModelRepository: LocalModelRepositoryPort,
        private val activeModelProvider: ActiveModelProviderPort,
        @param:ApplicationContext private val context: Context,
        private val modelType: ModelType,
        private val toolEnvelopeParserPort: ToolEnvelopeParserPort,
        @Suppress("UnusedPrivateProperty") // Injected for mutation tests; streaming holdback lives in core/agent.
        private val toolStreamGatePort: ToolStreamGatePort,
    ) : RustCompatibleInferenceService {
        companion object {
            private const val TAG = "LlamaInferenceService"
            private const val DEFAULT_TEMPERATURE = 0.7f
            private const val DEFAULT_TOP_P = 0.9f
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

        private val mutex = Mutex()
        private var currentSignature: SessionSignature? = null
        private var history: List<DomainChatMessage> = emptyList()
        private var isInitialized = false
        private var hasTriedCpuFallback = false

        private fun getModelPath(filename: String): String {
            // Use getExternalFilesDir to match ModelFileScanner's directory choice
            val modelsDir = File(context.getExternalFilesDir(null), "models")
            return File(modelsDir, filename).absolutePath
        }

        /**
         * Ensures the engine is loaded with the current model from registry.
         * If the model or config has changed since last load, tears down and reloads.
         */
        @Suppress("ThrowsCount")
        private suspend fun ensureModelLoaded(
            modelType: ModelType,
            options: GenerationOptions,
            onLoading: suspend () -> Unit = {},
        ) {
            val activeConfig =
                activeModelProvider.getActiveConfiguration(modelType)
                    ?: throw IllegalStateException("No active configuration for $modelType")

            if (!activeConfig.isLocal) {
                @Suppress("MaxLineLength")
                throw IllegalStateException(
                    "LlamaInferenceService cannot run API models. ModelType $modelType is mapped to an API configuration.",
                )
            }

            val configId = activeConfig.id as LocalModelConfigurationId
            val asset =
                localModelRepository.getAssetByConfigId(configId)
                    ?: throw IllegalStateException("No registered asset for config ${activeConfig.id}. Download a model first.")

            val targetReasoningBudget = options.reasoningBudget
            val targetTemperature = options.temperature ?: activeConfig.temperature?.toFloat() ?: DEFAULT_TEMPERATURE
            val targetTopK = options.topK ?: activeConfig.topK ?: 40
            val targetTopP = options.topP ?: activeConfig.topP?.toFloat() ?: DEFAULT_TOP_P
            val targetMaxTokens = options.maxTokens ?: activeConfig.maxTokens ?: 4096

            val newSignature =
                SessionSignature(
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
                val requiresMmproj =
                    options.imageUris.isNotEmpty() &&
                        asset.metadata.isMultimodal &&
                        asset.metadata.modelFileFormat == ModelFileFormat.GGUF
                if (requiresMmproj && mmprojPath.isNullOrBlank()) {
                    throw IllegalStateException("Vision-capable GGUF model requires an mmproj companion file.")
                }
                val systemPrompt = activeConfig.systemPrompt ?: "You are a helpful assistant."
                val samplingConfig =
                    LlamaSamplingConfig(
                        temperature = targetTemperature,
                        topP = targetTopP,
                        topK = targetTopK,
                        minP = activeConfig.minP?.toFloat() ?: 0.0f,
                        maxTokens = targetMaxTokens,
                        contextWindow = activeConfig.contextWindow ?: 4096,
                        batchSize = 256,
                        gpuLayers = 32, // Will be adjusted by GpuConfig.forDevice
                        thinkingEnabled = targetReasoningBudget > 0,
                        repeatPenalty = activeConfig.repetitionPenalty?.toFloat() ?: 1.1f,
                    )

                try {
                    sessionManager.initializeEngine(
                        LlamaModelConfig(
                            modelPath = modelPath,
                            mmprojPath = mmprojPath,
                            systemPrompt = systemPrompt,
                            sampling = samplingConfig,
                        ),
                    )

                    // History arrives pre-compacted from ChatHistoryRehydrator; no FIFO trimming needed.
                    sessionManager.setHistory(history)
                    sessionManager.startNewConversation()
                    isInitialized = true
                    currentSignature = newSignature
                } catch (e: CancellationException) {
                    throw e
                } catch (e: JavaCancellationException) {
                    throw e
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
                                sampling = cpuConfig,
                            ),
                        )

                        // History arrives pre-compacted from ChatHistoryRehydrator; no FIFO trimming needed.
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
        }

        override fun sendPrompt(
            prompt: String,
            closeConversation: Boolean,
        ): Flow<InferenceEvent> = sendPrompt(prompt, GenerationOptions(reasoningBudget = 0, modelType = modelType), closeConversation)

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

        override suspend fun completeSingleTurn(
            sessionMessages: List<AgentLoopMessage>,
            systemPrompt: String,
            availableTools: List<ToolDefinition>,
            allowToolCall: Boolean,
            onTextToken: suspend (String) -> Unit,
            onThinkingToken: suspend (String) -> Unit,
            options: GenerationOptions,
        ): AgentModelTurnResult {
            val targetModelType = options.modelType ?: modelType
            if (options.imageUris.isNotEmpty()) {
                loggingPort.warning(TAG, "Images are not supported in Llama agent loop turns; imageUris will be ignored")
            }
            val turnOptions =
                options.copy(
                    toolingEnabled = allowToolCall && availableTools.isNotEmpty(),
                    availableTools = availableTools,
                    systemPrompt = systemPrompt.ifBlank { options.systemPrompt },
                )
            val domainHistory = sessionMessages.map(::toLlamaDomainMessage)

            mutex.withLock {
                history = domainHistory
            }
            ensureModelLoaded(targetModelType, turnOptions)
            sessionManager.setHistory(domainHistory)

            val buffered =
                streamBufferedGeneration(
                    events = sessionManager.streamAssistantResponseWithOptions(turnOptions),
                    targetModelType = targetModelType,
                    emitVisible = false,
                    emitEvent = { event ->
                        if (event is InferenceEvent.Thinking) {
                            onThinkingToken(event.chunk)
                        }
                    },
                )
            emitProcessedText(buffered.fullText, targetModelType) { event ->
                when (event) {
                    is InferenceEvent.Thinking -> onThinkingToken(event.chunk)
                    is InferenceEvent.PartialResponse -> onTextToken(event.chunk)
                    else -> Unit
                }
            }
            val rawText = buffered.fullText
            return AgentModelTurnResult(
                assistantText = rawText.takeIf { it.isNotBlank() },
                toolCalls = emptyList(),
                finishReason = AgentFinishReason.Stop,
            )
        }

        override fun sendPrompt(
            prompt: String,
            options: GenerationOptions,
            closeConversation: Boolean,
        ): Flow<InferenceEvent> =
            channelFlow {
                // Get model type from options (we'll need to add it to GenerationOptions)
                val targetModelType = options.modelType ?: ModelType.FAST
                var imageUrisToProcess = options.imageUris
                if (imageUrisToProcess.size > 1) {
                    Log.w(TAG, "Llama supports only 1 image per request. Ignoring extra images.")
                    imageUrisToProcess = listOf(imageUrisToProcess.first())
                }

                // Downscale images and save them to temporary files for the JNI engine
                val processedOptions =
                    if (imageUrisToProcess.isNotEmpty()) {
                        val downscaledUris =
                            imageUrisToProcess.map { uri ->
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: JavaCancellationException) {
                    throw e
                } catch (e: Exception) {
                    send(InferenceEvent.Error(e, targetModelType))
                    return@channelFlow
                }

                var isThinking = false
                var buffer = ""

                try {
                    sessionManager.sendUserMessage(prompt)

                    // Use options-aware streaming instead of mutating samplingConfig
                    sessionManager.streamAssistantResponseWithOptions(processedOptions).collect { event ->
                        when (event) {
                            is GenerationEvent.Token -> {
                                val state =
                                    processThinkingTokens(
                                        currentBuffer = buffer,
                                        newChunk = event.text,
                                        isThinking = isThinking,
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
                    loggingPort.debug(TAG, "sendPrompt cancelled targetModelType=$targetModelType")
                    send(InferenceEvent.Finished(targetModelType))
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
                                    val file = File(path)
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
                        val state =
                            processThinkingTokens(
                                currentBuffer = buffer,
                                newChunk = event.text,
                                isThinking = isThinking,
                            )
                        buffer = state.buffer
                        isThinking = state.isThinking

                        state.emittedSegments.forEach { segment ->
                            when (segment.kind) {
                                SegmentKind.THINKING -> emitEvent(InferenceEvent.Thinking(segment.text, targetModelType))
                                SegmentKind.VISIBLE ->
                                    if (emitVisible) {
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
                                },
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

        private fun toLlamaDomainMessage(message: AgentLoopMessage): DomainChatMessage {
            val metadata =
                if (message.metadataJson != "null" && message.metadataJson.isNotBlank()) {
                    runCatching { Json.parseToJsonElement(message.metadataJson).jsonObject }.getOrNull()
                } else {
                    null
                }
            val callId = metadata?.get("call_id")?.jsonPrimitive?.contentOrNull
            return if (callId != null) {
                DomainChatMessage(
                    role = Role.USER,
                    content = toolEnvelopeParserPort.buildToolResultMessage(message.content),
                )
            } else {
                DomainChatMessage(role = message.role, content = message.content)
            }
        }

        private suspend fun emitProcessedText(
            rawText: String,
            targetModelType: ModelType,
            emitEvent: suspend (InferenceEvent) -> Unit,
        ) {
            var isThinking = false
            var buffer = ""
            val state =
                processThinkingTokens(
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
