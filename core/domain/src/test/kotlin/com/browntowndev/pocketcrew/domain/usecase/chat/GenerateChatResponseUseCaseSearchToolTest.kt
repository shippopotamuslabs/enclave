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
import com.browntowndev.pocketcrew.domain.port.repository.ExtractedUrlTrackerPort
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

    private val noOpTracker = object : ExtractedUrlTrackerPort {
        override val urls: Set<String> get() = emptySet()
        override fun add(url: String) {}
        override fun clear() {}
    }

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
            extractedUrlTracker = noOpTracker,
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
                        chunk = """