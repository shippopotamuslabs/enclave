package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.ChatSummary
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.CompactionPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.util.JTokkitTokenCounter
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Extended tests for [ChatHistoryRehydrator] covering compaction scenarios:
 * - Compaction port returns summary (primary path)
 * - Compaction port returns null + allowLocalSummarization=true (rolling summarization)
 * - Compaction port returns null + allowLocalSummarization=false (FIFO fallback)
 * - Context window budget exceeded triggers compaction
 * - Summary persistence after compaction
 * - FIFO trimming via ChatHistoryCompressor after compaction
 */
class ChatHistoryRehydratorCompactionTest {

    private val loggingPort = object : LoggingPort {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
        override fun recordException(tag: String, message: String, throwable: Throwable) {}
    }

    private fun makeMessage(id: String, role: Role, text: String, imageUri: String? = null) = Message(
        id = MessageId(id),
        chatId = ChatId("chat"),
        content = Content(text = text, imageUri = imageUri),
        role = role,
    )

    private fun makeHistory(count: Int, contentFn: (Int) -> String = { "Message $it with enough words to be meaningful" }) =
        (1..count).flatMap {
            listOf(
                makeMessage("user-$it", Role.USER, contentFn(it)),
                makeMessage("assistant-$it", Role.ASSISTANT, contentFn(it) + " response"),
            )
        }

    // ── Compaction port returns summary ────────────────────────────────────

    @Nested
    inner class CompactionPortReturnsSummary {

        @Test
        @DisplayName("When compaction succeeds, history is replaced with summary + recent messages")
        fun compactionReplacesHistoryWithSummary() = runTest {
            // Use long messages to ensure compaction threshold is exceeded,
            // but context window is large enough to hold the summary after compaction
            val history = makeHistory(50) { i ->
                "Message number $i with a longer text content that adds more tokens to ensure we exceed the compaction threshold for this context window size"
            }
            val compactedText = "Summary of the conversation about topics 1-10."
            val compactionPort = mockk<CompactionPort> {
                coEvery { compactHistory(any()) } returns compactedText
            }
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
                coEvery { saveChatSummary(any()) } returns Unit
            }
            val service = FakeInferenceServiceForRehydrator()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-50"),
                assistantMessageId = MessageId("assistant-50"),
                service = service,
                contextWindowTokens = 512, // Small enough to trigger compaction but large enough for summary
            )

