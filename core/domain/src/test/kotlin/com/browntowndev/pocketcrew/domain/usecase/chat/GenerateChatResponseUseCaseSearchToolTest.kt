package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceFactory
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerateChatResponseUseCaseSearchToolTest {

    @Test
    fun `FAST mode exposes tavily_web_search for non-local active models`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val inferenceService = FakeInferenceService(ModelType.FAST)
        inferenceFactory.serviceMap[ModelType.FAST] = inferenceService
        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = mockk(relaxed = true),
            messageRepository = mockMessageRepository(),
            loggingPort = mockk(relaxed = true),
            activeModelProvider = mockActiveModelProvider(
                ActiveModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    isLocal = false,
                    name = "OpenAI Fast",
                    systemPrompt = "Be concise.",
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
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = true),
            searchToolPromptComposer = SearchToolPromptComposer(),
            analyzeImageUseCase = AnalyzeImageUseCase(inferenceFactory, mockk(relaxed = true)),
        )

        useCase(
            prompt = "Find recent Android agent news",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("3"),
            mode = Mode.FAST,
        ).toList()

        val options = inferenceService.getSentOptions().single()
        assertTrue(options.toolingEnabled)
        assertEquals(listOf(ToolDefinition.TAVILY_WEB_SEARCH), options.availableTools)
    }

    @Test
    fun `persisted assistant text excludes raw tool trace payloads`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val inferenceService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(
                listOf(
                    InferenceEvent.PartialResponse(
                        chunk = """<tool_call>{"name":"tavily_web_search","arguments":{"query":"latest android tool calling"}}</tool_call>""",
                        modelType = ModelType.FAST,
                    ),
                    InferenceEvent.PartialResponse(
                        chunk = "Use the search result summary.",
                        modelType = ModelType.FAST,
                    ),
                    InferenceEvent.Finished(ModelType.FAST),
                )
            )
        }
        inferenceFactory.serviceMap[ModelType.FAST] = inferenceService
        val chatRepository = mockk<ChatRepository>(relaxed = true)
        val contentSlot = slot<String>()
        coEvery {
            chatRepository.persistAllMessageData(
                messageId = any(),
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = capture(contentSlot),
                messageState = any(),
                pipelineStep = any(),
            )
        } returns Unit

        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = chatRepository,
            messageRepository = mockMessageRepository(),
            loggingPort = mockk<LoggingPort>(relaxed = true),
            activeModelProvider = mockActiveModelProvider(
                ActiveModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    isLocal = false,
                    name = "OpenAI Fast",
                    systemPrompt = "Be concise.",
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
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = true),
            searchToolPromptComposer = SearchToolPromptComposer(),
            analyzeImageUseCase = AnalyzeImageUseCase(inferenceFactory, mockk(relaxed = true)),
        )

        val assistantMessageId = MessageId("2")
        useCase(
            prompt = "Find recent Android agent news",
            userMessageId = MessageId("1"),
            assistantMessageId = assistantMessageId,
            chatId = ChatId("3"),
            mode = Mode.FAST,
        ).toList()

        coVerify {
            chatRepository.persistAllMessageData(
                messageId = assistantMessageId,
                modelType = ModelType.FAST,
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = any(),
                messageState = MessageState.COMPLETE,
                pipelineStep = any(),
            )
        }
        assertEquals("Use the search result summary.", contentSlot.captured)
        assertFalse(contentSlot.captured.contains("<tool_call>"))
    }

    @Test
    fun `FAST mode composes the local search tool contract only for local active models`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val inferenceService = FakeInferenceService(ModelType.FAST)
        inferenceFactory.serviceMap[ModelType.FAST] = inferenceService
        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = mockk(relaxed = true),
            messageRepository = mockMessageRepository(),
            loggingPort = mockk(relaxed = true),
            activeModelProvider = mockActiveModelProvider(
                ActiveModelConfiguration(
                    id = LocalModelConfigurationId("8"),
                    isLocal = true,
                    name = "LiteRT Fast",
                    systemPrompt = "Be concise.",
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
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = true),
            searchToolPromptComposer = SearchToolPromptComposer(),
            analyzeImageUseCase = AnalyzeImageUseCase(inferenceFactory, mockk(relaxed = true)),
        )

        useCase(
            prompt = "Find recent Android agent news",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("3"),
            mode = Mode.FAST,
        ).toList()

        val options = inferenceService.getSentOptions().single()
        assertTrue(options.toolingEnabled)
        assertTrue(options.systemPrompt?.contains("Be concise.") == true)
        assertTrue(
            options.systemPrompt?.contains(
                """<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>"""
            ) == true
        )
    }

    @Test
    fun `FAST mode does not expose tools when search is disabled`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val inferenceService = FakeInferenceService(ModelType.FAST)
        inferenceFactory.serviceMap[ModelType.FAST] = inferenceService
        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = mockk(relaxed = true),
            messageRepository = mockMessageRepository(),
            loggingPort = mockk(relaxed = true),
            activeModelProvider = mockActiveModelProvider(
                ActiveModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    isLocal = false,
                    name = "OpenAI Fast",
                    systemPrompt = "Be concise.",
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
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = false),
            searchToolPromptComposer = SearchToolPromptComposer(),
            analyzeImageUseCase = AnalyzeImageUseCase(inferenceFactory, mockk(relaxed = true)),
        )

        useCase(
            prompt = "Find recent Android agent news",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("3"),
            mode = Mode.FAST,
        ).toList()

        val options = inferenceService.getSentOptions().single()
        assertFalse(options.toolingEnabled)
        assertTrue(options.availableTools.isEmpty())
    }

    @Test
    fun `local models do not get tool contract when search is disabled`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val inferenceService = FakeInferenceService(ModelType.FAST)
        inferenceFactory.serviceMap[ModelType.FAST] = inferenceService
        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = mockk(relaxed = true),
            messageRepository = mockMessageRepository(),
            loggingPort = mockk(relaxed = true),
            activeModelProvider = mockActiveModelProvider(
                ActiveModelConfiguration(
                    id = LocalModelConfigurationId("8"),
                    isLocal = true,
                    name = "LiteRT Fast",
                    systemPrompt = "Be concise.",
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
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = false),
            searchToolPromptComposer = SearchToolPromptComposer(),
            analyzeImageUseCase = AnalyzeImageUseCase(inferenceFactory, mockk(relaxed = true)),
        )

        useCase(
            prompt = "Find recent Android agent news",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("3"),
            mode = Mode.FAST,
        ).toList()

        val options = inferenceService.getSentOptions().single()
        assertEquals("Be concise.", options.systemPrompt)
        assertFalse(options.systemPrompt!!.contains("<tool_call>"))
    }

    @Test
    fun `FAST mode prepends analyzed image description before main inference`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val fastService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
        }
        val visionService = FakeInferenceService(ModelType.VISION).apply {
            setEmittedEvents(
                listOf(
                    InferenceEvent.PartialResponse("A red bicycle by a blue wall.", ModelType.VISION),
                    InferenceEvent.Finished(ModelType.VISION),
                )
            )
        }
        inferenceFactory.serviceMap[ModelType.FAST] = fastService
        inferenceFactory.serviceMap[ModelType.VISION] = visionService

        val messageRepository = mockk<MessageRepository> {
            coEvery { getMessagesForChat(any()) } returns emptyList()
            coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
            coEvery { saveVisionAnalysis(any(), any(), any(), any(), any()) } returns Unit
            coEvery { getMessageById(any()) } returns Message(
                id = MessageId("1"),
                chatId = ChatId("chat"),
                content = Content(text = "What should I fix?", imageUri = "file:///tmp/image.jpg"),
                role = Role.USER,
            )
        }

        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = mockk(relaxed = true),
            messageRepository = messageRepository,
            loggingPort = mockk(relaxed = true),
            activeModelProvider = mockActiveModelProvider(
                ActiveModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    isLocal = false,
                    name = "OpenAI Fast",
                    systemPrompt = "Be concise.",
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
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = false),
            searchToolPromptComposer = SearchToolPromptComposer(),
            analyzeImageUseCase = AnalyzeImageUseCase(inferenceFactory, mockk(relaxed = true)),
        )

        useCase(
            prompt = "What should I fix?",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
        ).toList()

        assertTrue(visionService.getSentOptions().single().imageUris.contains("file:///tmp/image.jpg"))
        assertTrue(fastService.getSentPrompts().single().contains("Attached image description:"))
        assertTrue(fastService.getSentPrompts().single().contains("A red bicycle by a blue wall."))
    }

    @Test
    fun `FAST mode logs chunked full prompt for local inference with analyzed image description`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val fastService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
        }
        val visionService = FakeInferenceService(ModelType.VISION).apply {
            setEmittedEvents(
                listOf(
                    InferenceEvent.PartialResponse("A red bicycle by a blue wall.", ModelType.VISION),
                    InferenceEvent.Finished(ModelType.VISION),
                )
            )
        }
        inferenceFactory.serviceMap[ModelType.FAST] = fastService
        inferenceFactory.serviceMap[ModelType.VISION] = visionService

        val messageRepository = mockk<MessageRepository> {
            coEvery { getMessagesForChat(any()) } returns emptyList()
            coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
            coEvery { saveVisionAnalysis(any(), any(), any(), any(), any()) } returns Unit
            coEvery { getMessageById(any()) } returns Message(
                id = MessageId("1"),
                chatId = ChatId("chat"),
                content = Content(text = "What should I fix?", imageUri = "file:///tmp/image.jpg"),
                role = Role.USER,
            )
        }
        val loggingPort = mockk<LoggingPort>(relaxed = true)

        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = mockk(relaxed = true),
            messageRepository = messageRepository,
            loggingPort = loggingPort,
            activeModelProvider = mockActiveModelProvider(
                ActiveModelConfiguration(
                    id = LocalModelConfigurationId("8"),
                    isLocal = true,
                    name = "LiteRT Fast",
                    systemPrompt = "Be concise.",
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
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = false),
            searchToolPromptComposer = SearchToolPromptComposer(),
            analyzeImageUseCase = AnalyzeImageUseCase(inferenceFactory, loggingPort),
        )

        useCase(
            prompt = "What should I fix?",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
        ).toList()

        verify {
            loggingPort.debug(
                "GenerateChatResponse",
                match { it.contains("Local prompt handoff modelType=FAST") && it.contains("containsImageDescription=true") }
            )
            loggingPort.debug(
                "GenerateChatResponse",
                match {
                    it.contains("Local prompt handoff chunk 1/1 modelType=FAST") &&
                        it.contains("Attached image description:") &&
                        it.contains("A red bicycle by a blue wall.") &&
                        it.contains("User request:") &&
                        it.contains("What should I fix?")
                }
            )
        }
    }

    @Test
    fun `FAST mode persists analyzed image description`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val fastService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
        }
        val visionService = FakeInferenceService(ModelType.VISION).apply {
            setEmittedEvents(
                listOf(
                    InferenceEvent.PartialResponse("A loose shelf mounted into drywall.", ModelType.VISION),
                    InferenceEvent.Finished(ModelType.VISION),
                )
            )
        }
        inferenceFactory.serviceMap[ModelType.FAST] = fastService
        inferenceFactory.serviceMap[ModelType.VISION] = visionService

        val messageRepository = mockk<MessageRepository>(relaxed = true) {
            coEvery { getMessagesForChat(any()) } returns emptyList()
            coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
            coEvery { saveVisionAnalysis(any(), any(), any(), any(), any()) } returns Unit
            coEvery { getMessageById(any()) } returns Message(
                id = MessageId("1"),
                chatId = ChatId("chat"),
                content = Content(text = "How should I repair this?", imageUri = "file:///tmp/image.jpg"),
                role = Role.USER,
            )
        }

        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = mockk(relaxed = true),
            messageRepository = messageRepository,
            loggingPort = mockk(relaxed = true),
            activeModelProvider = mockActiveModelProvider(
                ActiveModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    isLocal = false,
                    name = "OpenAI Fast",
                    systemPrompt = "Be concise.",
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
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = false),
            searchToolPromptComposer = SearchToolPromptComposer(),
            analyzeImageUseCase = AnalyzeImageUseCase(inferenceFactory, mockk(relaxed = true)),
        )

        useCase(
            prompt = "How should I repair this?",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
        ).toList()

        coVerify {
            messageRepository.saveVisionAnalysis(
                userMessageId = MessageId("1"),
                imageUri = "file:///tmp/image.jpg",
                promptText = "How should I repair this?",
                analysisText = "A loose shelf mounted into drywall.",
                modelType = ModelType.VISION,
            )
        }
    }

    @Test
    fun `image analysis failure is surfaced through normal generation state`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val visionService = FakeInferenceService(ModelType.VISION).apply {
            setShouldThrowOnSendPrompt(true)
        }
        val fastService = FakeInferenceService(ModelType.FAST)
        inferenceFactory.serviceMap[ModelType.VISION] = visionService
        inferenceFactory.serviceMap[ModelType.FAST] = fastService

        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = mockk(relaxed = true),
            messageRepository = mockk(relaxed = true) {
                coEvery { getMessagesForChat(any()) } returns emptyList()
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { saveVisionAnalysis(any(), any(), any(), any(), any()) } returns Unit
                coEvery { getMessageById(any()) } returns Message(
                    id = MessageId("1"),
                    chatId = ChatId("chat"),
                    content = Content(text = "Describe this", imageUri = "file:///tmp/image.jpg"),
                    role = Role.USER,
                )
            },
            loggingPort = mockk(relaxed = true),
            activeModelProvider = mockActiveModelProvider(
                ActiveModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    isLocal = false,
                    name = "OpenAI Fast",
                    systemPrompt = "Be concise.",
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
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = false),
            searchToolPromptComposer = SearchToolPromptComposer(),
            analyzeImageUseCase = AnalyzeImageUseCase(inferenceFactory, mockk(relaxed = true)),
        )

        val states = useCase(
            prompt = "Describe this",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
        ).toList()

        val finalAssistant = states.last().messages[MessageId("2")]
        assertTrue(finalAssistant?.content?.contains("Error: Simulated sendPrompt with options error") == true)
        assertTrue(fastService.getSentPrompts().isEmpty())
    }

    @Test
    fun `rehydration uses persisted vision analysis instead of placeholder`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val fastService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
        }
        inferenceFactory.serviceMap[ModelType.FAST] = fastService

        val priorUser = Message(
            id = MessageId("prior-user"),
            chatId = ChatId("chat"),
            content = Content(text = "", imageUri = "file:///tmp/prior.jpg"),
            role = Role.USER,
            messageState = MessageState.COMPLETE,
        )
        val currentUser = Message(
            id = MessageId("1"),
            chatId = ChatId("chat"),
            content = Content(text = "What changed?"),
            role = Role.USER,
        )
        val analysis = MessageVisionAnalysis(
            id = "analysis-1",
            userMessageId = MessageId("prior-user"),
            imageUri = "file:///tmp/prior.jpg",
            promptText = "",
            analysisText = "A cracked pipe joint under the sink.",
            modelType = ModelType.VISION,
            createdAt = 1L,
            updatedAt = 1L,
        )

        val messageRepository = mockk<MessageRepository>(relaxed = true) {
            coEvery { getMessageById(MessageId("1")) } returns currentUser
            coEvery { getMessagesForChat(ChatId("chat")) } returns listOf(priorUser, currentUser)
            coEvery { getVisionAnalysesForMessages(any()) } answers {
                val ids = firstArg<List<MessageId>>()
                ids.associateWith { id ->
                    if (id == MessageId("prior-user")) listOf(analysis) else emptyList()
                }.filterValues { it.isNotEmpty() }
            }
            coEvery { saveVisionAnalysis(any(), any(), any(), any(), any()) } returns Unit
        }

        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = mockk(relaxed = true),
            messageRepository = messageRepository,
            loggingPort = mockk(relaxed = true),
            activeModelProvider = mockActiveModelProvider(
                ActiveModelConfiguration(
                    id = ApiModelConfigurationId("7"),
                    isLocal = false,
                    name = "OpenAI Fast",
                    systemPrompt = "Be concise.",
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
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = false),
            searchToolPromptComposer = SearchToolPromptComposer(),
            analyzeImageUseCase = AnalyzeImageUseCase(inferenceFactory, mockk(relaxed = true)),
        )

        useCase(
            prompt = "What changed?",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
        ).toList()

        assertTrue(fastService.getHistory().single().content.contains("A cracked pipe joint under the sink."))
        assertFalse(fastService.getHistory().single().content.contains("[User attached an image]"))
    }

    private fun mockActiveModelProvider(
        configuration: ActiveModelConfiguration,
    ): ActiveModelProviderPort = mockk {
        coEvery { getActiveConfiguration(ModelType.FAST) } returns configuration
    }

    private fun mockMessageRepository(): MessageRepository = mockk {
        coEvery { getMessagesForChat(any()) } returns emptyList()
        coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
        coEvery { saveVisionAnalysis(any(), any(), any(), any(), any()) } returns Unit
        coEvery { getMessageById(any()) } returns Message(
            id = MessageId("1"),
            chatId = ChatId("1"),
            content = Content(""),
            role = Role.USER,
        )
        coEvery { saveMessage(any()) } returns MessageId("1")
    }

    private fun mockPipelineExecutor(): PipelineExecutorPort = mockk {
        every { executePipeline(any(), any()) } returns emptyFlow()
        coEvery { stopPipeline(any()) } returns Unit
        coEvery { resumeFromState(any(), any(), any(), any()) } returns emptyFlow()
    }

    private fun mockSettingsRepository(searchEnabled: Boolean): SettingsRepository = mockk {
        every { settingsFlow } returns flowOf(SettingsData(searchEnabled = searchEnabled))
    }
}
