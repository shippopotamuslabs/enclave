package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.chat.TRANSCRIPT_SEGMENT_SEPARATOR
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PersistAccumulatedChatMessagesUseCaseTest {
    private fun manager(chatRepository: FakeChatRepository): ChatGenerationAccumulatorManager =
        ChatGenerationAccumulatorManager(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            defaultAssistantMessageId = MessageId("assistant"),
            chatRepository = chatRepository,
        )

    private fun persistUseCase(
        chatRepository: FakeChatRepository,
        extractedUrls: Set<String> = emptySet(),
    ): PersistAccumulatedChatMessagesUseCase =
        PersistAccumulatedChatMessagesUseCase(
            chatRepository = chatRepository,
            messageRepository = FakeMessageRepository(),
            embeddingEngine = FakeEmbeddingEngine(),
            extractedUrls = extractedUrls,
        )

    @Test
    fun `persist use case strips tool traces before storing content`() =
        runTest {
            val chatRepository = FakeChatRepository()
            val manager = manager(chatRepository)
            val persistUseCase = persistUseCase(chatRepository)

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

            val persisted = chatRepository.persistAllMessageDataCalls.single()
            assertEquals("Final answer", persisted.content)
            assertEquals(MessageState.COMPLETE, persisted.messageState)
            assertFalse(persisted.content.contains("tool_call"))
        }

    @Test
    fun `FAAL-PS-01 sanitize removes tool_call trace`() =
        runTest {
            val chatRepository = FakeChatRepository()
            val manager = manager(chatRepository)
            val persistUseCase = persistUseCase(chatRepository)

            manager.reduce(
                MessageGenerationState.GeneratingText(
                    """Hello <tool_call>{"name":"append_blocks"}</tool_call> world""",
                    ModelType.FAST,
                ),
            )
            manager.reduce(MessageGenerationState.Finished(ModelType.FAST))
            persistUseCase(manager)

            val persisted = chatRepository.persistAllMessageDataCalls.single()
            assertFalse(persisted.content.contains("<tool_call>"), "FAAL-PS-01 opening tag")
            assertFalse(persisted.content.contains("</tool_call>"), "FAAL-PS-01 closing tag")
        }

    @Test
    fun `FAAL-PS-02 content length guard caps before Room write`() =
        runTest {
            val chatRepository = FakeChatRepository()
            val manager = manager(chatRepository)
            val persistUseCase = persistUseCase(chatRepository)
            val maxPersistContentChars = 100_000

            manager.reduce(
                MessageGenerationState.GeneratingText(
                    "x".repeat(maxPersistContentChars + 1),
                    ModelType.FAST,
                ),
            )
            manager.reduce(MessageGenerationState.Finished(ModelType.FAST))
            persistUseCase(manager)

            val persisted = chatRepository.persistAllMessageDataCalls.single()
            assertTrue(
                persisted.content.length <= maxPersistContentChars,
                "FAAL-PS-02 persisted content length",
            )
        }

    @Test
    fun `persist use case strips CDATA tool traces before storing content`() =
        runTest {
            val chatRepository = FakeChatRepository()
            val manager = manager(chatRepository)
            val persistUseCase = persistUseCase(chatRepository)

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

            val persisted = chatRepository.persistAllMessageDataCalls.single()
            assertEquals("Final answer", persisted.content)
            assertFalse(persisted.content.contains("<![CDATA[<tool"))
        }

    @Test
    fun `persist use case keeps incomplete messages in processing state`() =
        runTest {
            val chatRepository = FakeChatRepository()
            val manager = manager(chatRepository)

            manager.reduce(MessageGenerationState.Processing(ModelType.FAST))
            val persistUseCase = persistUseCase(chatRepository)
            persistUseCase(manager)

            assertEquals(1, chatRepository.persistAllMessageDataCalls.size)
            assertEquals(MessageState.PROCESSING, chatRepository.persistAllMessageDataCalls.single().messageState)
        }

    @Test
    fun `persist use case applies extracted flag to sources before persisting`() =
        runTest {
            val chatRepository = FakeChatRepository()
            val manager = manager(chatRepository)

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
            val persistUseCase = persistUseCase(chatRepository, extractedUrls = extractedUrls)
            persistUseCase(manager)

            // The persisted sources should have extracted=true for URLs in extractedUrls
            val persistedSources = chatRepository.persistAllMessageDataSources.single()
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
            assertEquals(listOf(extractedUrls.toList()), chatRepository.markWebSearchSourcesExtractedCalls)
        }

    @Test
    fun `persist passes transcript segments stitched consistently with thinking_raw and content`() =
        runTest {
            val chatRepository = FakeChatRepository()
            val manager = manager(chatRepository)
            manager.reduce(MessageGenerationState.Processing(ModelType.FAST))
            manager.reduce(MessageGenerationState.ThinkingLive("wave-a-", ModelType.FAST))
            manager.reduce(MessageGenerationState.GeneratingText("reply1", ModelType.FAST))
            manager.reduce(MessageGenerationState.ThinkingLive("wave-b-", ModelType.FAST))
            manager.reduce(MessageGenerationState.GeneratingText("reply2", ModelType.FAST))
            manager.reduce(MessageGenerationState.Finished(ModelType.FAST))

            val persistUseCase = persistUseCase(chatRepository)
            persistUseCase(manager)

            val assistantAcc = manager.messages.getValue(MessageId("assistant"))
            val segments = chatRepository.persistAllMessageDataSegments.single()
            assertEquals(4, segments.size)

            val stitchedThinking =
                segments
                    .filterIsInstance<MessageTranscriptSegment.Thinking>()
                    .joinToString(TRANSCRIPT_SEGMENT_SEPARATOR) { it.text }
            val stitchedAnswer =
                segments
                    .filterIsInstance<MessageTranscriptSegment.AssistantText>()
                    .joinToString("") { it.text }

            assertEquals(assistantAcc.thinkingRaw.toString(), stitchedThinking)
            assertEquals(assistantAcc.content.toString(), stitchedAnswer)
        }
}