            val resultHistory = service.getHistory()
            assertTrue(resultHistory.isNotEmpty(), "History should not be empty after compaction")
            assertTrue(resultHistory.first().role == Role.SYSTEM,
                "First message should be system prompt (summary)")
            assertTrue(resultHistory.first().content.contains(compactedText),
                "System prompt should contain the compaction summary")
        }

        @Test
        @DisplayName("Compaction summary is persisted via saveChatSummary")
        fun compactionSummaryIsPersisted() = runTest {
            val history = makeHistory(50) { i ->
                "Message number $i with a longer text content that adds more tokens to ensure we exceed the compaction threshold for this context window size"
            }
            val compactedText = "Summary persisted."
            var savedSummary: ChatSummary? = null
            val compactionPort = mockk<CompactionPort> {
                coEvery { compactHistory(any()) } returns compactedText
            }
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
                coEvery { saveChatSummary(any()) } answers {
                    savedSummary = firstArg()
                    Unit
                }
            }
            val service = FakeInferenceServiceForRehydrator()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-50"),
                assistantMessageId = MessageId("assistant-50"),
                service = service,
                contextWindowTokens = 512,
            )

            assertNotNull(savedSummary, "ChatSummary should be persisted")
            assertEquals(compactedText, savedSummary!!.content)
            assertEquals(ChatId("chat"), savedSummary!!.chatId)
        }
    }

    // ── Compaction port returns null ───────────────────────────────────────

    @Nested
    inner class CompactionPortReturnsNull {

        @Test
        @DisplayName("When allowLocalSummarization=false and compaction=null, falls through to FIFO trimming")
        fun fallsThroughToTrimmingWhenLocalDisallowed() = runTest {
            val history = makeHistory(20)
            val compactionPort = mockk<CompactionPort> {
                coEvery { compactHistory(any()) } returns null
            }
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
            }
            val service = FakeInferenceServiceForRehydrator()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-20"),
                assistantMessageId = MessageId("assistant-20"),
                service = service,
                contextWindowTokens = 128,
                allowLocalSummarization = false,
            )

            // With contextWindowTokens=128, FIFO should trim most messages
            val resultHistory = service.getHistory()
            // Should NOT contain summary prefix (no summary was created)
            assertFalse(resultHistory.any { it.content.contains("Summary of previous conversation") },
                "No summary should be prepended when no compaction or summarization occurred")
            // Should have fewer messages than original due to FIFO trimming
            assertTrue(resultHistory.size < history.size,
                "History should be trimmed via FIFO compressor")
        }

        @Test
        @DisplayName("When allowLocalSummarization=true and compaction=null, attempts rolling summarization")
        fun attemptsRollingSummarizationWhenLocalAllowed() = runTest {
            val history = makeHistory(20)
            val compactionPort = mockk<CompactionPort> {
                coEvery { compactHistory(any()) } returns null
            }
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
                coEvery { saveChatSummary(any()) } returns Unit
            }
            val service = FakeInferenceServiceForRehydrator()
            service.summaryResponse = "Rolling summary of earlier conversation."
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-20"),
                assistantMessageId = MessageId("assistant-20"),
                service = service,
                contextWindowTokens = 128,
                allowLocalSummarization = true,
            )

            assertTrue(service.promptWasSent,
                "RollingSummarizer should have called sendPrompt on the service")
        }
    }

    // ── Existing summary ──────────────────────────────────────────────────

    @Nested
    inner class ExistingSummary {

        @Test
        @DisplayName("When existing summary exists, messages after summary are kept")
        fun messagesAfterSummaryAreKept() = runTest {
            val existingSummary = ChatSummary(
                chatId = ChatId("chat"),
                content = "Earlier conversation about topics 1-5.",
                lastSummarizedMessageId = MessageId("assistant-10"),
            )
            val history = makeHistory(20)
            val compactionPort = mockk<CompactionPort>(relaxed = true)
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns existingSummary
            }
            val service = FakeInferenceServiceForRehydrator()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-20"),
                assistantMessageId = MessageId("assistant-20"),
                service = service,
                contextWindowTokens = 8192,
            )

            val resultHistory = service.getHistory()
            assertTrue(resultHistory.first().content.contains(existingSummary.content),
                "First message should contain the existing summary")
        }

        @Test
        @DisplayName("When lastSummarizedMessageId is null, no messages are loaded (summary covers everything)")
        fun nullPointerLoadsNoMessages() = runTest {
            val existingSummary = ChatSummary(
                chatId = ChatId("chat"),
                content = "Summary covers the entire conversation.",
                lastSummarizedMessageId = null,
            )
            val history = makeHistory(20)
            val compactionPort = mockk<CompactionPort>(relaxed = true)
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns existingSummary
            }
            val service = FakeInferenceServiceForRehydrator()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-20"),
                assistantMessageId = MessageId("assistant-20"),
                service = service,
                contextWindowTokens = 8192,
            )

            val resultHistory = service.getHistory()
            // When lastSummarizedMessageId is null, only the summary should be in the history —
            // no message content from the full history should appear, avoiding duplicate context.
            assertEquals(1, resultHistory.size,
                "Only the summary SYSTEM message should be in history when lastSummarizedMessageId is null")
            assertEquals(Role.SYSTEM, resultHistory.first().role)
            assertTrue(resultHistory.first().content.contains(existingSummary.content))
        }

        @Test
        @DisplayName("Second compaction updates summary and pointer")
        fun secondCompactionUpdatesSummaryAndPointer() = runTest {
            // Simulate: first compaction already happened (summary with pointer to assistant-10)
            // New messages 11-20 cause a second compaction
            val firstSummary = ChatSummary(
                chatId = ChatId("chat"),
                content = "First summary of messages 1-10.",
                lastSummarizedMessageId = MessageId("assistant-10"),
            )
            val history = makeHistory(20) { i ->
                "Long message $i with enough content to push tokens over the compaction threshold for a small context window"
            }
            val secondCompactedText = "Second summary covering all messages including first summary."
            var savedSummary: ChatSummary? = null
            val compactionPort = mockk<CompactionPort> {
                // Compaction port receives the summary + new messages
                coEvery { compactHistory(any()) } returns secondCompactedText
            }
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns firstSummary
                coEvery { saveChatSummary(any()) } answers {
                    savedSummary = firstArg()
                    Unit
                }
            }
            val service = FakeInferenceServiceForRehydrator()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-20"),
                assistantMessageId = MessageId("assistant-20"),
                service = service,
                contextWindowTokens = 512,
            )

            // Verify the new summary was saved
            assertNotNull(savedSummary, "Second compaction should save a new summary")
            assertEquals(secondCompactedText, savedSummary!!.content)
            // The new pointer should point to the last new message after the first summary pointer
            assertNotNull(savedSummary!!.lastSummarizedMessageId,
                "Second compaction should have a valid lastSummarizedMessageId")
        }
    }

    // ── Default allowLocalSummarization ────────────────────────────────────

    @Nested
    inner class DefaultBehavior {

        @Test
        @DisplayName("allowLocalSummarization defaults to false — no rolling summarization without explicit opt-in")
        fun defaultDisallowsLocalSummarization() = runTest {
            val history = makeHistory(20)
            val compactionPort = mockk<CompactionPort> {
                coEvery { compactHistory(any()) } returns null
            }
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
            }
            val service = FakeInferenceServiceForRehydrator()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-20"),
                assistantMessageId = MessageId("assistant-20"),
                service = service,
                contextWindowTokens = 128,
                // NOT passing allowLocalSummarization — should default to false
            )

            assertFalse(service.promptWasSent,
                "RollingSummarizer should NOT be called when allowLocalSummarization defaults to false")
        }
    }

    // ── Test helper ────────────────────────────────────────────────────────

    private class FakeInferenceServiceForRehydrator : com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort {
        private var history = listOf<ChatMessage>()
        var summaryResponse: String? = null
        var promptWasSent = false

        override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> =
            sendPrompt(prompt, GenerationOptions(reasoningBudget = 0), closeConversation)

        override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> {
            promptWasSent = true
            val response = summaryResponse ?: "Default summary"
            return flowOf(InferenceEvent.PartialResponse(response, ModelType.FAST))
        }

        override suspend fun setHistory(messages: List<ChatMessage>) {
            history = messages
        }

        override suspend fun closeSession() {}

        fun getHistory(): List<ChatMessage> = history
    }
}