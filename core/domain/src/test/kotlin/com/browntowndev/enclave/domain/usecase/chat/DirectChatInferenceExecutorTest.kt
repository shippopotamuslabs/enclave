package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLlmInferencePort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.FakePromptComposer
import com.browntowndev.enclave.core.testing.FakeSearchIntentClassifier
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceBusyException
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.usecase.FakeInferenceFactory
import com.browntowndev.enclave.domain.usecase.FakeInferenceService
import com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.MediaToolDefinitionFactory
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DirectChatInferenceExecutorTest {
    private lateinit var activeModelProvider: FakeActiveModelProvider
    private lateinit var messageRepository: FakeMessageRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var memoriesRepository: FakeMemoriesRepository
    private lateinit var embeddingEnginePort: FakeEmbeddingEngine
    private lateinit var promptComposer: FakePromptComposer
    private lateinit var defaultModelRepository: FakeDefaultModelRepository
    private lateinit var mediaProviderRepository: FakeMediaProviderRepository
    private lateinit var mediaToolDefinitionFactory: MediaToolDefinitionFactory
    private lateinit var getProviderCapabilitiesUseCase: GetProviderCapabilitiesUseCase
    private lateinit var loggingPort: FakeLoggingPort
    private lateinit var inferenceService: FakeLlmInferencePort
    private lateinit var inferenceFactory: FakeInferenceFactory
    private lateinit var executor: DirectChatInferenceExecutor

    @BeforeEach
    fun setup() {
        inferenceFactory = FakeInferenceFactory()
        inferenceService = FakeLlmInferencePort()
        inferenceFactory.serviceMap[ModelType.FAST] = inferenceService
        inferenceFactory.serviceMap[ModelType.VISION] = inferenceService
        activeModelProvider = FakeActiveModelProvider(configuration = null)
        messageRepository = FakeMessageRepository()
        settingsRepository = FakeSettingsRepository()
        memoriesRepository = FakeMemoriesRepository()
        embeddingEnginePort = FakeEmbeddingEngine()
        promptComposer = FakePromptComposer().apply { resultOverride = "" }
        defaultModelRepository = FakeDefaultModelRepository()
        mediaProviderRepository = FakeMediaProviderRepository()
        getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase()
        mediaToolDefinitionFactory = MediaToolDefinitionFactory(getProviderCapabilitiesUseCase)
        loggingPort = FakeLoggingPort()

        executor =
            DirectChatInferenceExecutor(
                inferenceFactory = inferenceFactory,
                activeModelProvider = activeModelProvider,
                messageRepository = messageRepository,
                settingsRepository = settingsRepository,
                memoriesRepository = memoriesRepository,
                embeddingEnginePort = embeddingEnginePort,
                promptComposer = promptComposer,
                defaultModelRepository = defaultModelRepository,
                mediaProviderRepository = mediaProviderRepository,
                mediaToolDefinitionFactory = mediaToolDefinitionFactory,
                getProviderCapabilitiesUseCase = getProviderCapabilitiesUseCase,
                loggingPort = loggingPort,
                searchIntentClassifier = FakeSearchIntentClassifier(),
                toolExecutor = FakeToolExecutor(),
            )
    }

    private fun createExecutor(
        inferenceFactory: FakeInferenceFactory,
        activeModelProvider: ActiveModelProviderPort = fakeActiveModelProvider(),
        messageRepository: MessageRepository = fakeMessageRepository(),
        settingsRepository: SettingsRepository = fakeSettingsRepository(),
    ): DirectChatInferenceExecutor =
        DirectChatInferenceExecutor(
            inferenceFactory = inferenceFactory,
            activeModelProvider = activeModelProvider,
            messageRepository = messageRepository,
            settingsRepository = settingsRepository,
            memoriesRepository = memoriesRepository,
            embeddingEnginePort = embeddingEnginePort,
            promptComposer = promptComposer,
            defaultModelRepository = defaultModelRepository,
            mediaProviderRepository = mediaProviderRepository,
            mediaToolDefinitionFactory = mediaToolDefinitionFactory,
            getProviderCapabilitiesUseCase = getProviderCapabilitiesUseCase,
            loggingPort = loggingPort,
            searchIntentClassifier = FakeSearchIntentClassifier(),
            toolExecutor = FakeToolExecutor(),
        )

    @Test
    fun `execute delegates search intent classification to injected classifier when search enabled`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    probability = 0.9f
                    requiresSearchResult = true
                }

            val factory = FakeInferenceFactory()
            factory.serviceMap[ModelType.FAST] =
                FakeInferenceService(ModelType.FAST).apply {
                    setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
                }

            val executor =
                DirectChatInferenceExecutor(
                    inferenceFactory = factory,
                    activeModelProvider = activeModelProvider,
                    messageRepository = messageRepository,
                    settingsRepository = fakeSettingsRepository(SettingsData(searchEnabled = true, tavilyKeyPresent = true)),
                    memoriesRepository = memoriesRepository,
                    embeddingEnginePort =
                        FakeEmbeddingEngine().apply {
                            uniformEmbedding = FloatArray(384) { 0.1f }
                        },
                    promptComposer = promptComposer,
                    defaultModelRepository = defaultModelRepository,
                    mediaProviderRepository = mediaProviderRepository,
                    mediaToolDefinitionFactory = mediaToolDefinitionFactory,
                    getProviderCapabilitiesUseCase = getProviderCapabilitiesUseCase,
                    loggingPort = loggingPort,
                    searchIntentClassifier = classifier,
                    toolExecutor = FakeToolExecutor(),
                )

            executor
                .execute(
                    prompt = "What are future proof sectors for software engineers?",
                    userMessageId = MessageId("u1"),
                    assistantMessageId = MessageId("a1"),
                    chatId = ChatId("c1"),
                    userHasImage = false,
                    modelType = ModelType.FAST,
                    backgroundInferenceEnabled = false,
                ).toList()

            assertTrue(classifier.probabilityCallCount >= 1)
            assertTrue(classifier.requiresSearchCallCount >= 1)
        }

    @Test
    fun `execute emits EngineLoading for InferenceEvent EngineLoading`() =
        runTest {
            val factory = FakeInferenceFactory()
            val service =
                FakeInferenceService(ModelType.FAST).apply {
                    setEmittedEvents(listOf(InferenceEvent.EngineLoading(ModelType.FAST)))
                }
            factory.serviceMap[ModelType.FAST] = service

            val executor = createExecutor(factory)
            val states =
                executor
                    .execute(
                        prompt = "hello",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = false,
                    ).toList()

            assertTrue(states.any { it is MessageGenerationState.EngineLoading })
        }

    @Test
    fun `execute emits GeneratingText for PartialResponse`() =
        runTest {
            val factory = FakeInferenceFactory()
            val service =
                FakeInferenceService(ModelType.FAST).apply {
                    setEmittedEvents(
                        listOf(
                            InferenceEvent.PartialResponse("Hello ", ModelType.FAST),
                            InferenceEvent.Finished(ModelType.FAST),
                        ),
                    )
                }
            factory.serviceMap[ModelType.FAST] = service

            val executor = createExecutor(factory)
            val states =
                executor
                    .execute(
                        prompt = "hi",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = false,
                    ).toList()

            val generatingStates = states.filterIsInstance<MessageGenerationState.GeneratingText>()
            assertEquals(1, generatingStates.size)
            assertEquals("Hello ", generatingStates.first().textDelta)
        }

    @Test
    fun `execute emits ThinkingLive for Thinking event`() =
        runTest {
            val factory = FakeInferenceFactory()
            val service =
                FakeInferenceService(ModelType.THINKING).apply {
                    setEmittedEvents(
                        listOf(
                            InferenceEvent.Thinking("hmm...", ModelType.THINKING),
                            InferenceEvent.Finished(ModelType.THINKING),
                        ),
                    )
                }
            factory.serviceMap[ModelType.THINKING] = service

            val executor = createExecutor(factory)
            val states =
                executor
                    .execute(
                        prompt = "think",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = false,
                        modelType = ModelType.THINKING,
                        backgroundInferenceEnabled = false,
                    ).toList()

            val thinkingStates = states.filterIsInstance<MessageGenerationState.ThinkingLive>()
            assertEquals(1, thinkingStates.size)
            assertEquals("hmm...", thinkingStates.first().thinkingChunk)
        }

    @Test
    fun `execute emits Finished for InferenceEvent Finished`() =
        runTest {
            val factory = FakeInferenceFactory()
            val service =
                FakeInferenceService(ModelType.FAST).apply {
                    setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
                }
            factory.serviceMap[ModelType.FAST] = service

            val executor = createExecutor(factory)
            val states =
                executor
                    .execute(
                        prompt = "hi",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = false,
                    ).toList()

            assertTrue(states.any { it is MessageGenerationState.Finished })
        }

    @Test
    fun `execute emits Blocked for SafetyBlocked event`() =
        runTest {
            val factory = FakeInferenceFactory()
            val service =
                FakeInferenceService(ModelType.FAST).apply {
                    setEmittedEvents(listOf(InferenceEvent.SafetyBlocked("harmful", ModelType.FAST)))
                }
            factory.serviceMap[ModelType.FAST] = service

            val executor = createExecutor(factory)
            val states =
                executor
                    .execute(
                        prompt = "bad",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = false,
                    ).toList()

            val blocked = states.filterIsInstance<MessageGenerationState.Blocked>()
            assertEquals(1, blocked.size)
            assertEquals("harmful", blocked.first().reason)
        }

    @Test
    fun `execute emits Failed for Error event`() =
        runTest {
            val factory = FakeInferenceFactory()
            val error = RuntimeException("model error")
            val service =
                FakeInferenceService(ModelType.FAST).apply {
                    setEmittedEvents(listOf(InferenceEvent.Error(error, ModelType.FAST)))
                }
            factory.serviceMap[ModelType.FAST] = service

            val executor = createExecutor(factory)
            val states =
                executor
                    .execute(
                        prompt = "hi",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = false,
                    ).toList()

            val failed = states.filterIsInstance<MessageGenerationState.Failed>()
            assertEquals(1, failed.size)
            assertEquals("model error", failed.first().error?.message)
        }

    @Test
    fun `execute emits Processing when userHasImage is true`() =
        runTest {
            val factory = FakeInferenceFactory()
            val service =
                FakeInferenceService(ModelType.FAST).apply {
                    setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
                }
            factory.serviceMap[ModelType.FAST] = service

            val executor = createExecutor(factory)
            val states =
                executor
                    .execute(
                        prompt = "look at this",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = true,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = false,
                    ).toList()

            val processing = states.filterIsInstance<MessageGenerationState.Processing>()
            assertTrue(processing.isNotEmpty(), "Should emit Processing state when userHasImage=true")
            assertEquals(ModelType.FAST, processing.first().modelType)
        }

    @Test
    fun `execute handles InferenceBusyException`() =
        runTest {
            val factory = FakeInferenceFactory()
            factory.exceptionToThrow = InferenceBusyException()

            val executor = createExecutor(factory)
            val states =
                executor
                    .execute(
                        prompt = "hi",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = false,
                    ).toList()

            val failed = states.filterIsInstance<MessageGenerationState.Failed>()
            assertEquals(1, failed.size)
            assertTrue(failed.first().error is InferenceBusyException)
        }

    @Test
    fun `execute handles IllegalStateException`() =
        runTest {
            val factory = FakeInferenceFactory()
            factory.exceptionToThrow = IllegalStateException("no model loaded")

            val executor = createExecutor(factory)
            val states =
                executor
                    .execute(
                        prompt = "hi",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = false,
                    ).toList()

            val failed = states.filterIsInstance<MessageGenerationState.Failed>()
            assertEquals(1, failed.size)
            assertTrue(failed.first().error is IllegalStateException)
        }

    @Test
    fun `execute handles InferenceIOException`() =
        runTest {
            val factory = FakeInferenceFactory()
            factory.exceptionToThrow =
                com.browntowndev.enclave.domain.model.error
                    .InferenceIOException("disk error")

            val executor = createExecutor(factory)
            val states =
                executor
                    .execute(
                        prompt = "hi",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = false,
                    ).toList()

            val failed = states.filterIsInstance<MessageGenerationState.Failed>()
            assertEquals(1, failed.size)
            assertTrue(failed.first().error is com.browntowndev.enclave.domain.model.error.InferenceIOException)
        }

    @Test
    fun `execute handles generic exception and logs it`() =
        runTest {
            val factory = FakeInferenceFactory()
            factory.exceptionToThrow = RuntimeException("unexpected")

            val executor = createExecutor(factory)
            val states =
                executor
                    .execute(
                        prompt = "hi",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = false,
                    ).toList()

            val failed = states.filterIsInstance<MessageGenerationState.Failed>()
            assertEquals(1, failed.size)
            assertTrue(loggingPort.errorCalls.any { it.throwable is RuntimeException })
        }

    @Test
    fun `stop is a no-op`() {
        val factory = FakeInferenceFactory()
        val executor = createExecutor(factory)
        // Should not throw
        executor.stop()
    }

    @Test
    fun `execute emits WebSearchSourcesAttached for WebSearchResults`() =
        runTest {
            val factory = FakeInferenceFactory()
            val sources =
                listOf(
                    com.browntowndev.enclave.domain.model.chat.WebSearchSource(
                        messageId =
                            com.browntowndev.enclave.domain.model.chat
                                .MessageId("u1"),
                        title = "Example",
                        url = "https://example.com",
                        content = "Content",
                    ),
                )
            val service =
                FakeInferenceService(ModelType.FAST).apply {
                    setEmittedEvents(
                        listOf(
                            InferenceEvent.WebSearchResults(sources, ModelType.FAST),
                            InferenceEvent.Finished(ModelType.FAST),
                        ),
                    )
                }
            factory.serviceMap[ModelType.FAST] = service

            val executor = createExecutor(factory)
            val states =
                executor
                    .execute(
                        prompt = "search this",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = false,
                    ).toList()

            val tavilyStates = states.filterIsInstance<MessageGenerationState.WebSearchSourcesAttached>()
            assertEquals(1, tavilyStates.size)
            assertEquals(sources, tavilyStates.first().sources)
        }

    @Test
    fun `emits failed state with decrypt message when active model cannot be read`() =
        runTest {
            val decryptError = ActiveModelConfigurationDecryptException(ModelType.FAST)
            activeModelProvider.failure = decryptError

            val states =
                executor
                    .execute(
                        prompt = "hello",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("c1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = false,
                    ).toList()

            val failed = states.filterIsInstance<MessageGenerationState.Failed>().single()
            assertTrue(failed.error is ActiveModelConfigurationDecryptException)
            assertEquals(ActiveModelConfigurationDecryptException.USER_MESSAGE, failed.error.message)
        }

    private fun fakeActiveModelProvider(
        fastConfig: ActiveModelConfiguration =
            ActiveModelConfiguration(
                modelId = "test-model",
                id = LocalModelConfigurationId("fast"),
                isLocal = true,
                name = "Fast",
                systemPrompt = "System",
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                maxTokens = 100,
                minP = 0.0,
                repetitionPenalty = 1.0,
                contextWindow = 4096,
                thinkingEnabled = false,
                isMultimodal = false,
            ),
    ): FakeActiveModelProvider = FakeActiveModelProvider(configuration = fastConfig)

    private fun fakeMessageRepository(): FakeMessageRepository = FakeMessageRepository()

    private fun fakeSettingsRepository(settings: SettingsData = SettingsData()): FakeSettingsRepository =
        FakeSettingsRepository(initial = settings)
}
