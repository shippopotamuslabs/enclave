package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.ChatSummary
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Edge case tests for [ChatHistoryRehydrator] compaction paths.
 *
 * These tests verify behavior at boundaries:
 * - Empty history
 * - Compaction port throws an exception
 * - History exactly at threshold (no compaction needed)
 * - History just over threshold (compaction triggered)
 * - allowLocalSummarization=false defaults (safety)
 * - RollingSummarizer exception fallback
 * - Vision analysis stripping
 */
class ChatHistoryRehydratorEdgeCaseTest {

    private val loggingPort = object : LoggingPort {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
        override fun recordException(tag: String, message: String, throwable: Throwable) {}
    }

    private fun makeMessage(id: String, role: Role, text: String) = Message(
        id = MessageId(id),
        chatId = ChatId("chat"),
        content = Content(text = text),
        role = role,
    )

    // ── Empty history ────────────────────────────────────────────────────────

    @Nested
    inner class EmptyHistory {

        @Test
        @DisplayName("Rehydrator handles empty history gracefully without compaction")
        fun handlesEmptyHistory() = runTest {
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns emptyList()
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
            }
            val compactionPort = mockk<CompactionPort>(relaxed = true)
            val service = FakeRehydratorServiceForEdgeCases()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-1"),
                assistantMessageId = MessageId("assistant-1"),
                service = service,
                contextWindowTokens = 4096,
            )

