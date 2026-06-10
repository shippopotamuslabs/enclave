package com.browntowndev.enclave.feature.inference

import android.util.Log
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase.SegmentKind
import com.browntowndev.enclave.domain.util.WebSearchResultParser
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTurnTiming
import com.browntowndev.enclave.feature.inference.telemetry.estimateLiteRtPromptTokens
import com.browntowndev.enclave.feature.inference.telemetry.estimateTokenCount
import com.browntowndev.enclave.feature.inference.telemetry.resolveLiteRtInputTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.browntowndev.enclave.domain.model.chat.ChatMessage as DomainChatMessage

class LiteRtInferenceServiceImpl
    @Inject
    constructor(
        private val conversationManager: ConversationManager,
        private val processThinkingTokens: ProcessThinkingTokensUseCase,
        private val toolExecutionEventPort: ToolExecutionEventPort,
        private val modelType: ModelType,
        private val telemetrySupport: InferenceTelemetrySupport? = null,
    ) : LlmInferencePort {
        companion object {
            private const val TAG = "LiteRtInferenceService"
        }

        override fun sendPrompt(
            prompt: String,
            closeConversation: Boolean,
        ): Flow<InferenceEvent> = sendPrompt(prompt, GenerationOptions(reasoningBudget = 0, modelType = modelType), closeConversation)

        override suspend fun closeSession() {
            conversationManager.closeConversation()
            conversationManager.closeEngine()
        }

        override suspend fun setHistory(messages: List<DomainChatMessage>) {
            conversationManager.setHistory(messages)
        }

        @Suppress("CyclomaticComplexMethod")
        override fun sendPrompt(
            prompt: String,
            options: GenerationOptions,
            closeConversation: Boolean,
        ): Flow<InferenceEvent> =
            channelFlow {
                val requestId = conversationManager.beginGenerationRequest()
                val targetModelType = options.modelType ?: ModelType.FAST
                var isThinking = false
                val accumulatedThought = StringBuilder()
                val accumulatedText = StringBuilder()
                var buffer = ""

                val eventJob =
                    launch {
                        toolExecutionEventPort.events
                            .filterIsInstance<ToolExecutionEvent.Finished>()
                            .collect { event ->
                                if (options.chatId != null && event.chatId != options.chatId) return@collect
                                if (options.userMessageId != null && event.userMessageId != options.userMessageId) return@collect

                                val resultJson = event.resultJson
                                if (!resultJson.isNullOrEmpty()) {
                                    val assistantMessageId = options.assistantMessageId
                                    if (assistantMessageId != null) {
                                        val sources = WebSearchResultParser.parse(assistantMessageId, resultJson)
                                        val validSources = sources.filter { it.url.isNotEmpty() && it.title.isNotEmpty() }
                                        if (validSources.isNotEmpty()) {
                                            send(InferenceEvent.WebSearchResults(validSources, targetModelType))
                                        }

                                        val mediaResult =
                                            com.browntowndev.enclave.domain.util.MediaResultParser
                                                .parse(resultJson)
                                        if (mediaResult.uris.isNotEmpty()) {
                                            send(
                                                InferenceEvent.Media(
                                                    mediaUris = mediaResult.uris,
                                                    mediaIds = mediaResult.ids,
                                                    mediaTypes = mediaResult.types,
                                                    prompts = mediaResult.prompts,
                                                    modelType = targetModelType,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                    }

                suspend fun attemptInference(retryOnFail: Boolean) {
                    val captureContext = telemetrySupport?.captureContext(options)
                    try {
                        val conversation =
                            conversationManager.getConversation(targetModelType, options) {
                                send(InferenceEvent.EngineLoading(targetModelType))
                            }
                        send(InferenceEvent.Processing(targetModelType))

                        Log.d(TAG, "Sending prompt with options: $prompt")

                        val timing = telemetrySupport?.let { InferenceTurnTiming() }
                        conversation.sendMessageAsync(prompt, options).collect { response ->
                            if (response.thought.isNotEmpty()) {
                                timing?.markFirstOutput()
                                accumulatedThought.append(response.thought)
                                send(InferenceEvent.Thinking(response.thought, targetModelType))
                            }

                            if (response.text.isNotEmpty()) {
                                val state = processThinkingTokens(buffer, response.text, isThinking)
                                buffer = state.buffer
                                isThinking = state.isThinking

                                state.emittedSegments.forEach { segment ->
                                    when (segment.kind) {
                                        SegmentKind.THINKING -> {
                                            timing?.markFirstOutput()
                                            accumulatedThought.append(segment.text)
                                            send(InferenceEvent.Thinking(segment.text, targetModelType))
                                        }
                                        SegmentKind.VISIBLE -> {
                                            timing?.markFirstOutput()
                                            accumulatedText.append(segment.text)
                                            send(InferenceEvent.PartialResponse(segment.text, targetModelType))
                                        }
                                    }
                                }
                            }
                        }

                        if (buffer.isNotEmpty()) {
                            if (isThinking) {
                                accumulatedThought.append(buffer)
                                send(InferenceEvent.Thinking(buffer, targetModelType))
                            } else {
                                accumulatedText.append(buffer)
                                send(InferenceEvent.PartialResponse(buffer, targetModelType))
                            }
                        }

                        val benchmark = conversation.benchmarkInfo()
                        val outputTokens =
                            benchmark?.lastDecodeTokenCount?.takeIf { it > 0 }
                                ?: estimateTokenCount(accumulatedText)
                        val reasoningTokens = estimateTokenCount(accumulatedThought)
                        val telemetryContext = conversationManager.telemetryContext()
                        val estimatedPromptTokens =
                            estimateLiteRtPromptTokens(
                                history = telemetryContext.history,
                                currentPrompt = prompt,
                                systemPrompt = telemetryContext.systemPrompt,
                            )
                        val inputTelemetry =
                            resolveLiteRtInputTelemetry(
                                benchmarkPrefillTokens = benchmark?.lastPrefillTokenCount,
                                estimatedTotalPromptTokens = estimatedPromptTokens,
                            )
                        val telemetry =
                            if (telemetrySupport != null && timing != null && captureContext != null) {
                                telemetrySupport.localTurnTelemetry(
                                    context = captureContext,
                                    providerLabel = "LiteRT",
                                    modelLabel = options.modelId ?: "local",
                                    timing = timing,
                                    outputTokens = outputTokens,
                                    inputTokens = inputTelemetry.inputTokens,
                                    cacheReadInputTokens = inputTelemetry.cacheReadInputTokens,
                                    reasoningTokens = reasoningTokens,
                                )
                            } else {
                                null
                            }
                        telemetrySupport?.emitCaptured(telemetry, targetModelType) { send(it) }
                        send(InferenceEvent.Finished(targetModelType))
                    } catch (e: CancellationException) {
                        // Native cancelProcess() and coroutine cancellation both surface as
                        // CancellationException on JVM. Emit Finished for UI cleanup; rethrow
                        // for structured concurrency. Do not call cancelProcess() here — that
                        // would bump the epoch and break a newer in-flight request.
                        Log.i(TAG, "LiteRT inference cancelled for modelType=$targetModelType", e)
                        send(InferenceEvent.Finished(targetModelType))
                        throw e
                    } catch (e: Exception) {
                        if (retryOnFail) {
                            Log.w(TAG, "LiteRT inference failed; resetting engine and retrying once", e)
                            try {
                                conversationManager.closeEngine()
                            } catch (teardownError: Throwable) {
                                Log.w(TAG, "Failed to close engine during inference reset", teardownError)
                            }

                            // Reset accumulators before retry
                            isThinking = false
                            accumulatedThought.clear()
                            accumulatedText.clear()
                            buffer = ""

                            attemptInference(retryOnFail = false)
                        } else {
                            Log.e(TAG, "Error sending prompt (retry failed)", e)
                            send(InferenceEvent.Error(e, targetModelType))
                        }
                    }
                }

                try {
                    attemptInference(retryOnFail = true)
                } finally {
                    eventJob.cancel()
                    conversationManager.endGenerationRequest(requestId)
                    if (closeConversation) {
                        conversationManager.closeConversation()
                    }
                }
            }.flowOn(Dispatchers.Default)
    }
