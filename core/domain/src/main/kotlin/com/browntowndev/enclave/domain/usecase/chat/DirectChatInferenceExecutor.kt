package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.error.InferenceIOException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.enclave.domain.port.inference.CommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.InferenceBusyException
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.MonotonicToolActivationPort
import com.browntowndev.enclave.domain.port.inference.NoopCommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.PromptComposerPort
import com.browntowndev.enclave.domain.port.inference.SearchIntentClassifierPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.inference.NoopToolSearchEmbeddingCachePort
import com.browntowndev.enclave.domain.port.inference.ToolSearchEmbeddingCachePort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.CancellationException
import javax.inject.Inject

/**
 * Executes single-model inference directly in the caller's coroutine scope.
 * This is the fallback implementation when background inference is disabled.
 *
 * Pure Kotlin — no Android framework dependencies. Lives in `:domain`.
 */
class DirectChatInferenceExecutor
    @Inject
    constructor(
        private val inferenceFactory: InferenceFactoryPort,
        private val activeModelProvider: ActiveModelProviderPort,
        private val messageRepository: MessageRepository,
        private val settingsRepository: SettingsRepository,
        private val memoriesRepository: MemoriesRepository,
        private val embeddingEnginePort: EmbeddingEnginePort,
        private val promptComposer: PromptComposerPort,
        private val defaultModelRepository: com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort,
        private val mediaProviderRepository: com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort,
        private val mediaToolDefinitionFactory: com.browntowndev.enclave.domain.usecase.media.MediaToolDefinitionFactory,
        private val getProviderCapabilitiesUseCase: com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase,
        private val loggingPort: LoggingPort,
        private val searchIntentClassifier: SearchIntentClassifierPort,
        private val toolExecutor: ToolExecutorPort,
        private val monotonicToolActivation: MonotonicToolActivationPort = InMemoryMonotonicToolActivationStore(),
        private val toolSearchEmbeddingCache: ToolSearchEmbeddingCachePort = NoopToolSearchEmbeddingCachePort,
        private val committedInferencePrompts: CommittedInferencePromptPort = NoopCommittedInferencePromptPort,
    ) : ChatInferenceExecutorPort {
        private val historyRehydrator =
            ChatHistoryRehydrator(
                messageRepository = messageRepository,
                loggingPort = loggingPort,
                committedInferencePrompts = committedInferencePrompts,
            )
        private val inferenceRequestPreparer =
            ChatInferenceRequestPreparer(
                activeModelProvider = activeModelProvider,
                settingsRepository = settingsRepository,
                messageRepository = messageRepository,
                memoriesRepository = memoriesRepository,
                embeddingEnginePort = embeddingEnginePort,
                promptComposer = promptComposer,
                defaultModelRepository = defaultModelRepository,
                mediaProviderRepository = mediaProviderRepository,
                mediaToolDefinitionFactory = mediaToolDefinitionFactory,
                getProviderCapabilitiesUseCase = getProviderCapabilitiesUseCase,
                loggingPort = loggingPort,
                searchIntentClassifier = searchIntentClassifier,
                toolExecutor = toolExecutor,
                monotonicToolActivation = monotonicToolActivation,
                toolSearchEmbeddingCache = toolSearchEmbeddingCache,
            )

        override fun execute(
            prompt: String,
            userMessageId: MessageId,
            assistantMessageId: MessageId,
            chatId: ChatId,
            userHasImage: Boolean,
            modelType: ModelType,
            backgroundInferenceEnabled: Boolean, // Ignored by direct executor
        ): Flow<MessageGenerationState> =
            flow {
                try {
                    if (userHasImage) {
                        emit(MessageGenerationState.Processing(modelType))
                    }
                    inferenceFactory.withInferenceService(modelType) { service ->
                        emitAll(
                            generateWithService(
                                prompt = prompt,
                                userMessageId = userMessageId,
                                assistantMessageId = assistantMessageId,
                                chatId = chatId,
                                service = service,
                                modelType = modelType,
                            ),
                        )
                    }
                } catch (e: InferenceBusyException) {
                    emit(MessageGenerationState.Failed(error = e, modelType = modelType))
                } catch (e: ActiveModelConfigurationDecryptException) {
                    emit(MessageGenerationState.Failed(e, modelType))
                } catch (e: IllegalStateException) {
                    emit(MessageGenerationState.Failed(e, modelType))
                } catch (e: InferenceIOException) {
                    emit(MessageGenerationState.Failed(e, modelType))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Unexpected error in $modelType mode", e)
                    emit(MessageGenerationState.Failed(error = e, modelType = modelType))
                } catch (e: Throwable) {
                    loggingPort.error(TAG, "Unexpected error in $modelType mode due to system error", e)
                    emit(MessageGenerationState.Failed(error = RuntimeException(e), modelType = modelType))
                }
            }.flowOn(Dispatchers.Default)

        override fun stop() {
            // No-op for direct executor, cancellation is handled by the coroutine scope
        }

        private fun generateWithService(
            prompt: String,
            userMessageId: MessageId,
            assistantMessageId: MessageId,
            chatId: ChatId,
            service: LlmInferencePort,
            modelType: ModelType,
        ): Flow<MessageGenerationState> =
            flow {
                val preparedRequest =
                    inferenceRequestPreparer(
                        prompt = prompt,
                        chatId = chatId,
                        userMessageId = userMessageId,
                        assistantMessageId = assistantMessageId,
                        modelType = modelType,
                    )

                if (preparedRequest.preRetrievalSources.isNotEmpty()) {
                    emit(
                        MessageGenerationState.WebSearchSourcesAttached(
                            preparedRequest.preRetrievalSources,
                            modelType,
                        ),
                    )
                }

                try {
                    historyRehydrator(
                        chatId = chatId,
                        userMessageId = userMessageId,
                        assistantMessageId = assistantMessageId,
                        service = service,
                    )
                } catch (e: Exception) {
                    loggingPort.debug(TAG, "Failed to rehydrate history: ${e.message}")
                }

                service
                    .sendPrompt(
                        preparedRequest.prompt,
                        preparedRequest.options,
                        closeConversation = false,
                    ).collect { event ->
                        when (event) {
                            is InferenceEvent.EngineLoading -> {
                                emit(MessageGenerationState.EngineLoading(event.modelType))
                            }

                            is InferenceEvent.Processing -> {
                                emit(MessageGenerationState.Processing(event.modelType))
                            }

                            is InferenceEvent.Thinking -> {
                                emit(MessageGenerationState.ThinkingLive(event.chunk, modelType))
                            }

                            is InferenceEvent.PartialResponse -> {
                                emit(MessageGenerationState.GeneratingText(event.chunk, event.modelType))
                            }

                            is InferenceEvent.WebSearchResults -> {
                                emit(MessageGenerationState.WebSearchSourcesAttached(event.sources, event.modelType))
                            }

                            is InferenceEvent.Artifacts -> {
                                emit(MessageGenerationState.ArtifactsAttached(event.artifacts, event.modelType))
                            }

                            is InferenceEvent.Media -> {
                                emit(
                                    MessageGenerationState.MediaAttached(
                                        mediaUris = event.mediaUris,
                                        mediaIds = event.mediaIds,
                                        mediaTypes = event.mediaTypes,
                                        prompts = event.prompts,
                                        modelType = event.modelType,
                                    ),
                                )
                            }

                            is InferenceEvent.CompactionApplied -> Unit

                            is InferenceEvent.PreCompact -> Unit

                            is InferenceEvent.PostCompact -> Unit

                            is InferenceEvent.ToolRepaired -> Unit

                            is InferenceEvent.ModelApiRetryScheduled ->
                                emit(
                                    MessageGenerationState.ModelApiRetryScheduled(
                                        attempt = event.attempt,
                                        maxAttempts = event.maxAttempts,
                                        delayMs = event.delayMs,
                                        modelType = event.modelType,
                                    ),
                                )

                            is InferenceEvent.TelemetryCaptured -> {
                                emit(
                                    MessageGenerationState.TelemetryCaptured(
                                        telemetry = event.telemetry,
                                        modelType = event.modelType,
                                    ),
                                )
                            }

                            is InferenceEvent.Finished -> {
                                emit(MessageGenerationState.Finished(event.modelType))
                            }

                            is InferenceEvent.SafetyBlocked -> {
                                loggingPort.warning(
                                    TAG,
                                    "InferenceEvent.SafetyBlocked modelType=${event.modelType} reason=${event.reason}",
                                )
                                emit(MessageGenerationState.Blocked(event.reason, event.modelType))
                            }

                            is InferenceEvent.Error -> {
                                loggingPort.error(
                                    TAG,
                                    "InferenceEvent.Error modelType=${event.modelType} message=${event.cause.message}",
                                    event.cause,
                                )
                                emit(MessageGenerationState.Failed(event.cause, event.modelType))
                            }
                        }
                    }
            }

        private companion object {
            private const val TAG = "DirectChatInference"
        }
    }
