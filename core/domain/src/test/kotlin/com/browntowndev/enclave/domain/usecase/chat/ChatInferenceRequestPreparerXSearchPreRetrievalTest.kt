package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.FakeSearchIntentClassifier
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.MediaToolDefinitionFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatInferenceRequestPreparerXSearchPreRetrievalTest {
    private lateinit var activeModelProvider: FakeActiveModelProvider
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var messageRepository: FakeMessageRepository
    private lateinit var memoriesRepository: FakeMemoriesRepository
    private lateinit var embeddingEnginePort: FakeEmbeddingEngine
    private val promptComposer = SearchToolPromptComposer()
    private lateinit var searchIntentClassifier: FakeSearchIntentClassifier
    private lateinit var toolExecutor: FakeToolExecutor

    private val fastConfig =
        ActiveModelConfiguration(
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
        requiresSearch: Boolean = false,
    ) {
        activeModelProvider = FakeActiveModelProvider(configuration = fastConfig)
        settingsRepository =
            FakeSettingsRepository(
                initial =
                    SettingsData(
                        xSearchEnabled = xSearchEnabled,
                        xApiKeyPresent = xApiKeyPresent,
                    ),
            )
        messageRepository =
            FakeMessageRepository(
                initial =
                    listOf(
                        Message(
                            id = MessageId("user"),
                            chatId = ChatId("chat"),
                            content = Content(text = ""),
                        ),
                    ),
            )
        memoriesRepository = FakeMemoriesRepository()
        embeddingEnginePort = FakeEmbeddingEngine()
        searchIntentClassifier =
            FakeSearchIntentClassifier().apply {
                requiresSearchResult = requiresSearch
                probability = if (requiresSearch) 0.9f else 0.1f
            }
        toolExecutor = FakeToolExecutor()
    }

    private fun buildPreparer(): ChatInferenceRequestPreparer =
        ChatInferenceRequestPreparer(
            activeModelProvider = activeModelProvider,
            settingsRepository = settingsRepository,
            messageRepository = messageRepository,
            memoriesRepository = memoriesRepository,
            embeddingEnginePort = embeddingEnginePort,
            promptComposer = promptComposer,
            defaultModelRepository = FakeDefaultModelRepository(),
            mediaProviderRepository = FakeMediaProviderRepository(),
            mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
            getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
            loggingPort = FakeLoggingPort(),
            searchIntentClassifier = searchIntentClassifier,
            toolExecutor = toolExecutor,
        )

    @Test
    fun `XPRE-01 Parseable X status URL triggers deterministic thread retrieval, bypassing classifier`() =
        runTest {
            setupCommonMocks(xSearchEnabled = true, xApiKeyPresent = true, requiresSearch = false)

            val mockThreadResponse = """{"requested_post":{"id":"2058763478853456162","text":"Target post","user_name":"Harshith"},"ancestor_posts":[],"data":[{"id":"2058763478853456162","text":"Target post","user_name":"Harshith"}],"anchor_tweet_id":"2058763478853456162"}"""
            toolExecutor.behavior = { request ->
                ToolExecutionResult(
                    toolName = "x_get_thread",
                    resultJson = mockThreadResponse,
                )
            }

            val preparer = buildPreparer()

            val result =
                preparer(
                    prompt = "Check this thread: https://x.com/HarshithLucky3/status/2058763478853456162?s=20",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            val executed = toolExecutor.executedRequests.single()
            assertEquals("x_get_thread", executed.toolName)
            assertTrue(executed.argumentsJson.contains("https://x.com/HarshithLucky3/status/2058763478853456162?s=20"))
            assertEquals(mockThreadResponse, result.preRetrievalXResultJson)
        }

    @Test
    fun `XPRE-02 Parseable Twitter status URL triggers deterministic thread retrieval, bypassing classifier`() =
        runTest {
            setupCommonMocks(xSearchEnabled = true, xApiKeyPresent = true, requiresSearch = false)

            toolExecutor.behavior = { request ->
                ToolExecutionResult(
                    toolName = "x_get_thread",
                    resultJson = "{}",
                )
            }

            val preparer = buildPreparer()

            preparer(
                prompt = "Check this: https://twitter.com/user/status/987654321",
                chatId = ChatId("chat"),
                userMessageId = MessageId("user"),
                assistantMessageId = MessageId("assistant"),
                modelType = ModelType.FAST,
            )

            val executed = toolExecutor.executedRequests.single()
            assertEquals("x_get_thread", executed.toolName)
            assertTrue(executed.argumentsJson.contains("https://twitter.com/user/status/987654321"))
        }

    @Test
    fun `XPRE-03 Prompt shapes keyword query and triggers recent search pre-retrieval when intent classifier fires`() =
        runTest {
            setupCommonMocks(xSearchEnabled = true, xApiKeyPresent = true, requiresSearch = true)

            val mockSearchResponse = """[{"id":"1","text":"AI News"}]"""
            toolExecutor.behavior = { request ->
                ToolExecutionResult(
                    toolName = "search_x_recent",
                    resultJson = mockSearchResponse,
                )
            }

            val preparer = buildPreparer()

            val result =
                preparer(
                    prompt = "Find latest breaking news on #AI and OpenAI @sama",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            val executed = toolExecutor.executedRequests.single()
            assertEquals("search_x_recent", executed.toolName)
            // We shape keywords from: "Find latest breaking news on #AI and OpenAI @sama"
            // Check if query is shaped correctly
            assertTrue(executed.argumentsJson.contains("news") || executed.argumentsJson.contains("breaking"))
            assertEquals(mockSearchResponse, result.preRetrievalXResultJson)
        }

    @Test
    fun `XPRE-05 X URL is present but X search skill is not configured, skipping pre-retrieval`() =
        runTest {
            setupCommonMocks(xSearchEnabled = false, xApiKeyPresent = true, requiresSearch = false)

            val preparer = buildPreparer()

            val result =
                preparer(
                    prompt = "Check this out: https://x.com/user/status/123",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertTrue(toolExecutor.executedRequests.isEmpty())
            assertNull(result.preRetrievalXResultJson)
        }

    @Test
    fun `XPRE-06 Prompt has search intent but classifier resolves to false, skipping pre-retrieval`() =
        runTest {
            setupCommonMocks(xSearchEnabled = true, xApiKeyPresent = true, requiresSearch = false)

            val preparer = buildPreparer()

            val result =
                preparer(
                    prompt = "Tell me a joke about keyboards",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertTrue(toolExecutor.executedRequests.isEmpty())
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
    fun `XPRE-10 Prompt has non-status X URL, skipping thread fetch and falling back to search`() =
        runTest {
            setupCommonMocks(xSearchEnabled = true, xApiKeyPresent = true, requiresSearch = true)

            toolExecutor.behavior = { request ->
                ToolExecutionResult(
                    toolName = "search_x_recent",
                    resultJson = "[]",
                )
            }

            val preparer = buildPreparer()

            preparer(
                prompt = "Check out the profile https://x.com/OpenAI and search for AI news",
                chatId = ChatId("chat"),
                userMessageId = MessageId("user"),
                assistantMessageId = MessageId("assistant"),
                modelType = ModelType.FAST,
            )

            // Should not execute thread fetch
            assertTrue(toolExecutor.executedRequests.none { it.toolName == "x_get_thread" })

            // Should execute search fallback
            assertEquals(1, toolExecutor.executedRequests.count { it.toolName == "search_x_recent" })
        }
}
