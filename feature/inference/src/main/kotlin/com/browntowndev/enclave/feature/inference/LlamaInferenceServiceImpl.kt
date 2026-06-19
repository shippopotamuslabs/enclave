package com.browntowndev.enclave.feature.inference

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.GenerationEvent
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.LlamaModelConfig
import com.browntowndev.enclave.domain.model.inference.LlamaSamplingConfig
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolEnvelopeParserPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase.SegmentKind
import com.browntowndev.enclave.feature.inference.llama.LlamaChatSessionManager
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTurnTiming
import com.browntowndev.enclave.feature.inference.telemetry.estimateTokenCount
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
import com.browntowndev.enclave.domain.model.chat.ChatMessage as DomainChatMessage
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
        private val telemetrySupport: InferenceTelemetrySupport? = null,
    ) : RustCompatibleInferenceService {
        companion object {
            private const val TAG = "LlamaInferenceService"
            private const val DEFAULT_TEMPERATURE = 0.7f
            private const val DEFAULT_TOP_P = 0.9f
            private const val MISMATCH_LOG_PREVIEW_LENGTH = 80
        }

        private data class SessionSignature(
            val modelId: LocalModelId,
            val configId: LocalModelConfigurationId,
            val reasoningBudget: Int,
            val temperature: Float,
            val topK: Int,
            val topP: Float,
            val maxTokens: Int,
            val contextWindow: Int,
        )

        private data class BufferedGeneration(
            val fullText: String,
            val accumulatedThought: String = "",
            val promptTokens: Int? = null,
            val generatedTokens: Int? = null,
            val prefixReusedTokens: Int? = null,
        )

        private val mutex = Mutex()
        private var currentSignature: SessionSignature? = null
        private var history: List<DomainChatMessage> = emptyList()
        private var kvRawTranscript: List<AgentLoopMessage> = emptyList()
        private var kvTranscriptAsSent: List<DomainChatMessage> = emptyList()
        private var kvSystemPrompt: String? = null
        private var kvTranscriptRevision: Long? = null
        private var kvContinuationValid = false
        private var isInitialized = false

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
            val targetContextWindow = activeConfig.contextWindow ?: 4096

            val newSignature =
                SessionSignature(
                    modelId = asset.metadata.id,
                    configId = configId,
                    reasoningBudget = targetReasoningBudget,
                    temperature = targetTemperature,
                    topK = targetTopK,
                    topP = targetTopP,
                    maxTokens = targetMaxTokens,
                    contextWindow = targetContextWindow,
                )

            mutex.withLock {
                if (isInitialized && currentSignature == newSignature) {
                    // Only update history if the model/options haven't changed
                    sessionManager.setHistory(history)
                    return@withLock
                }

                // Model, config, options, or history changed - need to reinitialize
                invalidateKvContinuation()
                onLoading()
                sessionManager.shutdown()
                isInitialized = false
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
                        contextWindow = targetContextWindow,
                        batchSize = 512,
                        gpuLayers = 0,
                        thinkingEnabled = targetReasoningBudget > 0,
                        repeatPenalty = activeConfig.repetitionPenalty?.toFloat() ?: 1.1f,
                    )

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
            }
        }

        override fun sendPrompt(
            prompt: String,
            closeConversation: Boolean,
        ): Flow<InferenceEvent> = sendPrompt(prompt, GenerationOptions(reasoningBudget = 0, modelType = modelType), closeConversation)

        override suspend fun closeSession() {
            // Constitutional Fix: remove runBlocking.
            mutex.withLock {
                invalidateKvContinuation()
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
                invalidateKvContinuation()
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
            contextSidecars: String?,
            availableTools: List<ToolDefinition>,
            allowToolCall: Boolean,
            onTextToken: suspend (String) -> Unit,
            onThinkingToken: suspend (String) -> Unit,
            options: GenerationOptions,
            onLifecycleEvent: suspend (InferenceEvent) -> Unit,
        ): AgentModelTurnResult {
            val targetModelType = options.modelType ?: modelType
            if (options.imageUris.isNotEmpty()) {
                loggingPort.warning(TAG, "Images are not supported in Llama agent loop turns; imageUris will be ignored")
            }
            val revision = options.transcriptRevision
            // Ids and metadata are session-local; only role+content affect the rendered prompt.
            val prefixMismatchIndex =
                sessionMessages
                    .zip(kvRawTranscript)
                    .indexOfFirst { (new, old) -> new.role != old.role || new.content != old.content }
            val continuation =
                kvContinuationValid &&
                    revision == kvTranscriptRevision &&
                    systemPrompt == kvSystemPrompt &&
                    sessionMessages.size > kvRawTranscript.size &&
                    prefixMismatchIndex < 0
            if (!continuation) {
                val mismatchDetail =
                    if (prefixMismatchIndex >= 0) {
                        val new = sessionMessages[prefixMismatchIndex]
                        val old = kvRawTranscript[prefixMismatchIndex]
                        " mismatchAt=$prefixMismatchIndex newRole=${new.role} oldRole=${old.role}" +
                            " newContent='${new.content.take(MISMATCH_LOG_PREVIEW_LENGTH)}'" +
                            " oldContent='${old.content.take(MISMATCH_LOG_PREVIEW_LENGTH)}'"
                    } else {
                        ""
                    }
                loggingPort.debug(
                    TAG,
                    "kv_continuation=false valid=$kvContinuationValid" +
                        " revisionMatch=${revision == kvTranscriptRevision}" +
                        " (rev=$revision stored=$kvTranscriptRevision)" +
                        " systemPromptMatch=${systemPrompt == kvSystemPrompt}" +
                        " sizeOk=${sessionMessages.size > kvRawTranscript.size}" +
                        " (new=${sessionMessages.size} stored=${kvRawTranscript.size})" +
                        mismatchDetail,
                )
            }
            val newMessages =
                if (continuation) {
                    sessionMessages.drop(kvRawTranscript.size).map(::toLlamaDomainMessage)
                } else {
                    sessionMessages.map(::toLlamaDomainMessage)
                }
            val newMessagesAsSent = injectSidecarIntoLastUserMessage(newMessages, contextSidecars)
            val domainHistory =
                if (continuation) {
                    kvTranscriptAsSent + newMessagesAsSent
                } else {
                    newMessagesAsSent
                }
            val prevMessageCount = if (continuation) kvTranscriptAsSent.size + 1 else 0
            val turnOptions =
                options.copy(
                    toolingEnabled = allowToolCall && availableTools.isNotEmpty(),
                    activeTools = availableTools,
                    systemPrompt = systemPrompt.ifBlank { options.systemPrompt },
                )

            mutex.withLock {
                history = domainHistory
            }
            try {
                ensureModelLoaded(targetModelType, turnOptions) {
                    onLifecycleEvent(InferenceEvent.EngineLoading(targetModelType))
                }
                val nativeContinuation = continuation && kvContinuationValid
                val nativePrevMessageCount = if (nativeContinuation) prevMessageCount else 0
                onLifecycleEvent(InferenceEvent.Processing(targetModelType))
                sessionManager.setHistory(domainHistory)

                val timing = telemetrySupport?.let { InferenceTurnTiming() }
                val captureContext = telemetrySupport?.captureContext(turnOptions)
                val buffered =
                    streamBufferedGeneration(
                        events =
                            sessionManager.streamAssistantResponseWithOptions(
                                options = turnOptions,
                                continuation = nativeContinuation,
                                prevMessageCount = nativePrevMessageCount,
                            ),
                        targetModelType = targetModelType,
                        emitVisible = true,
                        timing = timing,
                        emitEvent = { event ->
                            when (event) {
                                is InferenceEvent.Thinking -> onThinkingToken(event.chunk)
                                is InferenceEvent.PartialResponse -> onTextToken(event.chunk)
                                else -> Unit
                            }
                        },
                    )
                val rawText = buffered.fullText
                val outputTokens =
                    buffered.generatedTokens
                        ?: estimateTokenCount(rawText)
                val reasoningTokens = estimateTokenCount(buffered.accumulatedThought)
                val inputTokens =
                    listOfNotNull(buffered.prefixReusedTokens, buffered.promptTokens)
                        .takeIf { it.isNotEmpty() }
                        ?.sum()
                val telemetry =
                    if (telemetrySupport != null && timing != null && captureContext != null) {
                        telemetrySupport.localTurnTelemetry(
                            context = captureContext,
                            providerLabel = "llama.cpp",
                            modelLabel = turnOptions.modelId ?: "local",
                            timing = timing,
                            outputTokens = outputTokens,
                            inputTokens = inputTokens,
                            cacheReadInputTokens = buffered.prefixReusedTokens,
                            reasoningTokens = reasoningTokens,
                        )
                    } else {
                        null
                    }
                mutex.withLock {
                    kvRawTranscript = sessionMessages.toList()
                    kvTranscriptAsSent = domainHistory.toList()
                    kvSystemPrompt = systemPrompt
                    kvTranscriptRevision = revision
                    kvContinuationValid = true
                }
                return AgentModelTurnResult(
                    assistantText = rawText.takeIf { it.isNotBlank() },
                    toolCalls = emptyList(),
                    finishReason = AgentFinishReason.Stop,
                    telemetry = telemetry,
                )
            } catch (e: CancellationException) {
                invalidateKvContinuation()
                throw e
            } catch (e: JavaCancellationException) {
                invalidateKvContinuation()
                throw e
            } catch (e: Exception) {
                invalidateKvContinuation()
                throw e
            }
        }

        @Suppress("UnusedParameter")
        internal fun clearAppendOnlyOnRustCompactionBoundary(revisionAfter: Long? = null) {
            invalidateKvContinuation()
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
                    mutex.withLock { invalidateKvContinuation() }
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
            timing: InferenceTurnTiming? = null,
        ): BufferedGeneration {
            var completedText: String? = null
            var promptTokens: Int? = null
            var generatedTokens: Int? = null
            var prefixReusedTokens: Int? = null
            val accumulatedThought = StringBuilder()
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
                                SegmentKind.THINKING -> {
                                    timing?.markFirstOutput()
                                    accumulatedThought.append(segment.text)
                                    emitEvent(InferenceEvent.Thinking(segment.text, targetModelType))
                                }
                                SegmentKind.VISIBLE -> {
                                    timing?.markFirstOutput()
                                    if (emitVisible) {
                                        emitEvent(InferenceEvent.PartialResponse(segment.text, targetModelType))
                                    }
                                }
                            }
                        }
                    }
                    is GenerationEvent.Completed -> {
                        promptTokens = event.promptTokens
                        generatedTokens = event.generatedTokens
                        prefixReusedTokens = event.prefixReusedTokens
                        if (sawToken) {
                            if (buffer.isNotEmpty()) {
                                if (isThinking) {
                                    timing?.markFirstOutput()
                                    accumulatedThought.append(buffer)
                                    emitEvent(InferenceEvent.Thinking(buffer, targetModelType))
                                } else {
                                    timing?.markFirstOutput()
                                    if (emitVisible) {
                                        emitEvent(InferenceEvent.PartialResponse(buffer, targetModelType))
                                    }
                                }
                            }
                        } else if (event.fullText.isNotBlank()) {
                            emitProcessedText(
                                rawText = event.fullText,
                                targetModelType = targetModelType,
                                timing = timing,
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
                accumulatedThought = accumulatedThought.toString(),
                promptTokens = promptTokens,
                generatedTokens = generatedTokens,
                prefixReusedTokens = prefixReusedTokens,
            )
        }

        private fun toLlamaDomainMessage(message: AgentLoopMessage): DomainChatMessage {
            if (EngineInternalMessageMapper.isEngineInternalMessage(message.metadataJson)) {
                return DomainChatMessage(
                    role = Role.SYSTEM,
                    content = EngineInternalMessageMapper.formatForProviderWire(message.content),
                )
            }
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

        private fun injectSidecarIntoLastUserMessage(
            messages: List<DomainChatMessage>,
            contextSidecars: String?,
        ): List<DomainChatMessage> {
            if (contextSidecars.isNullOrBlank()) return messages
            val lastUserIndex = messages.indexOfLast { it.role == Role.USER }
            if (lastUserIndex < 0) return messages

            return messages.mapIndexed { index, message ->
                if (index == lastUserIndex) {
                    message.copy(content = "[context]\n$contextSidecars\n[/context]\n\n${message.content}")
                } else {
                    message
                }
            }
        }

        private fun invalidateKvContinuation() {
            kvContinuationValid = false
        }

        private suspend fun emitProcessedText(
            rawText: String,
            targetModelType: ModelType,
            emitEvent: suspend (InferenceEvent) -> Unit,
            timing: InferenceTurnTiming? = null,
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
                    SegmentKind.THINKING -> {
                        timing?.markFirstOutput()
                        emitEvent(InferenceEvent.Thinking(segment.text, targetModelType))
                    }
                    SegmentKind.VISIBLE -> {
                        timing?.markFirstOutput()
                        emitEvent(InferenceEvent.PartialResponse(segment.text, targetModelType))
                    }
                }
            }

            if (buffer.isNotEmpty()) {
                if (isThinking) {
                    timing?.markFirstOutput()
                    emitEvent(InferenceEvent.Thinking(buffer, targetModelType))
                } else {
                    timing?.markFirstOutput()
                    emitEvent(InferenceEvent.PartialResponse(buffer, targetModelType))
                }
            }
        }
    }
