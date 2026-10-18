package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatInferenceExecutorPort
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.ExtractedUrlTrackerPort
import com.browntowndev.enclave.domain.usecase.FakeInferenceLockManager
import com.browntowndev.enclave.domain.usecase.FakePipelineExecutor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerateChatResponseUseCaseBackgroundServicePersistenceTest {
    @Test
    fun `direct inference publishes accumulated snapshots to active turn port`() =
        runTest {
            val userMessageId = MessageId("user")
            val assistantMessageId = MessageId("assistant")
            val chatId = ChatId("chat")
            val activeChatTurnSnapshotPort = RecordingActiveChatTurnSnapshotPort()
            val chatInferenceExecutor = completedFastExecutor("final chunk")
            val useCase =
                createUseCase(
                    userMessageId = userMessageId,
                    chatId = chatId,
                    chatInferenceExecutor = chatInferenceExecutor,
                    activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
                )

            val emissions =
                useCase(
                    prompt = "hello",
                    userMessageId = userMessageId,
                    assistantMessageId = assistantMessageId,
                    chatId = chatId,
                    responseMode = ChatResponseMode.Fast,
                    backgroundInferenceEnabled = false,
                ).toList()

            val key =
                ActiveChatTurnKey(
                    chatId = chatId,
                    assistantMessageId = assistantMessageId,
                )
            val publishedSnapshots = activeChatTurnSnapshotPort.published.map { it.second }

            assertEquals(emissions, publishedSnapshots)
            assertTrue(activeChatTurnSnapshotPort.published.all { it.first == key })
            assertEquals(
                "final chunk",
                publishedSnapshots
                    .last()
                    .messages
                    .getValue(assistantMessageId)
                    .content,
            )
            assertTrue(
                publishedSnapshots
                    .last()
                    .messages
                    .getValue(assistantMessageId)
                    .isComplete,
            )
        }

    @Test
    fun `model override with background inference enabled does not publish snapshots from use case`() =
        runTest {
            val userMessageId = MessageId("user")
            val assistantMessageId = MessageId("assistant")
            val chatId = ChatId("chat")
            val activeChatTurnSnapshotPort = RecordingActiveChatTurnSnapshotPort()
            val overrideConfig = activeApiConfig(name = "Claude Sonnet", id = ApiModelConfigurationId("api-claude"))
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-claude"),
                    displayName = "Claude Sonnet",
                )
            val activeModelProvider =
                FakeActiveModelProvider().apply {
                    overrideConfiguration = overrideConfig
                    hasOverrideConfiguration = true
                }
            val chatInferenceExecutor = FakeChatInferenceExecutorPort()
            val useCase =
                createUseCase(
                    userMessageId = userMessageId,
                    chatId = chatId,
                    chatInferenceExecutor = chatInferenceExecutor,
                    activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
                    activeModelProvider = activeModelProvider,
                )

            useCase(
                prompt = "hello",
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                responseMode = ChatResponseMode.Other(modelOverride = override),
                backgroundInferenceEnabled = true,
            ).toList()

            assertTrue(activeChatTurnSnapshotPort.published.isEmpty())
        }

    @Test
    fun `background service start without a state stream does not duplicate persistence or publish snapshots from use case`() =
        runTest {
            val userMessageId = MessageId("user")
            val assistantMessageId = MessageId("assistant")
            val chatId = ChatId("chat")
            val activeChatTurnSnapshotPort = RecordingActiveChatTurnSnapshotPort()
            val chatRepository = FakeChatRepository()
            val chatInferenceExecutor = emptyBackgroundExecutor()
            val useCase =
                createUseCase(
                    userMessageId = userMessageId,
                    chatId = chatId,
                    chatInferenceExecutor = chatInferenceExecutor,
                    activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
                    chatRepository = chatRepository,
                )

            useCase(
                prompt = "hello",
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                responseMode = ChatResponseMode.Fast,
                backgroundInferenceEnabled = true,
            ).toList()

            assertTrue(activeChatTurnSnapshotPort.published.isEmpty())
            assertTrue(
                chatRepository.persistAllMessageDataCalls.none {
                    it.messageId == assistantMessageId
                },
            )
        }

    @Test
    fun `background service start failure persists the terminal error`() =
        runTest {
            val userMessageId = MessageId("user")
            val assistantMessageId = MessageId("assistant")
            val chatId = ChatId("chat")
            val chatRepository = FakeChatRepository()
            val chatInferenceExecutor =
                FakeChatInferenceExecutorPort().apply {
                    flow =
                        flowOf(
                            MessageGenerationState.Failed(
                                IllegalStateException("service start failed"),
                                ModelType.FAST,
                            ),
                        )
                }
            val useCase =
                createUseCase(
                    userMessageId = userMessageId,
                    chatId = chatId,
                    chatInferenceExecutor = chatInferenceExecutor,
                    activeChatTurnSnapshotPort = RecordingActiveChatTurnSnapshotPort(),
                    chatRepository = chatRepository,
                )

            useCase(
                prompt = "hello",
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                responseMode = ChatResponseMode.Fast,
                backgroundInferenceEnabled = true,
            ).toList()

            val matchingIndexes =
                chatRepository.persistAllMessageDataCalls.withIndex()
                    .filter { (_, call) ->
                        call.messageId == assistantMessageId &&
                            call.messageState == MessageState.COMPLETE &&
                            call.content.contains("service start failed")
                    }
                    .map { it.index }
            assertEquals(1, matchingIndexes.size)
            val persistedIndex = matchingIndexes.single()
            assertEquals(ModelType.FAST, chatRepository.persistAllMessageDataModelTypes[persistedIndex])
            assertNull(chatRepository.persistAllMessageDataThinkingRaw[persistedIndex])
        }

    @Test
    fun `background service cancellation before terminal state does not persist truncated assistant message`() =
        runTest {
            val userMessageId = MessageId("user")
            val assistantMessageId = MessageId("assistant")
            val chatId = ChatId("chat")
            val chatRepository = FakeChatRepository()
            val messageRepository =
                FakeMessageRepository(
                    initial =
                        listOf(
                            Message(
                                id = userMessageId,
                                chatId = chatId,
                                content = Content(text = "hello"),
                                role = Role.USER,
                            ),
                        ),
                )
            val chatInferenceExecutor =
                FakeChatInferenceExecutorPort().apply {
                    flow =
                        flow {
                            emit(MessageGenerationState.GeneratingText("partial", ModelType.FAST))
                            awaitCancellation()
                        }
                }
            val useCase =
                GenerateChatResponseUseCase(
                    pipelineExecutor = FakePipelineExecutor(),
                    chatRepository = chatRepository,
                    messageRepository = messageRepository,
                    loggingPort = FakeLoggingPort(),
                    activeModelProvider = FakeActiveModelProvider(),
                    extractedUrlTracker = noOpExtractedUrlTracker(),
                    chatInferenceExecutor = chatInferenceExecutor,
                    embeddingEngine = FakeEmbeddingEngine(),
                    inferenceLockManager = FakeInferenceLockManager(),
                )

            useCase(
                prompt = "hello",
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                responseMode = ChatResponseMode.Fast,
                backgroundInferenceEnabled = true,
            ).first()

            assertTrue(
                chatRepository.persistAllMessageDataCalls.none {
                    it.messageId == assistantMessageId && it.messageState == MessageState.COMPLETE
                },
            )
        }

    private fun createUseCase(
        userMessageId: MessageId,
        chatId: ChatId,
        chatInferenceExecutor: ChatInferenceExecutorPort,
        activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
        chatRepository: ChatRepository = FakeChatRepository(),
        activeModelProvider: ActiveModelProviderPort = FakeActiveModelProvider(),
    ): GenerateChatResponseUseCase {
        val messageRepository =
            FakeMessageRepository(
                initial =
                    listOf(
                        Message(
                            id = userMessageId,
                            chatId = chatId,
                            content = Content(text = "hello"),
                            role = Role.USER,
                        ),
                    ),
            )
        return GenerateChatResponseUseCase(
            pipelineExecutor = FakePipelineExecutor(),
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = FakeLoggingPort(),
            activeModelProvider = activeModelProvider,
            extractedUrlTracker = noOpExtractedUrlTracker(),
            chatInferenceExecutor = chatInferenceExecutor,
            embeddingEngine = FakeEmbeddingEngine(),
            activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
            inferenceLockManager = FakeInferenceLockManager(),
        )
    }

    private fun completedFastExecutor(text: String): FakeChatInferenceExecutorPort =
        FakeChatInferenceExecutorPort().apply {
            flow =
                flow {
                    emit(MessageGenerationState.GeneratingText(text, ModelType.FAST))
                    emit(MessageGenerationState.Finished(ModelType.FAST))
                }
        }

    private fun emptyBackgroundExecutor(): FakeChatInferenceExecutorPort =
        FakeChatInferenceExecutorPort().apply {
            flow = emptyFlow()
        }

    private fun activeApiConfig(
        name: String,
        id: ApiModelConfigurationId,
    ): ActiveModelConfiguration =
        ActiveModelConfiguration(
            modelId = name.lowercase().replace(" ", "-"),
            id = id,
            isLocal = false,
            name = name,
            systemPrompt = null,
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.95,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.1,
            contextWindow = 4096,
            thinkingEnabled = true,
        )

    private fun noOpExtractedUrlTracker(): ExtractedUrlTrackerPort =
        object : ExtractedUrlTrackerPort {
            override val urls: Set<String> get() = emptySet()

            override fun add(url: String) = Unit

            override fun clear() = Unit
        }

    private class RecordingActiveChatTurnSnapshotPort : ActiveChatTurnSnapshotPort {
        val published = mutableListOf<Pair<ActiveChatTurnKey, AccumulatedMessages>>()

        override fun observe(key: ActiveChatTurnKey): Flow<AccumulatedMessages?> = flowOf(null)

        override suspend fun publish(
            key: ActiveChatTurnKey,
            snapshot: AccumulatedMessages,
        ) {
            published += key to snapshot
        }

        override suspend fun markWebSearchSourcesExtracted(
            key: ActiveChatTurnKey,
            urls: List<String>,
        ) = Unit

        override suspend fun acknowledgeHandoff(key: ActiveChatTurnKey) = Unit

        override suspend fun attachArtifact(
            key: ActiveChatTurnKey,
            assistantMessageId: MessageId,
            artifact: com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest,
        ) = Unit

        override suspend fun attachMedia(
            key: ActiveChatTurnKey,
            assistantMessageId: MessageId,
            mediaUris: List<String>,
            mediaIds: List<String>,
            mediaTypes: List<String>,
            prompts: List<String>,
        ) = Unit

        override suspend fun clear(key: ActiveChatTurnKey) = Unit

        override suspend fun getSnapshot(key: ActiveChatTurnKey): AccumulatedMessages? = null
    }
}