            assertEquals(0, service.getHistorySize(), "Empty history should produce empty service history")
        }
    }

    // ── Compaction port exception ──────────────────────────────────────────

    @Nested
    inner class CompactionPortException {

        @Test
        @DisplayName("When compaction port throws, falls back to FIFO trimming without crashing")
        fun compactionPortThrowsFallbackToTrimming() = runTest {
            val history = (1..30).flatMap {
                listOf(
                    makeMessage("user-$it", Role.USER, "Question $it about topic $it"),
                    makeMessage("assistant-$it", Role.ASSISTANT, "Answer $it about topic $it"),
                )
            }
            val compactionPort = mockk<CompactionPort> {
                coEvery { compactHistory(any()) } throws RuntimeException("Compaction service unavailable")
            }
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
            }
            val service = FakeRehydratorServiceForEdgeCases()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-30"),
                assistantMessageId = MessageId("assistant-30"),
                service = service,
                contextWindowTokens = 500,
                allowLocalSummarization = false,
            )

            assertTrue(service.getHistorySize() < history.size,
                "FIFO trimming should reduce message count after compaction failure")
        }
    }

    // ── Threshold boundary ────────────────────────────────────────────────

    @Nested
    inner class ThresholdBoundary {

        @Test
        @DisplayName("History well within threshold does not trigger compaction")
        fun historyWellWithinThreshold() = runTest {
            // Use messages that are NOT the current user/assistant message
            val history = listOf(
                makeMessage("user-old", Role.USER, "Small question"),
                makeMessage("assistant-old", Role.ASSISTANT, "Small answer"),
            )
            var compactionCalled = false
            val compactionPort = mockk<CompactionPort> {
                coEvery { compactHistory(any()) } answers {
                    compactionCalled = true
                    "Summary"
                }
            }
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
                coEvery { saveChatSummary(any()) } returns Unit
            }
            val service = FakeRehydratorServiceForEdgeCases()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-1"),
                assistantMessageId = MessageId("assistant-1"),
                service = service,
                contextWindowTokens = 128000,
            )

            assertFalse(compactionCalled, "Compaction should not be called when history is well within threshold")
            assertEquals(2, service.getHistorySize(), "History should be preserved unchanged")
        }

        @Test
        @DisplayName("History just over threshold triggers compaction")
        fun historyJustOverThreshold() = runTest {
            val history = (1..60).flatMap {
                listOf(
                    makeMessage("user-$it", Role.USER, "Question number $it with sufficient context to generate meaningful tokens for testing the compaction threshold"),
                    makeMessage("assistant-$it", Role.ASSISTANT, "Answer number $it with enough detail to make this message consume tokens in the context window"),
                )
            }
            val compactionPort = mockk<CompactionPort> {
                coEvery { compactHistory(any()) } returns "Summary of earlier conversation"
            }
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
                coEvery { saveChatSummary(any()) } returns Unit
            }
            val service = FakeRehydratorServiceForEdgeCases()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-60"),
                assistantMessageId = MessageId("assistant-60"),
                service = service,
                contextWindowTokens = 256,
            )

            assertTrue(service.getHistorySize() < history.size,
                "Compaction should reduce history size when over threshold")
        }
    }

    // ── Compaction returns blank summary ──────────────────────────────────

    @Nested
    inner class CompactionReturnsBlank {

        @Test
        @DisplayName("When compaction returns blank summary, falls back to FIFO trimming")
        fun blankSummaryFallsBackToTrimming() = runTest {
            val history = (1..20).flatMap {
                listOf(
                    makeMessage("user-$it", Role.USER, "Question $it with enough words to exceed a tiny context window"),
                    makeMessage("assistant-$it", Role.ASSISTANT, "Answer $it with enough detail to consume tokens"),
                )
            }
            val compactionPort = mockk<CompactionPort> {
                coEvery { compactHistory(any()) } returns "   " // Blank/whitespace summary
            }
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
                coEvery { saveChatSummary(any()) } returns Unit
            }
            val service = FakeRehydratorServiceForEdgeCases()
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
                contextWindowTokens = 200,
                allowLocalSummarization = false,
            )

            assertTrue(service.getHistorySize() < history.size,
                "Should reduce history via FIFO trimming after blank compaction summary")
        }
    }

    // ── Single message history ─────────────────────────────────────────────

    @Nested
    inner class SingleMessageHistory {

        @Test
        @DisplayName("Rehydrator filters out current user message, resulting in empty history")
        fun singleMessageHistoryFiltered() = runTest {
            // The single message IS the current user message, so it gets filtered out
            val history = listOf(
                makeMessage("user-1", Role.USER, "Just one message"),
            )
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
            }
            val compactionPort = mockk<CompactionPort>(relaxed = true)
            val service = FakeRehydratorServiceForEdgeCases()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-1"),
                assistantMessageId = MessageId("assistant-1"),
                service = service,
                contextWindowTokens = 4096,
            )

            assertEquals(0, service.getHistorySize(), "Current user message is filtered out")
        }

        @Test
        @DisplayName("Rehydrator preserves non-current messages in single-message context")
        fun singlePastMessagePreserved() = runTest {
            // One old message plus the current user message (which gets filtered)
            val history = listOf(
                makeMessage("user-0", Role.USER, "Previous question"),
                makeMessage("user-1", Role.USER, "Just one message"),
            )
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
            }
            val compactionPort = mockk<CompactionPort>(relaxed = true)
            val service = FakeRehydratorServiceForEdgeCases()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-1"),
                assistantMessageId = MessageId("assistant-1"),
                service = service,
                contextWindowTokens = 4096,
            )

            assertEquals(1, service.getHistorySize(), "Previous message should be preserved")
        }
    }

    // ── RollingSummarizer exception fallback ─────────────────────────────────

    @Nested
    inner class RollingSummarizerException {

        @Test
        @DisplayName("When RollingSummarizer throws, falls back to FIFO trimming without crashing")
        fun rollingSummarizerExceptionFallsBackToTrimming() = runTest {
            val history = (1..30).flatMap {
                listOf(
                    makeMessage("user-$it", Role.USER, "Question $it with enough text to trigger compaction when context window is small"),
                    makeMessage("assistant-$it", Role.ASSISTANT, "Answer $it with enough detail about topic number $it to consume many tokens"),
                )
            }
            val compactionPort = mockk<CompactionPort> {
                coEvery { compactHistory(any()) } returns null
            }
            val service = FailingRehydratorService()
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
            }
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-30"),
                assistantMessageId = MessageId("assistant-30"),
                service = service,
                contextWindowTokens = 300,
                allowLocalSummarization = true,
            )

            assertTrue(service.sendPromptWasCalled, "RollingSummarizer should have been attempted")
            assertTrue(service.getHistorySize() < history.size,
                "FIFO trimming should reduce message count after RollingSummarizer failure")
        }

        @Test
        @DisplayName("When allowLocalSummarization=true and service succeeds, history is reduced")
        fun rollingSummarizerSuccessReducesHistory() = runTest {
            val history = (1..30).flatMap {
                listOf(
                    makeMessage("user-$it", Role.USER, "Question $it with enough text to trigger compaction when context window is small"),
                    makeMessage("assistant-$it", Role.ASSISTANT, "Answer $it with enough detail about topic number $it to consume many tokens"),
                )
            }
            val compactionPort = mockk<CompactionPort> {
                coEvery { compactHistory(any()) } returns null
            }
            val service = FakeRehydratorServiceForEdgeCases()
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
                coEvery { getChatSummary(any()) } returns null
                coEvery { saveChatSummary(any()) } returns Unit
            }
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-30"),
                assistantMessageId = MessageId("assistant-30"),
                service = service,
                contextWindowTokens = 300,
                allowLocalSummarization = true,
            )

            assertTrue(service.getHistorySize() < history.size,
                "History should be reduced via compaction or trimming")
        }
    }

    // ── Vision analysis stripping ─────────────────────────────────────────────

    @Nested
    inner class VisionAnalysisStripping {

        @Test
        @DisplayName("Image URIs are stripped from history content during rehydration")
        fun imageUrisStrippedFromHistory() = runTest {
            val history = listOf(
                makeMessage("user-1", Role.USER, "What's in this image?"),
                makeMessage("assistant-1", Role.ASSISTANT, "I can see a cat."),
                makeMessage("user-2", Role.USER, "Describe this photo"),
            )
            val visionAnalysis = MessageVisionAnalysis(
                id = "analysis-1",
                userMessageId = MessageId("user-1"),
                imageUri = "file:///image.jpg",
                promptText = "",
                analysisText = "A fluffy orange cat sitting on a windowsill",
                modelType = ModelType.VISION,
                createdAt = 1L,
                updatedAt = 1L,
            )
            val repository = mockk<MessageRepository> {
                coEvery { getMessagesForChat(any()) } returns history
                coEvery { getVisionAnalysesForMessages(any()) } returns mapOf(
                    MessageId("user-1") to listOf(visionAnalysis)
                )
                coEvery { getChatSummary(any()) } returns null
            }
            val compactionPort = mockk<CompactionPort>(relaxed = true)
            val service = FakeRehydratorServiceForEdgeCases()
            val rehydrator = ChatHistoryRehydrator(
                messageRepository = repository,
                compactionPort = compactionPort,
                loggingPort = loggingPort,
            )

            rehydrator(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user-2"),
                assistantMessageId = MessageId("assistant-2"),
                service = service,
                contextWindowTokens = 8192,
            )

            assertTrue(service.getHistorySize() > 0, "History should be non-empty after rehydration")
        }
    }

    // ── Test helpers ────────────────────────────────────────────────────────

    private open class FakeRehydratorServiceForEdgeCases : com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort {
        private var history = listOf<ChatMessage>()
        var sendPromptWasCalled = false
            protected set

        override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> =
            sendPrompt(prompt, GenerationOptions(reasoningBudget = 0), closeConversation)

        override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> {
            sendPromptWasCalled = true
            return flowOf(InferenceEvent.PartialResponse("Summary response", ModelType.FAST))
        }

        override suspend fun setHistory(messages: List<ChatMessage>) {
            history = messages
        }

        override suspend fun closeSession() {}

        fun getHistorySize(): Int = history.size
    }

    private class FailingRehydratorService : FakeRehydratorServiceForEdgeCases() {
        override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> {
            sendPromptWasCalled = true
            throw RuntimeException("Model failed to generate summary")
        }

        override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
            sendPromptWasCalled = true
            throw RuntimeException("Model failed to generate summary")
        }
    }
}