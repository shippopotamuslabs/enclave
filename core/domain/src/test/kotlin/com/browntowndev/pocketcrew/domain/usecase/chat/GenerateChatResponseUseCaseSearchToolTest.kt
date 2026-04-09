package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
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
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 3L,
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
        )

        useCase(
            prompt = "Find recent Android agent news",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 3L,
            mode = Mode.FAST,
        ).toList()

        coVerify {
            chatRepository.persistAllMessageData(
                messageId = 2L,
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
        )

        useCase(
            prompt = "Find recent Android agent news",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 3L,
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
        )

        useCase(
            prompt = "Find recent Android agent news",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 3L,
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
        )

        useCase(
            prompt = "Find recent Android agent news",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 3L,
            mode = Mode.FAST,
        ).toList()

        val options = inferenceService.getSentOptions().single()
        assertEquals("Be concise.", options.systemPrompt)
        assertFalse(options.systemPrompt!!.contains("<tool_call>"))
    }

    private fun mockActiveModelProvider(
        configuration: ActiveModelConfiguration,
    ): ActiveModelProviderPort = mockk {
        coEvery { getActiveConfiguration(ModelType.FAST) } returns configuration
    }

    private fun mockMessageRepository(): MessageRepository = mockk {
        coEvery { getMessagesForChat(any()) } returns emptyList()
        coEvery { getMessageById(any()) } returns Message(
            id = 1L,
            chatId = 1L,
            content = Content(""),
            role = Role.USER,
        )
        coEvery { saveMessage(any()) } returns 1L
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
