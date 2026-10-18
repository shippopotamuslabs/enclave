package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ExtractedUrlTrackerPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatGenerationProgressPersisterTest {
    @Test
    fun `applyState does not persist partial progress before terminal state`() =
        runTest {
            val chatRepository = FakeChatRepository()
            val session =
                ChatGenerationProgressPersister(
                    chatRepository = chatRepository,
                    messageRepository = FakeMessageRepository(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    extractedUrlTracker = FakeExtractedUrlTracker(),
                ).startSession(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                )

            session.applyState(MessageGenerationState.GeneratingText("hello", ModelType.FAST))
            session.applyState(MessageGenerationState.GeneratingText(" world", ModelType.FAST))
            session.applyState(MessageGenerationState.GeneratingText("!", ModelType.FAST))

            assertTrue(
                chatRepository.persistAllMessageDataCalls.none {
                    it.messageId == MessageId("assistant")
                },
            )
        }

    @Test
    fun `applyState persists web search sources immediately when attached`() =
        runTest {
            val chatRepository = FakeChatRepository()
            val session =
                ChatGenerationProgressPersister(
                    chatRepository = chatRepository,
                    messageRepository = FakeMessageRepository(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    extractedUrlTracker = FakeExtractedUrlTracker(),
                ).startSession(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                )
            val sources =
                listOf(
                    WebSearchSource(
                        messageId = MessageId("assistant"),
                        title = "Source",
                        url = "https://example.com",
                        content = "snippet",
                    ),
                )

            session.applyState(
                MessageGenerationState.WebSearchSourcesAttached(sources, ModelType.FAST),
            )

            assertEquals(
                listOf(MessageId("assistant") to sources),
                chatRepository.persistWebSearchSourcesCalls,
            )
            assertTrue(chatRepository.persistAllMessageDataCalls.isEmpty())
        }

    @Test
    fun `applyState always persists terminal state with complete content`() =
        runTest {
            val chatRepository = FakeChatRepository()
            val session =
                ChatGenerationProgressPersister(
                    chatRepository = chatRepository,
                    messageRepository = FakeMessageRepository(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    extractedUrlTracker = FakeExtractedUrlTracker(),
                ).startSession(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                )

            session.applyState(MessageGenerationState.GeneratingText("final answer", ModelType.FAST))
            session.applyState(MessageGenerationState.Finished(ModelType.FAST))

            val persisted = chatRepository.persistAllMessageDataCalls.single()
            assertEquals("final answer", persisted.content)
            assertEquals(MessageState.COMPLETE, persisted.messageState)
        }

    @Test
    fun `flush with cancelled partial progress does not persist truncated content`() =
        runTest {
            val chatRepository = FakeChatRepository()
            val session =
                ChatGenerationProgressPersister(
                    chatRepository = chatRepository,
                    messageRepository = FakeMessageRepository(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    extractedUrlTracker = FakeExtractedUrlTracker(),
                ).startSession(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                )

            session.applyState(MessageGenerationState.GeneratingText("truncated", ModelType.FAST))
            session.flush(markIncompleteAsCancelled = true)

            assertEquals(1, chatRepository.persistAllMessageDataCalls.size)
            assertEquals(
                MessageId("assistant"),
                chatRepository.persistAllMessageDataCalls.single().messageId,
            )
        }

    @Test
    fun `flush without cancellation does not persist incomplete progress`() =
        runTest {
            val chatRepository = FakeChatRepository()
            val session =
                ChatGenerationProgressPersister(
                    chatRepository = chatRepository,
                    messageRepository = FakeMessageRepository(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    extractedUrlTracker = FakeExtractedUrlTracker(),
                ).startSession(
                    mode = Mode.FAST,
                    chatId = ChatId("chat"),
                    userMessageId = MessageId("user"),
                    assistantMessageId = MessageId("assistant"),
                )

            session.applyState(MessageGenerationState.GeneratingText("still streaming", ModelType.FAST))
            session.flush(markIncompleteAsCancelled = false)

            assertTrue(chatRepository.persistAllMessageDataCalls.isEmpty())
        }

    private class FakeExtractedUrlTracker : ExtractedUrlTrackerPort {
        override val urls: Set<String> = emptySet()

        override fun add(url: String) = Unit

        override fun clear() = Unit
    }
}
