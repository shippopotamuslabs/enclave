package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.ResolvedImageTarget
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatInferenceRequestPreparerTest {

    @Test
    fun `multimodal local model sends image directly without tool contract`() = runTest {
        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastVisionConfig(),
                visionConfig = null,
            ),
            settingsRepository = mockSettingsRepository(SettingsData()),
            messageRepository = mockMessageRepository(
                resolvedImageTarget = ResolvedImageTarget(
                    userMessageId = MessageId("user"),
                    imageUri = "file:///tmp/direct.jpg",
                )
            ),
            searchToolPromptComposer = SearchToolPromptComposer(),
            loggingPort = mockk(relaxed = true),
        )

        val request = preparer(
            prompt = "What is in the image?",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST,
        )

        assertEquals(listOf("file:///tmp/direct.jpg"), request.options.imageUris)
        assertFalse(request.options.availableTools.contains(ToolDefinition.TAVILY_WEB_SEARCH))
        assertFalse(request.options.availableTools.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT))
        assertTrue(request.options.availableTools.contains(ToolDefinition.SEARCH_CHAT_HISTORY))
        assertTrue(request.options.availableTools.contains(ToolDefinition.SEARCH_CHAT))
        assertTrue(request.options.toolingEnabled)
        assertTrue(request.prompt.contains("The user attached an image."))
    }

    @Test
    fun `local text model composes both search and image tool contracts when eligible`() = runTest {
        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig(),
                visionConfig = apiVisionConfig(),
            ),
            settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = true)),
            messageRepository = mockMessageRepository(
                resolvedImageTarget = ResolvedImageTarget(
                    userMessageId = MessageId("user"),
                    imageUri = "file:///tmp/tool.jpg",
                )
            ),
            searchToolPromptComposer = SearchToolPromptComposer(),
            loggingPort = mockk(relaxed = true),
        )

        val request = preparer(
            prompt = "Find similar defects and inspect the image",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST,
        )

        assertTrue(request.options.toolingEnabled)
        assertTrue(request.options.imageUris.isEmpty())
        assertTrue(request.options.availableTools.contains(ToolDefinition.TAVILY_WEB_SEARCH))
        assertTrue(request.options.availableTools.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT))
        assertTrue(request.options.systemPrompt?.contains("Be concise.") == true)
        assertTrue(request.options.systemPrompt?.contains("attached_image_inspect") == true)
        assertTrue(request.prompt.contains("use attached_image_inspect"))
    }

    @Test
    fun `search enabled includes both TAVILY_WEB_SEARCH and TAVILY_EXTRACT in availableTools`() = runTest {
        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig(),
                visionConfig = null,
            ),
            settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = true)),
            messageRepository = mockMessageRepository(resolvedImageTarget = null),
            searchToolPromptComposer = SearchToolPromptComposer(),
            loggingPort = mockk(relaxed = true),
        )

        val request = preparer(
            prompt = "What is Android 17?",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST,
        )

        assertTrue(request.options.availableTools.contains(ToolDefinition.TAVILY_WEB_SEARCH))
        assertTrue(request.options.availableTools.contains(ToolDefinition.TAVILY_EXTRACT))
    }

    @Test
    fun `LiteRT model triggers LITE_RT_NATIVE strategy`() = runTest {
        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig().copy(
                    localModelFormat = com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.LITERTLM
                ),
                visionConfig = null,
            ),
            settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = true)),
            messageRepository = mockMessageRepository(resolvedImageTarget = null),
            searchToolPromptComposer = SearchToolPromptComposer(),
            loggingPort = mockk(relaxed = true),
        )

        val request = preparer(
            prompt = "Search for LiteRT",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST,
        )

        assertTrue(request.options.systemPrompt?.contains("call:tavily_web_search") == true)
        // tool_call may appear in strict rules
    }

    @Test
    fun `API model triggers SDK_NATIVE strategy`() = runTest {
        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = mockActiveModelProvider(
                fastConfig = apiVisionConfig(), // API model
                visionConfig = null,
            ),
            settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = true)),
            messageRepository = mockMessageRepository(resolvedImageTarget = null),
            searchToolPromptComposer = SearchToolPromptComposer(),
            loggingPort = mockk(relaxed = true),
        )

        val request = preparer(
            prompt = "Search with API",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST,
        )

        // SDK_NATIVE strategy should result in no tool instructions in system prompt
        assertFalse(request.options.systemPrompt?.contains("tavily_web_search") == true)
        assertTrue(request.options.availableTools.contains(ToolDefinition.TAVILY_WEB_SEARCH))
    }

    @Test
    fun `GGUF model triggers JSON_XML_ENVELOPE strategy`() = runTest {
        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig().copy(
                    localModelFormat = com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.GGUF
                ),
                visionConfig = null,
            ),
            settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = true)),
            messageRepository = mockMessageRepository(resolvedImageTarget = null),
            searchToolPromptComposer = SearchToolPromptComposer(),
            loggingPort = mockk(relaxed = true),
        )

        val request = preparer(
            prompt = "Search for GGUF",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST,
        )

        assertTrue(request.options.systemPrompt?.contains("<tool_call>") == true)
        assertTrue(request.options.systemPrompt?.contains("tavily_web_search") == true)
    }

    @Test
    fun `disabling search removes tools and instructions`() = runTest {
        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig(),
                visionConfig = null,
            ),
            settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = false)),
            messageRepository = mockMessageRepository(resolvedImageTarget = null),
            searchToolPromptComposer = SearchToolPromptComposer(),
            loggingPort = mockk(relaxed = true),
        )

        val request = preparer(
            prompt = "Just chat",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST,
        )

        assertFalse(request.options.availableTools.contains(ToolDefinition.TAVILY_WEB_SEARCH))
        assertTrue(request.options.availableTools.contains(ToolDefinition.SEARCH_CHAT_HISTORY))
        assertTrue(request.options.availableTools.contains(ToolDefinition.SEARCH_CHAT))
        assertTrue(request.options.availableTools.contains(ToolDefinition.GET_MESSAGE_CONTEXT))
        assertTrue(request.options.toolingEnabled)
        assertFalse(request.options.systemPrompt?.contains("tavily_web_search") == true)
        assertTrue(request.options.systemPrompt?.contains("search_chat_history") == true)
    }

    @Test
    fun `GET_MESSAGE_CONTEXT is always included in availableTools`() = runTest {
        val preparer = ChatInferenceRequestPreparer(
            activeModelProvider = mockActiveModelProvider(
                fastConfig = fastTextConfig(),
                visionConfig = null,
            ),
            settingsRepository = mockSettingsRepository(SettingsData(searchEnabled = false)),
            messageRepository = mockMessageRepository(resolvedImageTarget = null),
            searchToolPromptComposer = SearchToolPromptComposer(),
            loggingPort = mockk(relaxed = true),
        )

        val request = preparer(
            prompt = "Hello",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST,
        )

        assertTrue(request.options.availableTools.contains(ToolDefinition.GET_MESSAGE_CONTEXT))
    }

    @Test
    fun `blank image prompt adds fallback instruction`() {
        val prompt = prepareChatPrompt(
            prompt = "   ",
            hasImageContext = true,
            imageHandling = ChatImageHandling.DIRECT,
        )

        assertTrue(prompt.contains("Respond helpfully based on the image"))
        assertTrue(prompt.contains("The user attached an image."))
    }

    private fun mockActiveModelProvider(
        fastConfig: ActiveModelConfiguration,
        visionConfig: ActiveModelConfiguration?,
    ): ActiveModelProviderPort = mockk {
        coEvery { getActiveConfiguration(ModelType.FAST) } returns fastConfig
        coEvery { getActiveConfiguration(ModelType.VISION) } returns visionConfig
    }

    private fun mockSettingsRepository(settings: SettingsData): SettingsRepository = mockk {
        every { settingsFlow } returns flowOf(settings)
    }

    private fun mockMessageRepository(
        resolvedImageTarget: ResolvedImageTarget?,
    ): MessageRepository = mockk {
        coEvery { resolveLatestImageBearingUserMessage(any(), any()) } returns resolvedImageTarget
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
        isMultimodal = false,
    )

    private fun fastVisionConfig(): ActiveModelConfiguration = fastTextConfig().copy(
        id = LocalModelConfigurationId("fast-vision"),
        name = "Fast Vision",
        isMultimodal = true,
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
        isMultimodal = true,
    )
}
