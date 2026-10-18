package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.ResolvedImageTarget
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.MediaToolDefinitionFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatInferenceRequestPreparerToolSearchTest {
    @Test
    fun `TSD-01 preparer preloads search_tools and excludes disclose_tool_schemas`() =
        runTest {
            val preparer = preparer(searchEnabled = true, imageTarget = null)

            val request =
                preparer(
                    prompt = "Hello",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertAll(
                {
                    assertEquals(
                        listOf("tool_discovery_tool", "read_context_reference", "web_search", "web_extract"),
                        request.options.activeTools.map { it.name },
                    )
                },
                {
                    assertFalse(
                        request.options.toolCatalog.map { it.name }.contains("disclose_tool_schemas"),
                    )
                },
                { assertTrue(request.options.toolCatalog.map { it.name }.contains("append_blocks")) },
                { assertNotNull(request.options.toolSearchIndex) },
                {
                    assertEquals(
                        ToolDisclosureMode.PRELOADED,
                        request.options.toolCatalog.single { it.name == "web_search" }.disclosureMode,
                    )
                },
                {
                    assertEquals(
                        ToolDisclosureMode.DISCLOSE_ON_DEMAND,
                        request.options.toolCatalog.single { it.name == "append_blocks" }.disclosureMode,
                    )
                },
            )
        }

    @Test
    fun `TSD-20 read_context_reference is preloaded for prompt-cache prefix stability`() =
        runTest {
            val request =
                preparer(searchEnabled = true, imageTarget = null)(
                    prompt = "Hello",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertAll(
                {
                    assertTrue(
                        request.options.toolCatalog.map { it.name }.contains("read_context_reference"),
                    )
                },
                {
                    // Preloaded from turn 1 so its first mid-session spill activation does not
                    // insert a new tool into the wire tools[] block and bust the whole cache prefix.
                    assertTrue(
                        request.options.activeTools.map { it.name }.contains("read_context_reference"),
                    )
                },
            )
        }

    @Test
    fun `TSD-21 web tools omitted when web skill not configured`() =
        runTest {
            val request =
                preparer(searchEnabled = false, imageTarget = null)(
                    prompt = "Hello",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertAll(
                {
                    assertFalse(
                        request.options.toolCatalog.map { it.name }.contains("web_search"),
                    )
                },
                {
                    assertEquals(
                        listOf("tool_discovery_tool", "read_context_reference"),
                        request.options.activeTools.map { it.name },
                    )
                },
            )
        }

    @Test
    fun `TSD-22 attached_image_inspect preloaded only for image tool path`() =
        runTest {
            val toolPathRequest =
                preparer(
                    searchEnabled = true,
                    imageTarget =
                        ResolvedImageTarget(
                            userMessageId = MessageId("user"),
                            imageUris = listOf("file:///tmp/tool.jpg"),
                        ),
                    visionConfig = apiVisionConfig(),
                )(
                    prompt = "Inspect the attached photo",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )
            val noImageRequest =
                preparer(searchEnabled = true, imageTarget = null, visionConfig = null)(
                    prompt = "Hello",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertAll(
                {
                    assertTrue(
                        toolPathRequest.options.activeTools.map { it.name }.contains("attached_image_inspect"),
                    )
                },
                {
                    assertFalse(
                        noImageRequest.options.activeTools.map { it.name }.contains("attached_image_inspect"),
                    )
                },
            )
        }

    @Test
    fun `TSD-14 artifact intent adds trio to activeTools without search_tools call`() =
        runTest {
            val request =
                preparer(searchEnabled = true, imageTarget = null)(
                    prompt = "Write a PDF report about quarterly revenue",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )
            val activeNames = request.options.activeTools.map { it.name }

            assertAll(
                { assertTrue(activeNames.contains("start_artifact")) },
                { assertTrue(activeNames.contains("append_blocks")) },
                { assertTrue(activeNames.contains("search_current_artifact")) },
                {
                    assertEquals(
                        ToolDisclosureMode.INTENT_ACTIVATED,
                        request.options.toolCatalog.single { it.name == "start_artifact" }.disclosureMode,
                    )
                },
            )
        }

    @Test
    fun `MUT-03 disclose_tool_schemas absent from preload and catalog`() =
        runTest {
            val request =
                preparer(searchEnabled = true, imageTarget = null)(
                    prompt = "Hello",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            assertAll(
                {
                    assertFalse(
                        request.options.toolCatalog.map { it.name }.contains("disclose_tool_schemas"),
                    )
                },
                {
                    assertFalse(
                        request.options.activeTools.map { it.name }.contains("disclose_tool_schemas"),
                    )
                },
                {
                    assertFalse(
                        ToolDefinition.ALL_TOOLS.map { it.name }.contains("disclose_tool_schemas"),
                    )
                },
            )
        }

    @Test
    fun `TSD-29 preparer rehydrates search_tools activations from monotonic store on next turn`() =
        runTest {
            val monotonicStore = InMemoryMonotonicToolActivationStore()
            monotonicStore.recordActivation(ChatId("chat-search-rehydrate"), setOf("append_blocks"))
            val preparer =
                preparer(
                    searchEnabled = true,
                    imageTarget = null,
                    monotonicToolActivation = monotonicStore,
                )

            val request =
                preparer(
                    prompt = "continue writing",
                    chatId = ChatId("chat-search-rehydrate"),
                    userMessageId = MessageId("user-2"),
                    assistantMessageId = MessageId("assistant-2"),
                    modelType = ModelType.FAST,
                )

            assertTrue(
                request.options.activeTools.any { it.name == "append_blocks" },
                "Preparer should merge monotonic search_tools activations into activeTools",
            )
        }

    private fun preparer(
        searchEnabled: Boolean,
        imageTarget: ResolvedImageTarget?,
        visionConfig: ActiveModelConfiguration? = null,
        monotonicToolActivation: InMemoryMonotonicToolActivationStore = InMemoryMonotonicToolActivationStore(),
    ): ChatInferenceRequestPreparer {
        val fastConfig =
            ActiveModelConfiguration(
                modelId = "test-model",
                id = LocalModelConfigurationId("fast-text"),
                isLocal = true,
                name = "Fast",
                systemPrompt = "Be concise.",
                reasoningEffort = null,
                temperature = 0.7,
                topK = null,
                topP = null,
                minP = null,
                maxTokens = 1024,
                repetitionPenalty = null,
                contextWindow = 4096,
                thinkingEnabled = false,
            )
        return ChatInferenceRequestPreparer(
            activeModelProvider =
                FakeActiveModelProvider(configuration = fastConfig).apply {
                    configurationsByType[ModelType.VISION] = visionConfig
                },
            settingsRepository =
                FakeSettingsRepository(
                    initial =
                        SettingsData(
                            searchEnabled = searchEnabled,
                            tavilyKeyPresent = searchEnabled,
                        ),
                ),
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
                ).apply {
                    resolvedImageTarget = imageTarget
                },
            memoriesRepository = FakeMemoriesRepository(),
            embeddingEnginePort =
                FakeEmbeddingEngine().apply {
                    uniformEmbedding = ToolSearchTestFixtures.genericTestEmbedding()
                },
            promptComposer = SearchToolPromptComposer(),
            defaultModelRepository = FakeDefaultModelRepository(),
            mediaProviderRepository = FakeMediaProviderRepository(),
            mediaToolDefinitionFactory = MediaToolDefinitionFactory(GetProviderCapabilitiesUseCase()),
            getProviderCapabilitiesUseCase = GetProviderCapabilitiesUseCase(),
            loggingPort = FakeLoggingPort(),
            monotonicToolActivation = monotonicToolActivation,
        )
    }

    private fun apiVisionConfig(): ActiveModelConfiguration =
        ActiveModelConfiguration(
            modelId = "vision-api",
            id = ApiModelConfigurationId("vision-api"),
            isLocal = false,
            name = "Vision API",
            systemPrompt = "Inspect images.",
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
}
