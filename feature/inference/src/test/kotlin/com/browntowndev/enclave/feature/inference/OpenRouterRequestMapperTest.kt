package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.enclave.domain.model.config.OpenRouterProviderSort
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.chat.ChatInferenceRequestPreparer
import com.browntowndev.enclave.domain.usecase.chat.SearchToolPromptComposer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class OpenRouterRequestMapperTest {
    @Test
    fun `LTD-13 search_x_recent remains catalog-visible but inactive until disclosure`() =
        runTest {
            val preparer = createPreparer(SettingsData(xSearchEnabled = true))
            val request =
                preparer(
                    prompt = "What is trending on X?",
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                )

            val prompt = request.options.systemPrompt.orEmpty()
            val params =
                OpenRouterRequestMapper.mapToChatCompletionParams(
                    modelId = "openai/gpt-5.2",
                    prompt = request.prompt,
                    history = emptyList(),
                    options = request.options,
                )
            val registeredToolNames = params.tools().orElseThrow().map { it.asFunction().function().name() }

            assertAll(
                { assertTrue(request.options.toolCatalog.any { it.name == "search_x_recent" }) },
                { assertFalse(request.options.activeTools.contains(ToolDefinition.X_SEARCH)) },
                { assertFalse(request.options.activeTools.contains(ToolDefinition.X_SEARCH)) },
                { assertTrue(prompt.contains("# TOOL CATALOG:")) },
                { assertTrue(prompt.contains("search_x_recent")) },
                { assertFalse(prompt.contains("ActionResult{\"name\":\"search_x_recent\"")) },
                { assertFalse(prompt.contains("call:search_x_recent")) },
                { assertFalse(registeredToolNames.contains("search_x_recent")) },
            )
        }

    private fun createPreparer(settings: SettingsData): ChatInferenceRequestPreparer =
        ChatInferenceRequestPreparer(
            activeModelProvider =
                mockk {
                    coEvery { getActiveConfiguration(ModelType.FAST) } returns
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
                        )
                    coEvery { getActiveConfiguration(ModelType.VISION) } returns null
                },
            settingsRepository =
                mockk {
                    every { settingsFlow } returns flowOf(settings)
                },
            messageRepository =
                mockk {
                    coEvery { getMessageById(any()) } returns mockk(relaxed = true)
                    coEvery { resolveLatestImageBearingUserMessage(any(), any()) } returns null
                },
            memoriesRepository =
                mockk {
                    coEvery { getCoreMemories() } returns emptyList()
                    coEvery { searchMemories(any(), any(), any(), any()) } returns emptyList()
                },
            embeddingEnginePort =
                mockk {
                    coEvery { getEmbedding(any()) } returns floatArrayOf(0.1f)
                },
            promptComposer = SearchToolPromptComposer(),
            defaultModelRepository = mockk(relaxed = true),
            mediaProviderRepository = mockk(relaxed = true),
            mediaToolDefinitionFactory = mockk(relaxed = true),
            getProviderCapabilitiesUseCase = mockk(relaxed = true),
            loggingPort = mockk(relaxed = true),
        )

    @Test
    fun `mapToResponseParams adds openrouter fallback routing defaults`() {
        val params =
            OpenRouterRequestMapper.mapToResponseParams(
                modelId = "openai/gpt-5.2",
                prompt = "hello",
                history = listOf(ChatMessage(Role.SYSTEM, "You are concise.")),
                options = GenerationOptions(reasoningBudget = 0, maxTokens = 128),
            )

        assertTrue(
            params
                .model()
                .get()
                .toString()
                .contains("openai/gpt-5.2"),
        )
        assertTrue(params._additionalBodyProperties().containsKey("provider"))
        val providerBody = params._additionalBodyProperties()["provider"].toString()
        assertTrue(providerBody.contains("allow_fallbacks"))
        assertTrue(providerBody.contains("sort"))
        assertTrue(providerBody.contains("require_parameters"))
        assertEquals(128L, params.maxOutputTokens().get())
        assertEquals("You are concise.", params.instructions().get())
    }

    @Test
    fun `mapToChatCompletionParams adds openrouter fallback routing defaults`() {
        val params =
            OpenRouterRequestMapper.mapToChatCompletionParams(
                modelId = "openai/gpt-5.2",
                prompt = "hello",
                history = emptyList(),
                options = GenerationOptions(reasoningBudget = 0, maxTokens = 128),
            )

        assertEquals("openai/gpt-5.2", params.model().toString())
        assertTrue(params._additionalBodyProperties().containsKey("provider"))
        assertTrue(params._additionalBodyProperties()["provider"].toString().contains("allow_fallbacks"))
        assertEquals(128L, params.maxCompletionTokens().get())
    }

    @Test
    fun `mapToChatCompletionParams adds openrouter unified reasoning body`() {
        val params =
            OpenRouterRequestMapper.mapToChatCompletionParams(
                modelId = "deepseek/deepseek-v3.2",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 2_000,
                        reasoningEffort = ApiReasoningEffort.HIGH,
                    ),
            )

        val reasoningBody = params._additionalBodyProperties()["reasoning"].toString()
        assertTrue(reasoningBody.contains("high"), reasoningBody)
        assertTrue(reasoningBody.contains("2000"), reasoningBody)
        assertTrue(reasoningBody.contains("false"), reasoningBody)
    }

    @Test
    fun `mapToResponseParams applies custom openrouter routing settings`() {
        val params =
            OpenRouterRequestMapper.mapToResponseParams(
                modelId = "openai/gpt-5.2",
                prompt = "hello",
                history = emptyList(),
                options = GenerationOptions(reasoningBudget = 0),
                routing =
                    OpenRouterRoutingConfiguration(
                        providerSort = OpenRouterProviderSort.LATENCY,
                        allowFallbacks = false,
                        requireParameters = true,
                        dataCollectionPolicy = OpenRouterDataCollectionPolicy.ALLOW,
                        zeroDataRetention = true,
                    ),
            )

        val providerBody = params._additionalBodyProperties()["provider"].toString()
        assertTrue(providerBody.contains("latency"))
        assertTrue(providerBody.contains("allow_fallbacks"))
        assertTrue(providerBody.contains("false"))
        assertTrue(providerBody.contains("require_parameters"))
        assertTrue(providerBody.contains("allow"))
        assertTrue(providerBody.contains("zdr"))
    }

    @Test
    fun `mapToResponseParams omits max output tokens when configured max equals context window`() {
        val params =
            OpenRouterRequestMapper.mapToResponseParams(
                modelId = "openai/gpt-5.2",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        maxTokens = 262_144,
                        contextWindow = 262_144,
                    ),
            )

        assertFalse(params.maxOutputTokens().isPresent)
    }

    @Test
    fun `mapToChatCompletionParams omits max completion tokens when configured max equals context window`() {
        val params =
            OpenRouterRequestMapper.mapToChatCompletionParams(
                modelId = "openai/gpt-5.2",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        maxTokens = 262_144,
                        contextWindow = 262_144,
                    ),
            )

        assertFalse(params.maxCompletionTokens().isPresent)
    }

    @Test
    fun `mapToResponseParams includes image uris in user message`() {
        val tempFile =
            java.io.File.createTempFile("test", ".png").apply {
                writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                deleteOnExit()
            }
        val params =
            OpenRouterRequestMapper.mapToResponseParams(
                modelId = "openai/gpt-5.2",
                prompt = "what is in this image?",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        imageUris = listOf(tempFile.toURI().toString()),
                    ),
            )

        // The image_url check in Responses API is through ResponseInputImage
        val input = params.input().orElseThrow().toString()
        assertTrue(input.contains("what is in this image?"))
        assertTrue(input.contains("imageUrl"))
        assertTrue(input.contains("data:image/png;base64,"))
    }

    @Test
    fun `mapToChatCompletionParams includes image uris in user message`() {
        val tempFile =
            java.io.File.createTempFile("test", ".png").apply {
                writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                deleteOnExit()
            }
        val params =
            OpenRouterRequestMapper.mapToChatCompletionParams(
                modelId = "openai/gpt-5.2",
                prompt = "describe image",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        imageUris = listOf(tempFile.toURI().toString()),
                    ),
            )

        val chatBody = params.toString()
        assertTrue(chatBody.contains("describe image"))
        assertTrue(chatBody.contains("image_url"))
        assertTrue(chatBody.contains("data:image/png;base64,"))
    }

    @Test
    fun `mapToResponseParams serializes web_search when tooling is enabled`() {
        val params =
            OpenRouterRequestMapper.mapToResponseParams(
                modelId = "openai/gpt-5.2",
                prompt = "find recent android tool news",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        toolingEnabled = true,
                        activeTools = listOf(ToolDefinition.WEB_SEARCH),
                    ),
            )

        assertTrue(params.toString().contains("web_search"))
    }
}
