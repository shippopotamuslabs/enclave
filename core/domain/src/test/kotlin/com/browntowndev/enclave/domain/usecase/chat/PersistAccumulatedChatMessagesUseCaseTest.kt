package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
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
    fun `persist use case strips tool traces before storing content`() =
        runTest {
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
                    webSearchSources = any(),
                    artifacts = any(),
                    generatedMediaUris = any(),
                    generatedMediaAssetIds = any(),
                    generatedMediaTypes = any(),
                    generatedMediaPrompts = any(),
                    transcriptSegments = any(),
                )
            } returns Unit
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = chatRepository,
                )
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0.1f)
            val persistUseCase =
                PersistAccumulatedChatMessagesUseCase(
                    chatRepository = chatRepository,
                    messageRepository = mockk(relaxed = true),
                    embeddingEngine = embeddingEngine,
                    extractedUrls = emptySet(),
                )

            manager.reduce(
                MessageGenerationState.GeneratingText(
                    """{"name":"web_search","arguments":{"query":"android"}}""",
                    ModelType.FAST,
                ),
            )
            manager.reduce(
                MessageGenerationState.GeneratingText(
                    """<tool_result>{"answer":"value"}</tool_result>Final answer""",
                    ModelType.FAST,
                ),
            )
            manager.reduce(MessageGenerationState.Finished(ModelType.FAST))
            persistUseCase(manager)

            assertEquals("Final answer", contentSlot.captured)
            assertEquals(MessageState.COMPLETE, stateSlot.captured)
            assertFalse(contentSlot.captured.contains("tool_call"))
        }

    @Test
    fun `FAAL-PS-01 sanitize removes tool_call trace`() =
        runTest {
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
                    webSearchSources = any(),
                    artifacts = any(),
                    generatedMediaUris = any(),
                    generatedMediaAssetIds = any(),
                    generatedMediaTypes = any(),
                    generatedMediaPrompts = any(),
                    transcriptSegments = any(),
                )
            } returns Unit
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = chatRepository,
                )
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0.1f)
            val persistUseCase =
                PersistAccumulatedChatMessagesUseCase(
                    chatRepository = chatRepository,
                    messageRepository = mockk(relaxed = true),
                    embeddingEngine = embeddingEngine,
                    extractedUrls = emptySet(),
                )

            manager.reduce(
                MessageGenerationState.GeneratingText(
                    """Hello <tool_call>{"name":"append_blocks"}</tool_call> world""",
                    ModelType.FAST,
                ),
            )
            manager.reduce(MessageGenerationState.Finished(ModelType.FAST))
            persistUseCase(manager)

            assertFalse(contentSlot.captured.contains("<tool_call>"), "FAAL-PS-01 opening tag")
            assertFalse(contentSlot.captured.contains("</tool_call>"), "FAAL-PS-01 closing tag")
        }

    @Test
    fun `FAAL-PS-02 content length guard caps before Room write`() =
        runTest {
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
                    webSearchSources = any(),
                    artifacts = any(),
                    generatedMediaUris = any(),
                    generatedMediaAssetIds = any(),
                    generatedMediaTypes = any(),
                    generatedMediaPrompts = any(),
                    transcriptSegments = any(),
                )
            } returns Unit
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = chatRepository,
                )
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0.1f)
            val persistUseCase =
                PersistAccumulatedChatMessagesUseCase(
                    chatRepository = chatRepository,
                    messageRepository = mockk(relaxed = true),
                    embeddingEngine = embeddingEngine,
                    extractedUrls = emptySet(),
                )
            val maxPersistContentChars = 100_000

            manager.reduce(
                MessageGenerationState.GeneratingText(
                    "x".repeat(maxPersistContentChars + 1),
                    ModelType.FAST,
                ),
            )
            manager.reduce(MessageGenerationState.Finished(ModelType.FAST))
            persistUseCase(manager)

            assertTrue(
                contentSlot.captured.length <= maxPersistContentChars,
                "FAAL-PS-02 persisted content length",
            )
        }

    @Test
    fun `persist use case strips CDATA tool traces before storing content`() =
        runTest {
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
                    webSearchSources = any(),
                    artifacts = any(),
                    generatedMediaUris = any(),
                    generatedMediaAssetIds = any(),
                    generatedMediaTypes = any(),
                    generatedMediaPrompts = any(),
                    transcriptSegments = any(),
                )
            } returns Unit
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = chatRepository,
                )
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0.1f)
            val persistUseCase =
                PersistAccumulatedChatMessagesUseCase(
                    chatRepository = chatRepository,
                    messageRepository = mockk(relaxed = true),
                    embeddingEngine = embeddingEngine,
                    extractedUrls = emptySet(),
                )

            manager.reduce(
                MessageGenerationState.GeneratingText(
                    """<![CDATA[<tool>{"name":"web_search","arguments":{"query":"android"}}</tool>]]>""",
                    ModelType.FAST,
                ),
            )
            manager.reduce(
                MessageGenerationState.GeneratingText(
                    """<tool_result>{"answer":"value"}</tool_result>Final answer""",
                    ModelType.FAST,
                ),
            )
            manager.reduce(MessageGenerationState.Finished(ModelType.FAST))
            persistUseCase(manager)

            assertEquals("Final answer", contentSlot.captured)
            assertFalse(contentSlot.captured.contains("<![CDATA[<tool"))
        }

    @Test
    fun `persist use case keeps incomplete messages in processing state`() =
        runTest {
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
                    webSearchSources = any(),
                    artifacts = any(),
                    generatedMediaUris = any(),
                    generatedMediaAssetIds = any(),
                    generatedMediaTypes = any(),
                    generatedMediaPrompts = any(),
                    transcriptSegments = any(),
                )
            } returns Unit
            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = chatRepository,
                )

            manager.reduce(MessageGenerationState.Processing(ModelType.FAST))
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0.1f)
            val persistUseCase =
                PersistAccumulatedChatMessagesUseCase(
                    chatRepository = chatRepository,
                    messageRepository = mockk(relaxed = true),
                    embeddingEngine = embeddingEngine,
                    extractedUrls = emptySet(),
                )
            persistUseCase(manager)

            coVerify(exactly = 1) {
                chatRepository.persistAllMessageData(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
            assertEquals(MessageState.PROCESSING, stateSlot.captured)
        }

    @Test
    fun `persist use case applies extracted flag to sources before persisting`() =
        runTest {
            val chatRepository = mockk<ChatRepository>(relaxed = true)
            val sourcesSlot = slot<List<WebSearchSource>>()
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
                    webSearchSources = capture(sourcesSlot),
                    artifacts = any(),
                    generatedMediaUris = any(),
                    generatedMediaAssetIds = any(),
                    generatedMediaTypes = any(),
                    generatedMediaPrompts = any(),
                    transcriptSegments = any(),
                )
            } returns Unit

            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = chatRepository,
                )

            val source1 =
                WebSearchSource(
                    messageId = MessageId("assistant"),
                    title = "Result 1",
                    url = "https://example.com/page1",
                    content = "Content 1",
                    score = 0.9,
                    extracted = false,
                )
            val source2 =
                WebSearchSource(
                    messageId = MessageId("assistant"),
                    title = "Result 2",
                    url = "https://example.com/page2",
                    content = "Content 2",
                    score = 0.8,
                    extracted = false,
                )

            manager.reduce(MessageGenerationState.WebSearchSourcesAttached(listOf(source1, source2), ModelType.FAST))
            manager.reduce(MessageGenerationState.Finished(ModelType.FAST))

            val extractedUrls = setOf("https://example.com/page1")
            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0.1f)
            val persistUseCase =
                PersistAccumulatedChatMessagesUseCase(
                    chatRepository = chatRepository,
                    messageRepository = mockk(relaxed = true),
                    embeddingEngine = embeddingEngine,
                    extractedUrls = extractedUrls,
                )
            persistUseCase(manager)

            // The persisted sources should have extracted=true for URLs in extractedUrls
            val persistedSources = sourcesSlot.captured
            assertEquals(2, persistedSources.size)
            assertTrue(
                persistedSources.first { it.url == "https://example.com/page1" }.extracted,
                "Source whose URL is in extractedUrls should have extracted=true",
            )
            assertFalse(
                persistedSources.first { it.url == "https://example.com/page2" }.extracted,
                "Source whose URL is NOT in extractedUrls should have extracted=false",
            )

            // markWebSearchSourcesExtracted should also be called as a safety net
            coVerify(exactly = 1) { chatRepository.markWebSearchSourcesExtracted(extractedUrls.toList()) }
        }

    @Test
    fun `persist passes transcript segments stitched consistently with thinking_raw and content`() =
        runTest {
            val chatRepository = mockk<ChatRepository>(relaxed = true)
            val transcriptSlot =
                slot<List<MessageTranscriptSegment>>()
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
                    webSearchSources = any(),
                    artifacts = any(),
                    generatedMediaUris = any(),
                    generatedMediaAssetIds = any(),
                    generatedMediaTypes = any(),
                    generatedMediaPrompts = any(),
                    transcriptSegments = capture(transcriptSlot),
                )
            } returns Unit

            val manager =
                ChatGenerationAccumulatorManager(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    defaultAssistantMessageId = MessageId("assistant"),
                    chatRepository = chatRepository,
                )
            manager.reduce(MessageGenerationState.Processing(ModelType.FAST))
            manager.reduce(MessageGenerationState.ThinkingLive("wave-a-", ModelType.FAST))
            manager.reduce(MessageGenerationState.GeneratingText("reply1", ModelType.FAST))
            manager.reduce(MessageGenerationState.ThinkingLive("wave-b-", ModelType.FAST))
            manager.reduce(MessageGenerationState.GeneratingText("reply2", ModelType.FAST))
            manager.reduce(MessageGenerationState.Finished(ModelType.FAST))

            val embeddingEngine = mockk<EmbeddingEnginePort>()
            coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0.1f)
            val persistUseCase =
                PersistAccumulatedChatMessagesUseCase(
                    chatRepository = chatRepository,
                    messageRepository = mockk(relaxed = true),
                    embeddingEngine = embeddingEngine,
                    extractedUrls = emptySet(),
                )
            persistUseCase(manager)

            val assistantAcc = manager.messages.getValue(MessageId("assistant"))
            val segments = transcriptSlot.captured
            assertEquals(4, segments.size)

            val stitchedThinking =
                segments
                    .filterIsInstance<MessageTranscriptSegment.Thinking>()
                    .joinToString("") { it.text }
            val stitchedAnswer =
                segments
                    .filterIsInstance<MessageTranscriptSegment.AssistantText>()
                    .joinToString("") { it.text }

            assertEquals(assistantAcc.thinkingRaw.toString(), stitchedThinking)
            assertEquals(assistantAcc.content.toString(), stitchedAnswer)
        }
}
