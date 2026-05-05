package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatInferenceRequestPreparerXSearchTest {

    @Test
    fun `X Search tool included when enabled`() = runTest {
        val preparer = createPreparer(SettingsData(xSearchEnabled = true))

        val request = preparer(
            prompt = "Search X",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST,
        )

        assertTrue(request.options.availableTools.contains(ToolDefinition.X_SEARCH))
        assertTrue(request.options.systemPrompt?.contains("search_x_recent") == true)
    }

    @Test
    fun `X Search tool excluded when disabled`() = runTest {
        val preparer = createPreparer(SettingsData(xSearchEnabled = false))

        val request = preparer(
            prompt = "Just chat",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST,
        )

        assertFalse(request.options.availableTools.contains(ToolDefinition.X_SEARCH))
        assertFalse(request.options.systemPrompt?.contains("search_x_recent") == true)
    }

    @Test
    fun `X Search and Web Search are independent`() = runTest {
        val preparer = createPreparer(SettingsData(searchEnabled = true, xSearchEnabled = true))

        val request = preparer(
            prompt = "Search both",
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            modelType = ModelType.FAST,
        )

        assertTrue(request.options.availableTools.contains(ToolDefinition.WEB_SEARCH))
        assertTrue(request.options.availableTools.contains(ToolDefinition.X_SEARCH))
    }

    private fun createPreparer(settings: SettingsData): ChatInferenceRequestPreparer {
        return ChatInferenceRequestPreparer(
            activeModelProvider = mockk {
                coEvery { getActiveConfiguration(ModelType.FAST) } returns ActiveModelConfiguration(
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
            settingsRepository = mockk {
                every { settingsFlow } returns flowOf(settings)
            },
            messageRepository = mockk {
                coEvery { getMessageById(any()) } returns mockk(relaxed = true)
                coEvery { resolveLatestImageBearingUserMessage(any(), any()) } returns null
            },
            memoriesRepository = mockk {
                coEvery { getCoreMemories() } returns emptyList()
                coEvery { searchMemories(any(), any()) } returns emptyList()
            },
            embeddingEnginePort = mockk {
                coEvery { getEmbedding(any()) } returns floatArrayOf(0.1f)
            },
            searchToolPromptComposer = SearchToolPromptComposer(),
            defaultModelRepository = mockk(relaxed = true),
            mediaProviderRepository = mockk(relaxed = true),
            mediaToolDefinitionFactory = mockk(relaxed = true),
            getProviderCapabilitiesUseCase = mockk(relaxed = true),
            loggingPort = mockk(relaxed = true),
        )
    }
}
