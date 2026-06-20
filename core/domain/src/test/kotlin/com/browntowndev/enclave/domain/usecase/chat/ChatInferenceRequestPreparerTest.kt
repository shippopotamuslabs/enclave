package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.ResolvedImageTarget
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.model.memory.MemoryRetrievalPolicy
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
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatInferenceRequestPreparerTest {
    @Test
    fun `LTD-01 request preparer seeds catalog and active tools separately`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        mockSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "Build a document from recent sources",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertAll(
                {
                    assertEquals(
                        listOf(
                            "tool_discovery_tool",
                            "read_context_reference",
                            "web_search",
                            "web_extract",
                            "search_chat_history",
                            "search_chat",
                            "get_message_context",
                            "manage_memories",
                            "start_artifact",
                            "append_blocks",
                            "search_current_artifact",
                        ),
                        request.options.toolCatalog.map { it.name },
                    )
                },
                {
                    assertEquals(
                        listOf("tool_discovery_tool", "read_context_reference", "web_search", "web_extract"),
                        request.options.activeTools.map { it.name },
                    )
                },
                {
                    assertEquals(
                        "DISCLOSE_ON_DEMAND",
                        request
                            .options
                            .toolCatalog
                            .single { it.name == "append_blocks" }
                            .disclosureMode
                            .name,
                    )
                },
                {
                    assertEquals(
                        "PRELOADED",
                        request
                            .options
                            .toolCatalog
                            .single { it.name == "web_search" }
                            .disclosureMode
                            .name,
                    )
                },
            )
        }

    @Test
    fun `multimodal local model sends image directly without tool contract`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = fastVisionConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = mockSettingsRepository(SettingsData()),
                    messageRepository =
                        mockMessageRepository(
                            resolvedImageTarget =
                                ResolvedImageTarget(
                                    userMessageId = MessageId("user"),
                                    imageUris = listOf("file:///tmp/direct.jpg"),
                                ),
                        ),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "What is in the image?",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertEquals(listOf("file:///tmp/direct.jpg"), request.options.imageUris)
            val catalogNames = request.options.toolCatalog.map { it.name }
            assertFalse(catalogNames.contains(ToolDefinition.WEB_SEARCH.name))
            assertFalse(catalogNames.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT.name))
            assertTrue(catalogNames.contains(ToolDefinition.SEARCH_CHAT_HISTORY.name))
            assertTrue(catalogNames.contains(ToolDefinition.SEARCH_CHAT.name))
            assertTrue(catalogNames.contains(ToolDefinition.MANAGE_MEMORIES.name))
            assertTrue(request.options.toolingEnabled)
            assertTrue(request.prompt.contains("The user attached one or more images."))
        }

    @Test
    fun `local text model composes both search and image tool contracts when eligible`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = apiVisionConfig(),
                        ),
                    settingsRepository =
                        mockSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository =
                        mockMessageRepository(
                            resolvedImageTarget =
                                ResolvedImageTarget(
                                    userMessageId = MessageId("user"),
                                    imageUris = listOf("file:///tmp/tool.jpg"),
                                ),
                        ),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "Find similar defects and inspect the image",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertTrue(request.options.toolingEnabled)
            assertTrue(request.options.imageUris.isEmpty())
            assertTrue(request.options.activeTools.contains(ToolDefinition.WEB_SEARCH))
            assertTrue(request.options.activeTools.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT))
            assertTrue(request.options.systemPrompt?.contains("Be concise.") == true)
            assertFalse(request.options.systemPrompt?.contains("# ACTIVE TOOL EXAMPLES:") == true)
            assertTrue(request.prompt.contains("The user attached one or more images."))
        }

    @Test
    fun `search enabled includes both WEB_SEARCH and WEB_EXTRACT in activeTools`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        mockSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "What is Android 17?",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertTrue(request.options.activeTools.contains(ToolDefinition.WEB_SEARCH))
            assertTrue(request.options.activeTools.contains(ToolDefinition.WEB_EXTRACT))
        }

    @Test
    fun `LiteRT model triggers LITE_RT_NATIVE strategy`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig =
                                fastTextConfig().copy(
                                    localModelFormat = com.browntowndev.enclave.domain.model.inference.ModelFileFormat.LITERTLM,
                                ),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        mockSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "Search for LiteRT",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertTrue(request.options.systemPrompt?.contains("call:tool_discovery_tool") == true)
            assertFalse(request.options.systemPrompt?.contains("# ACTIVE TOOL EXAMPLES:") == true)
        }

    @Test
    fun `API model triggers SDK_NATIVE strategy`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = apiVisionConfig(), // API model
                            visionConfig = null,
                        ),
                    settingsRepository =
                        mockSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "Search with API",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            // SDK_NATIVE: request tools[] is authoritative; no sidecar routing or catalog enumeration
            val systemPrompt = requireNotNull(request.options.systemPrompt)
            assertTrue(systemPrompt.contains("# TOOL ROUTING:"))
            assertTrue(systemPrompt.contains("request tools parameter"))
            assertFalse(systemPrompt.contains("# TOOL GROUPS:"))
            assertFalse(systemPrompt.contains("<tool_call>"))
            assertFalse(systemPrompt.contains("registered tools"))
            assertTrue(request.options.activeTools.contains(ToolDefinition.WEB_SEARCH))
        }

    @Test
    fun `GGUF model triggers JSON_XML_ENVELOPE strategy`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig =
                                fastTextConfig().copy(
                                    localModelFormat = com.browntowndev.enclave.domain.model.inference.ModelFileFormat.GGUF,
                                ),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        mockSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "Search for GGUF",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertTrue(request.options.systemPrompt?.contains("<tool_call>") == true)
            assertFalse(request.options.systemPrompt?.contains("ActionResult") == true)
            assertTrue(request.options.systemPrompt?.contains("web_search") == true)
        }

    @Test
    fun `disabling search removes tools and instructions`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = false)),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "Just chat",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            val catalogNames = request.options.toolCatalog.map { it.name }
            assertFalse(catalogNames.contains(ToolDefinition.WEB_SEARCH.name))
            assertTrue(catalogNames.contains(ToolDefinition.SEARCH_CHAT_HISTORY.name))
            assertTrue(catalogNames.contains(ToolDefinition.SEARCH_CHAT.name))
            assertTrue(catalogNames.contains(ToolDefinition.GET_MESSAGE_CONTEXT.name))
            assertTrue(request.options.toolingEnabled)
            assertFalse(request.options.activeTools.any { it.name == ToolDefinition.WEB_SEARCH.name })
            assertFalse(request.options.systemPrompt?.contains("# CONVERSATION MEMORY TOOLS") == true)
        }

    @Test
    fun `GET_MESSAGE_CONTEXT is always included in toolCatalog`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = false)),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "Hello",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertTrue(
                request.options.toolCatalog
                    .map { it.name }
                    .contains(ToolDefinition.GET_MESSAGE_CONTEXT.name),
            )
        }

    @Test
    fun `injects core and retrieved memories into system prompt`() =
        runTest {
            val coreMemories =
                listOf(
                    Memory(category = MemoryCategory.CORE_IDENTITY, content = "User is a developer."),
                    Memory(category = MemoryCategory.PREFERENCES, content = "User likes dark mode."),
                )
            val retrievedMemories =
                listOf(
                    Memory(category = MemoryCategory.FACTS, content = "User has a cat."),
                )
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = false)),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository =
                        mockk {
                            coEvery { getCoreMemories() } returns coreMemories
                            coEvery {
                                searchMemories(
                                    any(),
                                    MemoryRetrievalPolicy.CHAT_RETRIEVAL_LIMIT,
                                    MemoryRetrievalPolicy.MAX_L2_DISTANCE,
                                    MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES,
                                )
                            } returns retrievedMemories
                        },
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "Tell me about myself",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertTrue(request.options.systemPrompt?.contains("User is a developer.") == true)
            assertTrue(request.options.systemPrompt?.contains("User likes dark mode.") == true)
            assertTrue(request.options.systemPrompt?.contains("# ABOUT THE USER:") == true)
            assertFalse(request.options.systemPrompt?.contains("# RELEVANT FACTS & CONTEXT:") == true)
            assertTrue(request.options.volatileTurnContext?.contains("User has a cat.") == true)
            assertTrue(request.options.volatileTurnContext?.contains("# RELEVANT FACTS & CONTEXT:") == true)
        }

    @Test
    fun `local small context window uses compact static system prompt`() =
        runTest {
            val coreMemories =
                listOf(
                    com.browntowndev.enclave.domain.model.memory.Memory(
                        category = com.browntowndev.enclave.domain.model.memory.MemoryCategory.PSYCH_PROFILE,
                        content = "Avoids vulnerability.",
                    ),
                )
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = true)),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository =
                        mockk {
                            coEvery { getCoreMemories() } returns coreMemories
                            coEvery {
                                searchMemories(
                                    any(),
                                    MemoryRetrievalPolicy.CHAT_RETRIEVAL_LIMIT,
                                    MemoryRetrievalPolicy.MAX_L2_DISTANCE,
                                    MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES,
                                )
                            } returns emptyList()
                        },
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "hello",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertFalse(request.options.systemPrompt?.contains("PSYCHOLOGICAL PROFILE") == true)
            assertFalse(request.options.systemPrompt?.contains("Avoids vulnerability.") == true)
            assertFalse(request.options.systemPrompt?.contains("# ACTIVE TOOL EXAMPLES:") == true)
            assertFalse(request.options.systemPrompt?.contains("# CONVERSATION MEMORY TOOLS") == true)
            assertTrue(request.options.systemPrompt?.contains("# TOOL DISCOVERY:") == true)
        }

    @Test
    fun `blank image prompt adds fallback instruction`() {
        val prompt =
            prepareChatPrompt(
                prompt = "   ",
                hasImageContext = true,
                imageHandling = ChatImageHandling.DIRECT,
            )

        assertTrue(prompt.contains("Respond helpfully based on the context"))
        assertTrue(prompt.contains("The user attached one or more images."))
    }

    private fun mockActiveModelProvider(
        fastConfig: ActiveModelConfiguration,
        visionConfig: ActiveModelConfiguration?,
    ): ActiveModelProviderPort =
        mockk {
            coEvery { getActiveConfiguration(ModelType.FAST) } returns fastConfig
            coEvery { getActiveConfiguration(ModelType.VISION) } returns visionConfig
        }

    private fun mockSettingsRepository(settings: SettingsData): SettingsRepository =
        mockk {
            every { settingsFlow } returns flowOf(settings)
        }

    private fun mockMessageRepository(resolvedImageTarget: ResolvedImageTarget?): MessageRepository =
        mockk {
            coEvery { getMessageById(any()) } returns mockk(relaxed = true)
            coEvery { resolveLatestImageBearingUserMessage(any(), any()) } returns resolvedImageTarget
        }

    private fun mockMemoriesRepository(): MemoriesRepository =
        mockk {
            coEvery { getCoreMemories() } returns emptyList()
            coEvery { searchMemories(any(), any(), any(), any()) } returns emptyList()
        }

    private fun mockEmbeddingEngine(): EmbeddingEnginePort =
        mockk {
            coEvery { getEmbedding(any()) } returns floatArrayOf(0.1f, 0.2f)
        }

    private fun fastTextConfig(): ActiveModelConfiguration =
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

    private fun fastVisionConfig(): ActiveModelConfiguration =
        fastTextConfig().copy(
            id = LocalModelConfigurationId("fast-vision"),
            name = "Fast Vision",
            isMultimodal = true,
        )

    private fun apiVisionConfig(): ActiveModelConfiguration =
        ActiveModelConfiguration(
            modelId = "test-model",
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
            isMultimodal = true,
        )

    private fun litertThinkingConfig(): ActiveModelConfiguration =
        fastTextConfig().copy(
            id = LocalModelConfigurationId("litert-thinking"),
            name = "LiteRT Thinking",
            thinkingEnabled = true,
            localModelFormat = ModelFileFormat.LITERTLM,
        )

    private fun ggufThinkingConfig(): ActiveModelConfiguration =
        fastTextConfig().copy(
            id = LocalModelConfigurationId("gguf-thinking"),
            name = "GGUF Thinking",
            thinkingEnabled = true,
            reasoningBudget = 1536,
            localModelFormat = ModelFileFormat.GGUF,
        )

    @Test
    fun `LiteRT local model with thinkingEnabled true derives reasoningBudget 0`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = litertThinkingConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = false)),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "Think step by step",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertEquals(0, request.options.reasoningBudget)
        }

    @Test
    fun `GGUF local model with thinkingEnabled true uses configured reasoningBudget`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = ggufThinkingConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = false)),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "Reason carefully",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertEquals(1536, request.options.reasoningBudget)
        }

    @Test
    fun `local model with thinkingEnabled false derives reasoningBudget 0`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = fastTextConfig(), // thinkingEnabled = false
                            visionConfig = null,
                        ),
                    settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = false)),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "Quick answer",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertEquals(0, request.options.reasoningBudget)
        }

    @Test
    fun `when search classifier returns true then pre-retrieval search is executed and injected`() =
        runTest {
            val mockClassifier = mockk<SearchIntentClassifierPort>()
            val mockExecutor = mockk<ToolExecutorPort>()

            every { mockClassifier.getSearchIntentProbability(any()) } returns 0.9f
            every { mockClassifier.requiresSearch(any(), any()) } returns true
            coEvery { mockExecutor.execute(any()) } returns
                com.browntowndev.enclave.domain.model.inference.ToolExecutionResult(
                    toolName = ToolDefinition.WEB_SEARCH.name,
                    resultJson =
                        """{"results":[{"title":"Search Title","content":"${"Search result content ".repeat(2_000)}"}]}""",
                )

            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        mockSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                    searchIntentClassifier = mockClassifier,
                    toolExecutor = mockExecutor,
                )

            val request =
                preparer(
                    prompt = "Tell me the news",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            val systemPrompt = request.options.systemPrompt
            val volatileTurnContext = requireNotNull(request.options.volatileTurnContext)
            assertTrue(systemPrompt == null || !systemPrompt.contains("RELEVANT WEB SEARCH RESULTS"))
            assertTrue(volatileTurnContext.contains("RELEVANT WEB SEARCH RESULTS"))
            assertTrue(volatileTurnContext.length <= 2_048)
            assertTrue(volatileTurnContext.endsWith("[Context truncated to fit local model window]"))
            assertTrue(request.prompt.startsWith("Tell me the news"))
        }

    @Test
    fun `when heuristic marks non-search then pre-retrieval search is skipped even if classifier is positive`() =
        runTest {
            val mockClassifier = mockk<SearchIntentClassifierPort>()
            val mockExecutor = mockk<ToolExecutorPort>()

            every { mockClassifier.getSearchIntentProbability(any()) } returns 0.95f
            every { mockClassifier.requiresSearch(any(), any()) } returns true

            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        mockSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                    searchIntentClassifier = mockClassifier,
                    toolExecutor = mockExecutor,
                )

            val request =
                preparer(
                    prompt = "I want this as an artifact that I can export",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            val systemPrompt = request.options.systemPrompt
            assertTrue(systemPrompt == null || !systemPrompt.contains("RELEVANT WEB SEARCH RESULTS"))
            coVerify(exactly = 0) { mockExecutor.execute(any()) }
            verify(exactly = 0) { mockClassifier.getSearchIntentProbability(any()) }
        }

    @Test
    fun `SCENARIO_PREPARER_ELIGIBLE_TOOLS`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        mockActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        mockSettingsRepository(
                            SettingsData(xSearchEnabled = true, xApiKeyPresent = true),
                        ),
                    messageRepository = mockMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = mockMemoriesRepository(),
                    embeddingEnginePort = mockEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = mockk(relaxed = true),
                    mediaProviderRepository = mockk(relaxed = true),
                    mediaToolDefinitionFactory = mockk(relaxed = true),
                    getProviderCapabilitiesUseCase = mockk(relaxed = true),
                    loggingPort = mockk(relaxed = true),
                )

            val request =
                preparer(
                    prompt = "Hello",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            val catalogNames = request.options.toolCatalog.map { it.name }
            val activeNames = request.options.activeTools.map { it.name }

            assertTrue(catalogNames.contains(ToolDefinition.X_GET_THREAD.name))
            assertTrue(catalogNames.contains(ToolDefinition.X_TRENDING.name))
            assertTrue(catalogNames.contains(ToolDefinition.X_USER_TWEETS.name))
            assertTrue(catalogNames.contains(ToolDefinition.X_MENTION_SEARCH.name))

            assertFalse(activeNames.contains(ToolDefinition.X_GET_THREAD.name))
            assertFalse(activeNames.contains(ToolDefinition.X_TRENDING.name))
            assertFalse(activeNames.contains(ToolDefinition.X_USER_TWEETS.name))
            assertFalse(activeNames.contains(ToolDefinition.X_MENTION_SEARCH.name))
        }
}
