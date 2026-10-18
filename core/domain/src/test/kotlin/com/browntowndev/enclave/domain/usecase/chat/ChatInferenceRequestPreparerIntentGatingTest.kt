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
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.inference.SearchIntentClassifierPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.MediaToolDefinitionFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatInferenceRequestPreparerIntentGatingTest {
    @Test
    fun `IGTA-02 search_x_recent preloaded when X search configured`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    probability = 0.9f
                    requiresSearchResult = true
                }

            val request =
                preparer(classifier = classifier)
                    .invoke(
                        prompt = "What are the latest headlines about the election?",
                        chatId = ChatId("chat"),
                        userMessageId = MessageId("user"),
                        assistantMessageId = MessageId("assistant"),
                        modelType = ModelType.FAST,
                    )

            // search_x_recent is preloaded on par with web_search whenever X search is configured,
            // so it is always active (and PRELOADED in the catalog) rather than intent-activated.
            // This keeps the wire tools[] block stable from turn 1 for prompt-cache reuse.
            assertTrue(request.options.activeTools.any { it.name == "search_x_recent" })
            assertEquals(
                ToolDisclosureMode.PRELOADED,
                request.options.toolCatalog
                    .single { it.name == "search_x_recent" }
                    .disclosureMode,
            )
        }

    @Test
    fun `IGTA-03 X search disabled prevents search_x_recent activation`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    probability = 0.9f
                    requiresSearchResult = true
                }

            val request =
                preparer(
                    classifier = classifier,
                    settings =
                        configuredWebAndX().copy(
                            xSearchEnabled = false,
                            xApiKeyPresent = false,
                        ),
                ).invoke(
                    prompt = "Latest breaking news on X",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertFalse(request.options.activeTools.any { it.name == "search_x_recent" })
        }

    @Test
    fun `IGTA-04 artifact intent activates trio on export phrasing without search`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    requiresSearchResult = true
                }

            val request =
                preparer(classifier = classifier)
                    .invoke(
                        prompt = "Export this conversation as a markdown artifact",
                        chatId = ChatId("chat"),
                        userMessageId = MessageId("user"),
                        assistantMessageId = MessageId("assistant"),
                        modelType = ModelType.FAST,
                    )

            val activeNames = request.options.activeTools.map { it.name }
            assertTrue(activeNames.contains("start_artifact"))
            assertTrue(activeNames.contains("append_blocks"))
            assertTrue(activeNames.contains("search_current_artifact"))
            // search_x_recent is preloaded (X configured), so it is present independent of the
            // artifact-only intent path. The point of this test — artifact trio active, no proactive
            // web search results — is carried by the prompt/volatile-context assertions below.
            assertTrue(activeNames.contains("search_x_recent"))
            assertTrue(
                request.options.systemPrompt == null ||
                    !request.options.systemPrompt!!.contains("# RELEVANT WEB SEARCH RESULTS:"),
            )
            assertTrue(request.options.volatileTurnContext.isNullOrBlank())
        }

    @Test
    fun `IGTA-05 combined prompt activates artifact and search tools with web pre-retrieval`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    probability = 0.9f
                    requiresSearchResult = true
                }
            val executor =
                FakeToolExecutor().apply {
                    resultJson = """{"results":[{"title":"T","content":"Fixture search body"}]}"""
                }

            val request =
                preparer(classifier = classifier, toolExecutor = executor)
                    .invoke(
                        prompt = "Research current mortgage rates and write a PDF report",
                        chatId = ChatId("chat"),
                        userMessageId = MessageId("user"),
                        assistantMessageId = MessageId("assistant"),
                        modelType = ModelType.FAST,
                    )

            val activeNames = request.options.activeTools.map { it.name }
            assertTrue(activeNames.contains("start_artifact"))
            assertTrue(activeNames.contains("search_x_recent"))
            assertFalse(request.options.systemPrompt!!.contains("# RELEVANT WEB SEARCH RESULTS:"))
            assertTrue(request.options.volatileTurnContext!!.contains("# RELEVANT WEB SEARCH RESULTS:"))
            assertTrue(request.options.volatileTurnContext!!.contains("Fixture search body"))
        }

    @Test
    fun `IGTA-06 heuristic non-search artifact prompt skips web pre-retrieval`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    requiresSearchResult = true
                }
            val executor = FakeToolExecutor()

            val request =
                preparer(classifier = classifier, toolExecutor = executor)
                    .invoke(
                        prompt = "I want this as an artifact that I can export",
                        chatId = ChatId("chat"),
                        userMessageId = MessageId("user"),
                        assistantMessageId = MessageId("assistant"),
                        modelType = ModelType.FAST,
                    )

            assertTrue(request.options.activeTools.any { it.name == "start_artifact" })
            assertTrue(executor.executedRequests.isEmpty())
            assertEquals(0, classifier.probabilityCallCount)
        }

    @Test
    fun `IGTA-07 search_x_recent preloaded even when classifier probability is low`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    probability = 0.1f
                    requiresSearchResult = false
                }

            val request =
                preparer(classifier = classifier)
                    .invoke(
                        prompt = "Tell me today's weather in Boston",
                        chatId = ChatId("chat"),
                        userMessageId = MessageId("user"),
                        assistantMessageId = MessageId("assistant"),
                        modelType = ModelType.FAST,
                    )

            // X exposure is no longer gated on the search-intent classifier: search_x_recent is
            // preloaded whenever X search is configured, so it stays in the active set even when the
            // classifier says no search is needed. (Proactive X pre-retrieval is still intent-gated.)
            assertTrue(request.options.activeTools.any { it.name == "search_x_recent" })
        }

    @Test
    fun `IGTA-13 search_x_recent preloaded on its own toggle independent of web searchEnabled`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    requiresSearchResult = true
                }

            val request =
                preparer(
                    classifier = classifier,
                    settings =
                        SettingsData(
                            searchEnabled = false,
                            tavilyKeyPresent = true,
                            xSearchEnabled = true,
                            xApiKeyPresent = true,
                        ),
                ).invoke(
                    prompt = "Trending topics on X",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            // X search is configured by its own toggle + key (isXSearchSkillConfigured), independent
            // of the web searchEnabled master toggle. search_x_recent is therefore preloaded, while
            // web tools stay absent because web search is not configured.
            assertTrue(request.options.activeTools.any { it.name == "search_x_recent" })
            assertFalse(request.options.toolCatalog.any { it.name == "web_search" })
        }

    @Test
    fun `IGTA-16 missing Tavily key hides web tools and skips classifier`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    requiresSearchResult = true
                }

            val request =
                preparer(
                    classifier = classifier,
                    settings =
                        SettingsData(
                            searchEnabled = true,
                            searchProvider = SearchProvider.TAVILY,
                            tavilyKeyPresent = false,
                        ),
                ).invoke(
                    prompt = "What are today's mortgage rates?",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            val catalogNames = request.options.toolCatalog.map { it.name }
            val activeNames = request.options.activeTools.map { it.name }
            assertFalse(catalogNames.contains("web_search"))
            assertFalse(catalogNames.contains("web_extract"))
            assertFalse(activeNames.contains("web_search"))
            assertEquals(0, classifier.probabilityCallCount)
            assertTrue(
                request.options.systemPrompt == null ||
                    !request.options.systemPrompt!!.contains("# RELEVANT WEB SEARCH RESULTS:"),
            )
        }

    @Test
    fun `IGTA-17 missing X API key omits search_x_recent from catalog`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    probability = 0.9f
                    requiresSearchResult = true
                }

            val request =
                preparer(
                    classifier = classifier,
                    settings =
                        configuredWebAndX().copy(xApiKeyPresent = false),
                ).invoke(
                    prompt = "What is trending on X right now?",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertFalse(request.options.toolCatalog.any { it.name == "search_x_recent" })
            assertFalse(request.options.activeTools.any { it.name == "search_x_recent" })
        }

    @Test
    fun `IGTA-18 web-only configuration runs classifier but never activates X`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    probability = 0.9f
                    requiresSearchResult = true
                }
            val executor =
                FakeToolExecutor().apply {
                    resultJson = """{"results":[{"title":"T","content":"Fixture search body"}]}"""
                }

            val request =
                preparer(
                    classifier = classifier,
                    toolExecutor = executor,
                    settings =
                        SettingsData(
                            searchEnabled = true,
                            tavilyKeyPresent = true,
                            xSearchEnabled = false,
                            xApiKeyPresent = false,
                        ),
                ).invoke(
                    prompt = "Latest headlines about the election",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertTrue(request.options.activeTools.any { it.name == "web_search" })
            assertFalse(request.options.activeTools.any { it.name == "search_x_recent" })
            assertFalse(request.options.toolCatalog.any { it.name == "search_x_recent" })
            assertFalse(request.options.systemPrompt!!.contains("# RELEVANT WEB SEARCH RESULTS:"))
            assertTrue(request.options.volatileTurnContext!!.contains("# RELEVANT WEB SEARCH RESULTS:"))
        }

    @Test
    fun `MUT-01 search_x_recent preloaded into active set when X configured regardless of search intent`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    requiresSearchResult = false
                }

            val request =
                preparer(classifier = classifier)
                    .invoke(
                        prompt = "Summarize this paragraph for me",
                        chatId = ChatId("chat"),
                        userMessageId = MessageId("user"),
                        assistantMessageId = MessageId("assistant"),
                        modelType = ModelType.FAST,
                    )

            // search_x_recent is preloaded last (eligibleTools orders X tools after web/artifact),
            // present even though the classifier reports no search intent.
            assertEquals(
                listOf("tool_discovery_tool", "read_context_reference", "web_search", "web_extract", "search_x_recent"),
                request.options.activeTools.map { it.name },
            )
        }

    @Test
    fun `MUT-02 artifact tools active while search path blocked`() =
        runTest {
            val classifier =
                FakeSearchIntentClassifier().apply {
                    requiresSearchResult = false
                }

            val request =
                preparer(classifier = classifier)
                    .invoke(
                        prompt = "Draft a PDF summary of our meeting notes",
                        chatId = ChatId("chat"),
                        userMessageId = MessageId("user"),
                        assistantMessageId = MessageId("assistant"),
                        modelType = ModelType.FAST,
                    )

            val activeNames = request.options.activeTools.map { it.name }
            assertTrue(activeNames.contains("start_artifact"))
            assertTrue(activeNames.contains("append_blocks"))
            assertTrue(activeNames.contains("search_current_artifact"))
            // search_x_recent is preloaded (X configured); the "search path blocked" guarantee this
            // test cares about is the absence of proactive web search results, asserted below.
            assertTrue(activeNames.contains("search_x_recent"))
            assertTrue(
                request.options.systemPrompt == null ||
                    !request.options.systemPrompt!!.contains("# RELEVANT WEB SEARCH RESULTS:"),
            )
        }

    @Test
    fun `IGTA-15 preparer sets activeTools for local LiteRT model when artifact intent fires`() =
        runTest {
            val litertConfig =
                ActiveModelConfiguration(
                    modelId = "test",
                    id = LocalModelConfigurationId("test"),
                    isLocal = true,
                    name = "Test",
                    systemPrompt = "Be concise.",
                    temperature = 0.7,
                    topK = 40,
                    topP = 0.9,
                    maxTokens = 512,
                    contextWindow = 4096,
                    thinkingEnabled = false,
                    isMultimodal = false,
                    minP = 0.05,
                    repetitionPenalty = 1.1,
                    localModelFormat = ModelFileFormat.LITERTLM,
                )

            val request =
                preparer(
                    fastConfig = litertConfig,
                ).invoke(
                    prompt = "Generate a markdown document summarizing this chat",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertEquals(ToolCallStrategy.LITE_RT_NATIVE, request.options.toolCallStrategy)
            assertTrue(request.options.activeTools.any { it.name == "start_artifact" })
        }

    private fun configuredWebAndX(): SettingsData =
        SettingsData(
            searchEnabled = true,
            tavilyKeyPresent = true,
            xSearchEnabled = true,
            xApiKeyPresent = true,
        )

    private fun preparer(
        classifier: SearchIntentClassifierPort = FakeSearchIntentClassifier(),
        toolExecutor: ToolExecutorPort = FakeToolExecutor(),
        settings: SettingsData = configuredWebAndX(),
        fastConfig: ActiveModelConfiguration = fastTextConfig(),
    ): ChatInferenceRequestPreparer =
        ChatInferenceRequestPreparer(
            activeModelProvider =
                FakeActiveModelProvider(configuration = fastConfig).apply {
                    configurationsByType[ModelType.VISION] = null
                },
            settingsRepository = FakeSettingsRepository(initial = settings),
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
                ),
            memoriesRepository = FakeMemoriesRepository(),
            embeddingEnginePort = FakeEmbeddingEngine(),
            promptComposer = SearchToolPromptComposer(),
            defaultModelRepository = FakeDefaultModelRepository(),
            mediaProviderRepository = FakeMediaProviderRepository(),
            mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
            getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
            loggingPort = FakeLoggingPort(),
            searchIntentClassifier = classifier,
            toolExecutor = toolExecutor,
        )

    private fun fastTextConfig(): ActiveModelConfiguration =
        ActiveModelConfiguration(
            modelId = "test",
            id = LocalModelConfigurationId("test"),
            isLocal = true,
            name = "Test",
            systemPrompt = "Be concise.",
            temperature = 0.7,
            topK = 40,
            topP = 0.9,
            maxTokens = 512,
            contextWindow = 4096,
            thinkingEnabled = false,
            isMultimodal = false,
            minP = 0.05,
            repetitionPenalty = 1.1,
            localModelFormat = ModelFileFormat.GGUF,
        )
}
