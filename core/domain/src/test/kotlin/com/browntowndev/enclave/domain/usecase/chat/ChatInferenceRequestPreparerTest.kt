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
import com.browntowndev.enclave.domain.model.chat.ResolvedImageTarget
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.MediaToolDefinitionFactory
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
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        fakeSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
                        fakeActiveModelProvider(
                            fastConfig = fastVisionConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = fakeSettingsRepository(SettingsData()),
                    messageRepository =
                        fakeMessageRepository(
                            resolvedImageTarget =
                                ResolvedImageTarget(
                                    userMessageId = MessageId("user"),
                                    imageUris = listOf("file:///tmp/direct.jpg"),
                                ),
                        ),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = apiVisionConfig(),
                        ),
                    settingsRepository =
                        fakeSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository =
                        fakeMessageRepository(
                            resolvedImageTarget =
                                ResolvedImageTarget(
                                    userMessageId = MessageId("user"),
                                    imageUris = listOf("file:///tmp/tool.jpg"),
                                ),
                        ),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        fakeSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
                        fakeActiveModelProvider(
                            fastConfig =
                                fastTextConfig().copy(
                                    localModelFormat = com.browntowndev.enclave.domain.model.inference.ModelFileFormat.LITERTLM,
                                ),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        fakeSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
                        fakeActiveModelProvider(
                            fastConfig = apiVisionConfig(), // API model
                            visionConfig = null,
                        ),
                    settingsRepository =
                        fakeSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
                        fakeActiveModelProvider(
                            fastConfig =
                                fastTextConfig().copy(
                                    localModelFormat = com.browntowndev.enclave.domain.model.inference.ModelFileFormat.GGUF,
                                ),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        fakeSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = fakeSettingsRepository(SettingsData(searchEnabled = false)),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = fakeSettingsRepository(SettingsData(searchEnabled = false)),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = fakeSettingsRepository(SettingsData(searchEnabled = false)),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository =
                        FakeMemoriesRepository().apply {
                            seed(coreMemories + retrievedMemories)
                        },
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = fakeSettingsRepository(SettingsData(searchEnabled = true)),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository =
                        FakeMemoriesRepository().apply {
                            seed(coreMemories)
                        },
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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

    private fun fakeActiveModelProvider(
        fastConfig: ActiveModelConfiguration,
        visionConfig: ActiveModelConfiguration?,
    ): FakeActiveModelProvider =
        FakeActiveModelProvider(configuration = fastConfig).apply {
            configurationsByType[ModelType.VISION] = visionConfig
        }

    private fun fakeSettingsRepository(settings: SettingsData): FakeSettingsRepository =
        FakeSettingsRepository(initial = settings)

    private fun fakeMessageRepository(resolvedImageTarget: ResolvedImageTarget?): FakeMessageRepository =
        FakeMessageRepository(
            initial =
                listOf(
                    Message(
                        id = MessageId("user"),
                        chatId = ChatId("chat"),
                        content = Content(text = ""),
                    ),
                ),
        ).apply {
            this.resolvedImageTarget = resolvedImageTarget
        }

    private fun fakeMemoriesRepository(): FakeMemoriesRepository = FakeMemoriesRepository()

    private fun fakeEmbeddingEngine(): FakeEmbeddingEngine =
        FakeEmbeddingEngine().apply {
            uniformEmbedding = floatArrayOf(0.1f, 0.2f)
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
                        fakeActiveModelProvider(
                            fastConfig = litertThinkingConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = fakeSettingsRepository(SettingsData(searchEnabled = false)),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
                        fakeActiveModelProvider(
                            fastConfig = ggufThinkingConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository = fakeSettingsRepository(SettingsData(searchEnabled = false)),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(), // thinkingEnabled = false
                            visionConfig = null,
                        ),
                    settingsRepository = fakeSettingsRepository(SettingsData(searchEnabled = false)),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
            val classifier =
                FakeSearchIntentClassifier().apply {
                    probability = 0.9f
                    requiresSearchResult = true
                }
            val executor =
                FakeToolExecutor().apply {
                    behavior = { request ->
                        com.browntowndev.enclave.domain.model.inference.ToolExecutionResult(
                            toolName = ToolDefinition.WEB_SEARCH.name,
                            resultJson =
                                """{"results":[{"title":"Search Title","content":"${"Search result content ".repeat(2_000)}"}]}""",
                        )
                    }
                }

            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        fakeSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
                    searchIntentClassifier = classifier,
                    toolExecutor = executor,
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
            val classifier =
                FakeSearchIntentClassifier().apply {
                    probability = 0.95f
                    requiresSearchResult = true
                }
            val executor = FakeToolExecutor()

            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        fakeSettingsRepository(
                            SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                        ),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
                    searchIntentClassifier = classifier,
                    toolExecutor = executor,
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
            assertTrue(executor.executedRequests.isEmpty())
            assertEquals(0, classifier.probabilityCallCount)
        }

    @Test
    fun `SCENARIO_PREPARER_ELIGIBLE_TOOLS`() =
        runTest {
            val preparer =
                ChatInferenceRequestPreparer(
                    activeModelProvider =
                        fakeActiveModelProvider(
                            fastConfig = fastTextConfig(),
                            visionConfig = null,
                        ),
                    settingsRepository =
                        fakeSettingsRepository(
                            SettingsData(xSearchEnabled = true, xApiKeyPresent = true),
                        ),
                    messageRepository = fakeMessageRepository(resolvedImageTarget = null),
                    memoriesRepository = fakeMemoriesRepository(),
                    embeddingEnginePort = fakeEmbeddingEngine(),
                    promptComposer = SearchToolPromptComposer(),
                    defaultModelRepository = FakeDefaultModelRepository(),
                    mediaProviderRepository = FakeMediaProviderRepository(),
                    mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
                    getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
                    loggingPort = FakeLoggingPort(),
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
