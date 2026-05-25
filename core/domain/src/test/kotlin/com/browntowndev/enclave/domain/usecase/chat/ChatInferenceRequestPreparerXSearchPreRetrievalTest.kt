package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.SearchIntentClassifierPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatInferenceRequestPreparerXSearchPreRetrievalTest {

    private val activeModelProvider = mockk<ActiveModelProviderPort>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val messageRepository = mockk<MessageRepository>()
    private val memoriesRepository = mockk<MemoriesRepository>()
    private val embeddingEnginePort = mockk<EmbeddingEnginePort>()
    private val promptComposer = SearchToolPromptComposer()
    private val searchIntentClassifier = mockk<SearchIntentClassifierPort>()
    private val toolExecutor = mockk<ToolExecutorPort>()

    private val fastConfig = ActiveModelConfiguration(
        modelId = "test-model",
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
        isMultimodal = false,
    )

    private fun setupCommonMocks(
        xSearchEnabled: Boolean = true,
        xApiKeyPresent: Boolean = true,
        requiresSearch: Boolean = false
    ) {
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns fastConfig
        every { settingsRepository.settingsFlow } returns flowOf(
            SettingsData(
                xSearchEnabled = xSearchEnabled,
                xApiKeyPresent = xApiKeyPresent
            )
        )
        coEvery { messageRepository.getMessageById(any()) } returns mockk(relaxed = true)
        coEvery { messageRepository.resolveLatestImageBearingUserMessage(any(), any()) } returns null
        coEvery { memoriesRepository.getCoreMemories() } returns emptyList()
        coEvery { memoriesRepository.searchMemories(any(), any(), any(), any()) } returns emptyList()
        coEvery { embeddingEnginePort.getEmbedding(any()) } returns floatArrayOf(0.1f, 0.2f)

        every { searchIntentClassifier.requiresSearch(any(), any()) } returns requiresSearch
        every { searchIntentClassifier.getSearchIntentProbability(any()) } returns if (requiresSearch) 0.9f else 0.1f
    }

    @Test
    fun `XPRE-01 Parseable X status URL triggers deterministic thread retrieval, bypassing classifier`() = runTest {
        setupCommonMocks(xSearchEnabled = true, xApiKeyPresent = true, requiresSearch = false)

        val mockThreadResponse = """{"requested_post":{"id":"2058763478853456162","text":"Target post","user_name":"Harshith"},"ancestor_posts":[],"data":[{"id":"2058763478853456162","text":"Target post","user_name":"Harshith"}],"anchor_tweet_id":"2058763478853456162"}"""
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "x_get_thread",
            resultJson = mockThreadResponse
        )

        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = activeModelProvider,
            settingsRepository = settingsRepository,
            messageRepository = messageRepository,
            memoriesRepository = memoriesRepository,
            embeddingEnginePort = embeddingEnginePort,
            promptComposer = promptComposer,
            defaultModelRepository = mockk(relaxed = true),
            mediaProviderRepository = mockk(relaxed = true),
            mediaToolDefinitionFactory = mockk(relaxed = true),
            getProviderCapabilitiesUseCase = mockk(relaxed = true),
            loggingPort = mockk(relaxed = true),
            searchIntentClassifier = searchIntentClassifier,
            toolExecutor = toolExecutor
        )

        val result = preparer(
            prompt = "Check this thread: https://x.com/HarshithLucky3/status/2058763478853456162?s=20",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST
        )

        coVerify(exactly = 1) {
            toolExecutor.execute(withArg {
                assertEquals("x_get_thread", it.toolName)
                assertTrue(it.argumentsJson.contains("https://x.com/HarshithLucky3/status/2058763478853456162?s=20"))
            })
        }
        assertEquals(mockThreadResponse, result.preRetrievalXResultJson)
    }

    @Test
    fun `XPRE-02 Parseable Twitter status URL triggers deterministic thread retrieval, bypassing classifier`() = runTest {
        setupCommonMocks(xSearchEnabled = true, xApiKeyPresent = true, requiresSearch = false)

        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "x_get_thread",
            resultJson = "{}"
        )

        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = activeModelProvider,
            settingsRepository = settingsRepository,
            messageRepository = messageRepository,
            memoriesRepository = memoriesRepository,
            embeddingEnginePort = embeddingEnginePort,
            promptComposer = promptComposer,
            defaultModelRepository = mockk(relaxed = true),
            mediaProviderRepository = mockk(relaxed = true),
            mediaToolDefinitionFactory = mockk(relaxed = true),
            getProviderCapabilitiesUseCase = mockk(relaxed = true),
            loggingPort = mockk(relaxed = true),
            searchIntentClassifier = searchIntentClassifier,
            toolExecutor = toolExecutor
        )

        preparer(
            prompt = "Check this: https://twitter.com/user/status/987654321",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST
        )

        coVerify(exactly = 1) {
            toolExecutor.execute(withArg {
                assertEquals("x_get_thread", it.toolName)
                assertTrue(it.argumentsJson.contains("https://twitter.com/user/status/987654321"))
            })
        }
    }

    @Test
    fun `XPRE-03 Prompt shapes keyword query and triggers recent search pre-retrieval when intent classifier fires`() = runTest {
        setupCommonMocks(xSearchEnabled = true, xApiKeyPresent = true, requiresSearch = true)

        val mockSearchResponse = """[{"id":"1","text":"AI News"}]"""
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "search_x_recent",
            resultJson = mockSearchResponse
        )

        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = activeModelProvider,
            settingsRepository = settingsRepository,
            messageRepository = messageRepository,
            memoriesRepository = memoriesRepository,
            embeddingEnginePort = embeddingEnginePort,
            promptComposer = promptComposer,
            defaultModelRepository = mockk(relaxed = true),
            mediaProviderRepository = mockk(relaxed = true),
            mediaToolDefinitionFactory = mockk(relaxed = true),
            getProviderCapabilitiesUseCase = mockk(relaxed = true),
            loggingPort = mockk(relaxed = true),
            searchIntentClassifier = searchIntentClassifier,
            toolExecutor = toolExecutor
        )

        val result = preparer(
            prompt = "Find latest breaking news on #AI and OpenAI @sama",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST
        )

        coVerify(exactly = 1) {
            toolExecutor.execute(withArg {
                assertEquals("search_x_recent", it.toolName)
                // We shape keywords from: "Find latest breaking news on #AI and OpenAI @sama"
                // Check if query is shaped correctly
                assertTrue(it.argumentsJson.contains("news") || it.argumentsJson.contains("breaking"))
            })
        }
        assertEquals(mockSearchResponse, result.preRetrievalXResultJson)
    }

    @Test
    fun `XPRE-05 X URL is present but X search skill is not configured, skipping pre-retrieval`() = runTest {
        setupCommonMocks(xSearchEnabled = false, xApiKeyPresent = true, requiresSearch = false)

        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = activeModelProvider,
            settingsRepository = settingsRepository,
            messageRepository = messageRepository,
            memoriesRepository = memoriesRepository,
            embeddingEnginePort = embeddingEnginePort,
            promptComposer = promptComposer,
            defaultModelRepository = mockk(relaxed = true),
            mediaProviderRepository = mockk(relaxed = true),
            mediaToolDefinitionFactory = mockk(relaxed = true),
            getProviderCapabilitiesUseCase = mockk(relaxed = true),
            loggingPort = mockk(relaxed = true),
            searchIntentClassifier = searchIntentClassifier,
            toolExecutor = toolExecutor
        )

        val result = preparer(
            prompt = "Check this out: https://x.com/user/status/123",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST
        )

        coVerify(exactly = 0) { toolExecutor.execute(any()) }
        assertNull(result.preRetrievalXResultJson)
    }

    @Test
    fun `XPRE-06 Prompt has search intent but classifier resolves to false, skipping pre-retrieval`() = runTest {
        setupCommonMocks(xSearchEnabled = true, xApiKeyPresent = true, requiresSearch = false)

        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = activeModelProvider,
            settingsRepository = settingsRepository,
            messageRepository = messageRepository,
            memoriesRepository = memoriesRepository,
            embeddingEnginePort = embeddingEnginePort,
            promptComposer = promptComposer,
            defaultModelRepository = mockk(relaxed = true),
            mediaProviderRepository = mockk(relaxed = true),
            mediaToolDefinitionFactory = mockk(relaxed = true),
            getProviderCapabilitiesUseCase = mockk(relaxed = true),
            loggingPort = mockk(relaxed = true),
            searchIntentClassifier = searchIntentClassifier,
            toolExecutor = toolExecutor
        )

        val result = preparer(
            prompt = "Tell me a joke about keyboards",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST
        )

        coVerify(exactly = 0) { toolExecutor.execute(any()) }
        assertNull(result.preRetrievalXResultJson)
    }

    @Test
    fun `XPRE-07 X status URL containing query parameters extracts the exact tweet ID`() {
        val prompt = "https://x.com/HarshithLucky3/status/2058763478853456162?s=20&t=abcdef"
        val extracted = ChatInferenceRequestPreparer.parseTweetIdFromUrl(prompt)
        assertEquals("2058763478853456162", extracted)
    }

    @Test
    fun `XPRE-08 X status URL with trailing slash extracts the exact tweet ID`() {
        val prompt = "https://twitter.com/some_user/status/1122334455/"
        val extracted = ChatInferenceRequestPreparer.parseTweetIdFromUrl(prompt)
        assertEquals("1122334455", extracted)
    }

    @Test
    fun `XPRE-10 Prompt has non-status X URL, skipping thread fetch and falling back to search`() = runTest {
        setupCommonMocks(xSearchEnabled = true, xApiKeyPresent = true, requiresSearch = true)

        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "search_x_recent",
            resultJson = "[]"
        )

        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = activeModelProvider,
            settingsRepository = settingsRepository,
            messageRepository = messageRepository,
            memoriesRepository = memoriesRepository,
            embeddingEnginePort = embeddingEnginePort,
            promptComposer = promptComposer,
            defaultModelRepository = mockk(relaxed = true),
            mediaProviderRepository = mockk(relaxed = true),
            mediaToolDefinitionFactory = mockk(relaxed = true),
            getProviderCapabilitiesUseCase = mockk(relaxed = true),
            loggingPort = mockk(relaxed = true),
            searchIntentClassifier = searchIntentClassifier,
            toolExecutor = toolExecutor
        )

        preparer(
            prompt = "Check out the profile https://x.com/OpenAI and search for AI news",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST
        )

        // Should not execute thread fetch
        coVerify(exactly = 0) {
            toolExecutor.execute(withArg {
                assertEquals("x_get_thread", it.toolName)
            })
        }

        // Should execute search fallback
        coVerify(exactly = 1) {
            toolExecutor.execute(withArg {
                assertEquals("search_x_recent", it.toolName)
            })
        }
    }
}
