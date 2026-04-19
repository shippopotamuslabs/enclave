package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.util.JTokkitTokenCounter
import com.browntowndev.pocketcrew.domain.util.TokenCounter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [RollingSummarizer] — the fallback summarization strategy used when
 * context budget is exceeded and shouldSummarize is true.
 *
 * Uses [FakeInferenceServiceForRollingSummarizer] to control inference responses.
 */
class RollingSummarizerTest {

    private val loggingPort = object : LoggingPort {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
        override fun recordException(tag: String, message: String, throwable: Throwable) {}
    }

    @Nested
    inner class MinimumMessageCount {

        @Test
        @DisplayName("summarize returns null when fewer than 4 messages")
        fun returnsNullWhenFewerThan4Messages() = runTest {
            val service = FakeInferenceServiceForRollingSummarizer()
            val summarizer = RollingSummarizer(loggingPort, JTokkitTokenCounter)

            val result = summarizer.summarize(
                messages = listOf(
                    ChatMessage(Role.USER, "Hello"),
                    ChatMessage(Role.ASSISTANT, "Hi"),
                ),
                service = service,
            )

            assertNull(result, "Should return null for fewer than 4 messages")
        }

        @Test
        @DisplayName("summarize returns null when exactly 3 messages")
        fun returnsNullFor3Messages() = runTest {
            val service = FakeInferenceServiceForRollingSummarizer()
            val summarizer = RollingSummarizer(loggingPort, JTokkitTokenCounter)

            val result = summarizer.summarize(
                messages = listOf(
                    ChatMessage(Role.USER, "Hello"),
                    ChatMessage(Role.ASSISTANT, "Hi there"),
                    ChatMessage(Role.USER, "How are you?"),
                ),
                service = service,
            )

            assertNull(result, "Should return null for 3 messages (need at least 4)")
        }
    }

    @Nested
    inner class SummarizationBehavior {

        @Test
        @DisplayName("summarize sends prompt to service and returns summary text")
        fun returnsSummaryFromService() = runTest {
            val service = FakeInferenceServiceForRollingSummarizer()
            service.emitEvents(listOf(
                InferenceEvent.PartialResponse("Summary of the conversation.", ModelType.FAST),
            ))
            val summarizer = RollingSummarizer(loggingPort, JTokkitTokenCounter)

            val messages = listOf(
                ChatMessage(Role.USER, "What is Kotlin?"),
                ChatMessage(Role.ASSISTANT, "Kotlin is a modern programming language."),
                ChatMessage(Role.USER, "Tell me more about coroutines."),
                ChatMessage(Role.ASSISTANT, "Coroutines are lightweight threads for async work."),
            )

            val result = summarizer.summarize(messages, service)

            assertNotNull(result, "Summarize should return a non-null summary")
            assertTrue(result!!.contains("Summary"), "Summary should contain the response text")
            assertTrue(service.getPromptsSent().isNotEmpty(), "Service should have received a prompt")
        }

        @Test
        @DisplayName("summarize includes system prompt in messages but excludes it from the slice")
        fun excludesSystemPromptFromSlice() = runTest {
            val service = FakeInferenceServiceForRollingSummarizer()
            service.emitEvents(listOf(
                InferenceEvent.PartialResponse("A brief summary.", ModelType.FAST),
            ))
            val summarizer = RollingSummarizer(loggingPort, JTokkitTokenCounter)

            val messages = listOf(
                ChatMessage(Role.SYSTEM, "You are a helpful assistant."),
                ChatMessage(Role.USER, "Question 1"),
                ChatMessage(Role.ASSISTANT, "Answer 1"),
                ChatMessage(Role.USER, "Question 2"),
                ChatMessage(Role.ASSISTANT, "Answer 2"),
            )

            val result = summarizer.summarize(messages, service)

            assertNotNull(result)
            val promptSent = service.getPromptsSent().first()
            // System prompt should be filtered out of the history that gets summarized
            assertFalse(promptSent.contains("You are a helpful assistant."),
                "System prompt should be excluded from the summarization prompt text"
            )
        }

        @Test
        @DisplayName("summarize returns null when service throws an exception")
        fun returnsNullOnServiceException() = runTest {
            val service = FailingInferenceServiceForRollingSummarizer()
            val summarizer = RollingSummarizer(loggingPort, JTokkitTokenCounter)

            val messages = listOf(
                ChatMessage(Role.USER, "Q1"),
                ChatMessage(Role.ASSISTANT, "A1"),
                ChatMessage(Role.USER, "Q2"),
                ChatMessage(Role.ASSISTANT, "A2"),
            )

            val result = summarizer.summarize(messages, service)

            assertNull(result, "Should return null when service throws")
        }
    }

    @Nested
    inner class TokenSplitting {

        @Test
        @DisplayName("summarize slices approximately 50% of history tokens for summarization")
        fun slicesApproximately50Percent() = runTest {
            val service = FakeInferenceServiceForRollingSummarizer()
            service.emitEvents(listOf(
                InferenceEvent.PartialResponse("Summary result.", ModelType.FAST),
            ))
            val tokenCounter = JTokkitTokenCounter
            val summarizer = RollingSummarizer(loggingPort, tokenCounter)

            // Create messages where the first half has more tokens than the second
            val messages = listOf(
                ChatMessage(Role.USER, "This is a very long question about topic alpha with many words."),
                ChatMessage(Role.ASSISTANT, "This is a very long answer about topic alpha with many details and information."),
                ChatMessage(Role.USER, "Short question."),
                ChatMessage(Role.ASSISTANT, "Short answer."),
            )

            val result = summarizer.summarize(messages, service)

            assertNotNull(result)
            // The prompt should contain the first two messages (longer ones) in the slice
            val prompt = service.getPromptsSent().first()
            assertTrue(prompt.contains("topic alpha"), "Prompt should include the first longer messages")
        }
    }

    // ── Fake that exposes sent prompts ─────────────────────────────────────

    private open class FakeInferenceServiceForRollingSummarizer : com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort {
        private val promptsSent = mutableListOf<String>()
        private var eventsToEmit: List<InferenceEvent> = emptyList()

        fun emitEvents(events: List<InferenceEvent>) {
            eventsToEmit = events
        }

        fun getPromptsSent(): List<String> = promptsSent.toList()

        override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
            promptsSent.add(prompt)
            return flowOf(*eventsToEmit.toTypedArray())
        }

        override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> {
            promptsSent.add(prompt)
            return flowOf(*eventsToEmit.toTypedArray())
        }

        override suspend fun setHistory(messages: List<ChatMessage>) {}
        override suspend fun closeSession() {}
    }

    private class FailingInferenceServiceForRollingSummarizer : FakeInferenceServiceForRollingSummarizer() {
        override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
            throw RuntimeException("Service unavailable")
        }

        override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> {
            throw RuntimeException("Service unavailable")
        }
    }
}