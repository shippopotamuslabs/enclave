package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.chat.TavilySource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PersistAccumulatedChatMessagesUseCaseTest {

    @Test
    fun `persist use case strips tool traces before storing content`() = runTest {
        val chatRepository = mockk<ChatRepository>(relaxed = true)
        val contentSlot = slot<String>()
        val stateSlot = slot<MessageState>()
        coEvery {
            chatRepository.persistAllMessageData(
                messageId = any(),
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = capture(contentSlot),
                messageState = capture(stateSlot),
                pipelineStep = any(),
                tavilySources = any(),
            )
        } returns Unit
        val manager = ChatGenerationAccumulatorManager(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            defaultAssistantMessageId = MessageId("assistant"),
            chatRepository = chatRepository,
        )
        val embeddingEngine = mockk<EmbeddingEnginePort>()
        coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0.1f)
        val persistUseCase = PersistAccumulatedChatMessagesUseCase(
            chatRepository = chatRepository,
            messageRepository = mockk(relaxed = true),
            embeddingEngine = embeddingEngine,
            extractedUrls = emptySet(),
        )

        manager.reduce(
            MessageGenerationState.GeneratingText(
                """{"name":"tavily_web_search","arguments":{"query":"android"}}""",
                ModelType.FAST,
            )
        )
        manager.reduce(
            MessageGenerationState.GeneratingText(
                """<tool_result>{"answer":"value"}</tool_result>Final answer""",
                ModelType.FAST,
            )
        )
        manager.reduce(MessageGenerationState.Finished(ModelType.FAST))
        persistUseCase(manager)

        assertEquals("Final answer", contentSlot.captured)
        assertEquals(MessageState.COMPLETE, stateSlot.captured)
        assertFalse(contentSlot.captured.contains("tool_call"))
    }

    @Test
    fun `persist use case strips CDATA tool traces before storing content`() = runTest {
        val chatRepository = mockk<ChatRepository>(relaxed = true)
        val contentSlot = slot<String>()
        coEvery {
            chatRepository.persistAllMessageData(
                messageId = any(),
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = capture(contentSlot),
                messageState = any(),
                pipelineStep = any(),
                tavilySources = any(),
            )
        } returns Unit
        val manager = ChatGenerationAccumulatorManager(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            defaultAssistantMessageId = MessageId("assistant"),
            chatRepository = chatRepository,
        )
        val embeddingEngine = mockk<EmbeddingEnginePort>()
        coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0.1f)
        val persistUseCase = PersistAccumulatedChatMessagesUseCase(
            chatRepository = chatRepository,
            messageRepository = mockk(relaxed = true),
            embeddingEngine = embeddingEngine,
            extractedUrls = emptySet(),
        )

        manager.reduce(
            MessageGenerationState.GeneratingText(
                """<![CDATA[<tool>{"name":"tavily_web_search","arguments":{"query":"android"}}</tool>]]>""",
                ModelType.FAST,
            )
        )
        manager.reduce(
            MessageGenerationState.GeneratingText(
                """<tool_result>{"answer":"value"}</tool_result>Final answer""",
                ModelType.FAST,
            )
        )
        manager.reduce(MessageGenerationState.Finished(ModelType.FAST))
        persistUseCase(manager)

        assertEquals("Final answer", contentSlot.captured)
        assertFalse(contentSlot.captured.contains("<![CDATA[<tool"))
    }

    @Test
    fun `persist use case keeps incomplete messages in processing state`() = runTest {
        val chatRepository = mockk<ChatRepository>(relaxed = true)
        val stateSlot = slot<MessageState>()
        coEvery {
            chatRepository.persistAllMessageData(
                messageId = any(),
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = any(),
                messageState = capture(stateSlot),
                pipelineStep = any(),
                tavilySources = any(),
            )
        } returns Unit
        val manager = ChatGenerationAccumulatorManager(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            defaultAssistantMessageId = MessageId("assistant"),
            chatRepository = chatRepository,
        )

        manager.reduce(MessageGenerationState.Processing(ModelType.FAST))
        val embeddingEngine = mockk<EmbeddingEnginePort>()
        coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0.1f)
        val persistUseCase = PersistAccumulatedChatMessagesUseCase(
            chatRepository = chatRepository,
            messageRepository = mockk(relaxed = true),
            embeddingEngine = embeddingEngine,
            extractedUrls = emptySet(),
        )
        persistUseCase(manager)

        coVerify(exactly = 1) { chatRepository.persistAllMessageData(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(MessageState.PROCESSING, stateSlot.captured)
    }

    @Test
    fun `persist use case applies extracted flag to sources before persisting`() = runTest {
        val chatRepository = mockk<ChatRepository>(relaxed = true)
        val sourcesSlot = slot<List<TavilySource>>()
        coEvery {
            chatRepository.persistAllMessageData(
                messageId = any(),
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = any(),
                messageState = any(),
                pipelineStep = any(),
                tavilySources = capture(sourcesSlot),
            )
        } returns Unit

        val manager = ChatGenerationAccumulatorManager(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            defaultAssistantMessageId = MessageId("assistant"),
            chatRepository = chatRepository,
        )

        val source1 = TavilySource(
            messageId = MessageId("assistant"),
            title = "Result 1",
            url = "https://example.com/page1",
            content = "Content 1",
            score = 0.9,
            extracted = false,
        )
        val source2 = TavilySource(
            messageId = MessageId("assistant"),
            title = "Result 2",
            url = "https://example.com/page2",
            content = "Content 2",
            score = 0.8,
            extracted = false,
        )

        manager.reduce(MessageGenerationState.TavilySourcesAttached(listOf(source1, source2), ModelType.FAST))
        manager.reduce(MessageGenerationState.Finished(ModelType.FAST))

        val extractedUrls = setOf("https://example.com/page1")
        val embeddingEngine = mockk<EmbeddingEnginePort>()
        coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0.1f)
        val persistUseCase = PersistAccumulatedChatMessagesUseCase(
            chatRepository = chatRepository,
            messageRepository = mockk(relaxed = true),
            embeddingEngine = embeddingEngine,
            extractedUrls = extractedUrls,
        )
        persistUseCase(manager)

        // The persisted sources should have extracted=true for URLs in extractedUrls
        val persistedSources = sourcesSlot.captured
        assertEquals(2, persistedSources.size)
        assertTrue(persistedSources.first { it.url == "https://example.com/page1" }.extracted,
            "Source whose URL is in extractedUrls should have extracted=true")
        assertFalse(persistedSources.first { it.url == "https://example.com/page2" }.extracted,
            "Source whose URL is NOT in extractedUrls should have extracted=false")

        // markSourcesExtracted should also be called as a safety net
        coVerify(exactly = 1) { chatRepository.markSourcesExtracted(extractedUrls.toList()) }
    }
}
