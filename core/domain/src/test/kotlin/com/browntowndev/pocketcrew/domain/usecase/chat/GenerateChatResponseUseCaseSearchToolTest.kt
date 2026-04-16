package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.chat.ResolvedImageTarget
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
        assertTrue(options.availableTools.contains(ToolDefinition.TAVILY_WEB_SEARCH))
        assertTrue(options.availableTools.contains(ToolDefinition.TAVILY_EXTRACT))
        assertTrue(options.availableTools.contains(ToolDefinition.SEARCH_CHAT_HISTORY))
        assertTrue(options.availableTools.contains(ToolDefinition.SEARCH_CHAT))
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
                tavilySources = any(),
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
                tavilySources = any(),
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
        fun `FAST mode handles missing configurations gracefully`() = runTest {
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
        assertFalse(options.availableTools.contains(ToolDefinition.TAVILY_WEB_SEARCH))
        assertTrue(options.availableTools.contains(ToolDefinition.SEARCH_CHAT_HISTORY))
        assertTrue(options.availableTools.contains(ToolDefinition.SEARCH_CHAT))
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
        )

        useCase(
            prompt = "Find recent Android agent news",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("3"),
            mode = Mode.FAST,
        ).toList()

        val options = inferenceService.getSentOptions().single()
        assertTrue(options.systemPrompt!!.contains("Be concise."))
        assertTrue(options.systemPrompt!!.contains("search_chat_history"))
        assertFalse(options.systemPrompt!!.contains("tavily_web_search"))
    }

    @Test
    fun `FAST mode exposes both search and image inspect tools when API vision is configured`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val fastService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
        }
        inferenceFactory.serviceMap[ModelType.FAST] = fastService

        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = mockk(relaxed = true),
            messageRepository = mockMessageRepository(
                currentMessage = Message(
                    id = MessageId("1"),
                    chatId = ChatId("chat"),
                    content = Content(text = "What is this?", imageUri = "file:///photo.jpg"),
                    role = Role.USER,
                ),
                resolvedImageTarget = ResolvedImageTarget(
                    userMessageId = MessageId("1"),
                    imageUri = "file:///photo.jpg",
                ),
            ),
            loggingPort = mockk(relaxed = true),
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig(),
                visionConfig = apiVisionConfig(),
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = true),
            searchToolPromptComposer = SearchToolPromptComposer(),
        )

        useCase(
            prompt = "What is this?",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
        ).toList()

        val options = fastService.getSentOptions().single()
        assertTrue(options.toolingEnabled)
        assertTrue(options.availableTools.contains(ToolDefinition.TAVILY_WEB_SEARCH))
        assertTrue(options.availableTools.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT))
        assertTrue(options.systemPrompt?.contains("attached_image_inspect") == true)
        assertEquals(listOf(ModelType.FAST), inferenceFactory.resolvedTypes)
    }

    @Test
    fun `FAST mode exposes only image inspect when search is disabled but API vision is configured`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val fastService = FakeInferenceService(ModelType.FAST).apply {
            setEmittedEvents(listOf(InferenceEvent.Finished(ModelType.FAST)))
        }
        inferenceFactory.serviceMap[ModelType.FAST] = fastService

        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = mockk(relaxed = true),
            messageRepository = mockMessageRepository(
                currentMessage = Message(
                    id = MessageId("1"),
                    chatId = ChatId("chat"),
                    content = Content(text = "Describe it", imageUri = "file:///photo.jpg"),
                    role = Role.USER,
                ),
                resolvedImageTarget = ResolvedImageTarget(
                    userMessageId = MessageId("1"),
                    imageUri = "file:///photo.jpg",
                ),
            ),
            loggingPort = mockk(relaxed = true),
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig(),
                visionConfig = apiVisionConfig(),
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = false),
            searchToolPromptComposer = SearchToolPromptComposer(),
        )

        useCase(
            prompt = "Describe it",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
        ).toList()

        val options = fastService.getSentOptions().single()
        assertTrue(options.toolingEnabled)
        assertFalse(options.availableTools.contains(ToolDefinition.TAVILY_WEB_SEARCH))
        assertTrue(options.availableTools.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT))
        assertTrue(options.systemPrompt?.contains("attached_image_inspect") == true)
        assertEquals(listOf(ModelType.FAST), inferenceFactory.resolvedTypes)
    }

    @Test
    fun `FAST mode does not expose image inspect when no resolved image exists`() = runTest {
        val inferenceFactory = FakeInferenceFactory()
        val fastService = FakeInferenceService(ModelType.FAST)
        inferenceFactory.serviceMap[ModelType.FAST] = fastService

        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = mockk(relaxed = true),
            messageRepository = mockMessageRepository(
                currentMessage = Message(
                    id = MessageId("1"),
                    chatId = ChatId("chat"),
                    content = Content(text = "Hello"),
                    role = Role.USER,
                ),
                resolvedImageTarget = null,
            ),
            loggingPort = mockk(relaxed = true),
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig(),
                visionConfig = apiVisionConfig(),
            ),
            settingsRepository = mockSettingsRepository(searchEnabled = false),
            searchToolPromptComposer = SearchToolPromptComposer(),
        )

        useCase(
            prompt = "Hello",
            userMessageId = MessageId("1"),
            assistantMessageId = MessageId("2"),
            chatId = ChatId("chat"),
            mode = Mode.FAST,
        ).toList()

        val options = fastService.getSentOptions().single()
        assertTrue(options.toolingEnabled)
        assertFalse(options.availableTools.contains(ToolDefinition.TAVILY_WEB_SEARCH))
        assertTrue(options.availableTools.contains(ToolDefinition.SEARCH_CHAT_HISTORY))
        assertTrue(options.availableTools.contains(ToolDefinition.SEARCH_CHAT))
        assertFalse(options.systemPrompt?.contains("attached_image_inspect") == true)
        assertEquals(listOf(ModelType.FAST), inferenceFactory.resolvedTypes)
    }

    private fun mockActiveModelProvider(
        fastConfig: ActiveModelConfiguration,
        visionConfig: ActiveModelConfiguration? = null,
    ): ActiveModelProviderPort = mockk {
        coEvery { getActiveConfiguration(ModelType.FAST) } returns fastConfig
        coEvery { getActiveConfiguration(ModelType.VISION) } returns visionConfig
    }

    private fun fastTextConfig(): ActiveModelConfiguration = ActiveModelConfiguration(
        id = LocalModelConfigurationId("fast-text"),
        isLocal = true,
        name = "Fast",
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
        visionCapable = false,
    )

    private fun fastVisionConfig(): ActiveModelConfiguration = fastTextConfig().copy(
        id = LocalModelConfigurationId("fast-vision"),
        name = "Fast Vision",
        visionCapable = true,
    )

    private fun apiVisionConfig(): ActiveModelConfiguration = ActiveModelConfiguration(
        id = ApiModelConfigurationId("vision-api"),
        isLocal = false,
        name = "Vision API",
        systemPrompt = "Inspect the image carefully.",
        reasoningEffort = null,
        temperature = 0.2,
        topK = 40,
        topP = 0.95,
        maxTokens = 1024,
        minP = 0.0,
        repetitionPenalty = 1.0,
        contextWindow = 8192,
        thinkingEnabled = false,
        visionCapable = true,
    )

    private fun mockMessageRepository(
        currentMessage: Message = Message(
            id = MessageId("1"),
            chatId = ChatId("1"),
            content = Content(""),
            role = Role.USER,
        ),
        resolvedImageTarget: ResolvedImageTarget? = null,
    ): MessageRepository = mockk {
        coEvery { getMessagesForChat(any()) } returns emptyList()
        coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
        coEvery { saveVisionAnalysis(any(), any(), any(), any(), any()) } returns Unit
        coEvery { resolveLatestImageBearingUserMessage(any(), any()) } returns resolvedImageTarget
        coEvery { getMessageById(any()) } returns currentMessage
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
