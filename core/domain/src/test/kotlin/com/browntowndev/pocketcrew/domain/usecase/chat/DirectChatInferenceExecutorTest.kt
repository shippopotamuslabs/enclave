package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceBusyException
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceFactory
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DirectChatInferenceExecutorTest {

    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var messageRepository: MessageRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var memoriesRepository: MemoriesRepository
    private lateinit var embeddingEnginePort: EmbeddingEnginePort
    private lateinit var searchToolPromptComposer: SearchToolPromptComposer
    private lateinit var loggingPort: LoggingPort
    private lateinit var inferenceFactory: FakeInferenceFactory
    private lateinit var executor: DirectChatInferenceExecutor

    @BeforeEach
    fun setup() {
        inferenceFactory = mockk()
        activeModelProvider = mockk()
        messageRepository = mockk()
        settingsRepository = mockk(relaxed = true) {
            every { settingsFlow } returns flowOf(SettingsData())
        }
        memoriesRepository = mockk(relaxed = true) {
            coEvery { getCoreMemories() } returns emptyList()
            coEvery { searchMemories(any(), any()) } returns emptyList()
        }
        embeddingEnginePort = mockk(relaxed = true) {
            coEvery { getEmbedding(any()) } returns floatArrayOf()
        }
        searchToolPromptComposer = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)

        executor = DirectChatInferenceExecutor(
            inferenceFactory = inferenceFactory,
            activeModelProvider = activeModelProvider,
            messageRepository = messageRepository,
            settingsRepository = settingsRepository,
            memoriesRepository = memoriesRepository,
            embeddingEnginePort = embeddingEnginePort,
            searchToolPromptComposer = searchToolPromptComposer,
            loggingPort = loggingPort,
        )
    }

    private fun createExecutor(
        inferenceFactory: FakeInferenceFactory,
        activeModelProvider: ActiveModelProviderPort = mockActiveModelProvider(),
        messageRepository: MessageRepository = mockMessageRepository(),
        settingsRepository: SettingsRepository = mockSettingsRepository(),
    ): DirectChatInferenceExecutor {
        return DirectChatInferenceExecutor(
            inferenceFactory = inferenceFactory,
            activeModelProvider = activeModelProvider,
            messageRepository = messageRepository,
            settingsRepository = settingsRepository,
            memoriesRepository = memoriesRepository,
            embeddingEnginePort = embeddingEnginePort,
            searchToolPromptComposer = searchToolPromptComposer,
            loggingPort = loggingPort,
        )
    }

    @Test
    fun `execute emits EngineLoading for InferenceEvent EngineLoading`() = runTest {
        val factory = FakeInferenceFactory()
        val service = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.EngineLoading(ModelType.FAST)))
        }
        factory.serviceMap[ModelType.FAST] = service

        val executor = createExecutor(factory)
        val states = executor.execute(
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
    fun `execute emits GeneratingText for PartialResponse`() = runTest {
        val factory = FakeInferenceFactory()
        val service = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(
                listOf(
                    InferenceEvent.PartialResponse("Hello ", ModelType.FAST),
                    InferenceEvent.Finished(ModelType.FAST),
                )
            )
        }
        factory.serviceMap[ModelType.FAST] = service

        val executor = createExecutor(factory)
        val states = executor.execute(
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
    fun `execute emits ThinkingLive for Thinking event`() = runTest {
        val factory = FakeInferenceFactory()
        val service = FakeInferenceService(ModelType.THINKING).apply {
            setEmittedEvents(
                listOf(
                    InferenceEvent.Thinking("hmm...", ModelType.THINKING),
                    InferenceEvent.Finished(ModelType.THINKING),
                )
            )
        }
        factory.serviceMap[ModelType.THINKING] = service

        val executor = createExecutor(factory)
        val states = executor.execute(
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
    fun `execute emits Finished for InferenceEvent Finished`() = runTest {
        val factory = FakeInferenceFactory()
        val service = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
        }
        factory.serviceMap[ModelType.FAST] = service

        val executor = createExecutor(factory)
        val states = executor.execute(
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
    fun `execute emits Blocked for SafetyBlocked event`() = runTest {
        val factory = FakeInferenceFactory()
        val service = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.SafetyBlocked("harmful", ModelType.FAST)))
        }
        factory.serviceMap[ModelType.FAST] = service

        val executor = createExecutor(factory)
        val states = executor.execute(
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
    fun `execute emits Failed for Error event`() = runTest {
        val factory = FakeInferenceFactory()
        val error = RuntimeException("model error")
        val service = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Error(error, ModelType.FAST)))
        }
        factory.serviceMap[ModelType.FAST] = service

        val executor = createExecutor(factory)
        val states = executor.execute(
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
    fun `execute emits Processing when userHasImage is true`() = runTest {
        val factory = FakeInferenceFactory()
        val service = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
        }
        factory.serviceMap[ModelType.FAST] = service

        val executor = createExecutor(factory)
        val states = executor.execute(
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
    fun `execute handles InferenceBusyException`() = runTest {
        val factory = FakeInferenceFactory()
        factory.exceptionToThrow = InferenceBusyException()

        val executor = createExecutor(factory)
        val states = executor.execute(
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
    fun `execute handles IllegalStateException`() = runTest {
        val factory = FakeInferenceFactory()
        factory.exceptionToThrow = IllegalStateException("no model loaded")

        val executor = createExecutor(factory)
        val states = executor.execute(
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
    fun `execute handles IOException`() = runTest {
        val factory = FakeInferenceFactory()
        factory.exceptionToThrow = java.io.IOException("disk error")

        val executor = createExecutor(factory)
        val states = executor.execute(
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
        assertTrue(failed.first().error is java.io.IOException)
    }

    @Test
    fun `execute handles generic exception and logs it`() = runTest {
        val factory = FakeInferenceFactory()
        factory.exceptionToThrow = RuntimeException("unexpected")

        val executor = createExecutor(factory)
        val states = executor.execute(
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
        verify { loggingPort.error(any(), any(), any<RuntimeException>()) }
    }

    @Test
    fun `stop is a no-op`() {
        val factory = FakeInferenceFactory()
        val executor = createExecutor(factory)
        // Should not throw
        executor.stop()
    }

    @Test
    fun `execute emits TavilySourcesAttached for TavilyResults`() = runTest {
        val factory = FakeInferenceFactory()
        val sources = listOf(
            com.browntowndev.pocketcrew.domain.model.chat.TavilySource(
                messageId = com.browntowndev.pocketcrew.domain.model.chat.MessageId("u1"),
                title = "Example",
                url = "https://example.com",
                content = "Content",
            )
        )
        val service = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(
                listOf(
                    InferenceEvent.TavilyResults(sources, ModelType.FAST),
                    InferenceEvent.Finished(ModelType.FAST),
                )
            )
        }
        factory.serviceMap[ModelType.FAST] = service

        val executor = createExecutor(factory)
        val states = executor.execute(
            prompt = "search this",
            userMessageId = MessageId("u1"),
            assistantMessageId = MessageId("a1"),
            chatId = ChatId("c1"),
            userHasImage = false,
            modelType = ModelType.FAST,
            backgroundInferenceEnabled = false,
        ).toList()

        val tavilyStates = states.filterIsInstance<MessageGenerationState.TavilySourcesAttached>()
        assertEquals(1, tavilyStates.size)
        assertEquals(sources, tavilyStates.first().sources)
    }

    private fun mockActiveModelProvider(): ActiveModelProviderPort = mockk {
        coEvery { getActiveConfiguration(ModelType.FAST) } returns ActiveModelConfiguration(
            id = LocalModelConfigurationId("fast"),
            isLocal = true,
            name = "Fast",
            systemPrompt = "",
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.95,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.1,
            contextWindow = 4096,
            thinkingEnabled = false,
        )
        coEvery { getActiveConfiguration(ModelType.THINKING) } returns ActiveModelConfiguration(
            id = LocalModelConfigurationId("thinking"),
            isLocal = true,
            name = "Thinking",
            systemPrompt = "",
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.95,
            maxTokens = 2048,
            minP = 0.0,
            repetitionPenalty = 1.1,
            contextWindow = 4096,
            thinkingEnabled = false,
        )
        coEvery { getActiveConfiguration(ModelType.VISION) } returns null
    }

    private fun mockMessageRepository(): MessageRepository = mockk(relaxed = true) {
        coEvery { getMessagesForChat(any()) } returns emptyList()
        coEvery { getChatSummary(any()) } returns null
        coEvery { resolveLatestImageBearingUserMessage(any(), any()) } returns null
    }

    private fun mockSettingsRepository(): SettingsRepository = mockk {
        every { settingsFlow } returns flowOf(SettingsData())
    }
}