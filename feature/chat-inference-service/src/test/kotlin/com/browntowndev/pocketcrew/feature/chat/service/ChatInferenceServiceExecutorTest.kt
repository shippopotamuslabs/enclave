package com.browntowndev.pocketcrew.feature.chat.service

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.feature.inference.InferenceEventBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatInferenceServiceExecutorTest {

    private val serviceStarter = mockk<ChatInferenceServiceStarter>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val inferenceEventBus = InferenceEventBus()
    private val executor = ChatInferenceServiceExecutor(serviceStarter, loggingPort, inferenceEventBus)

    private val chatId = ChatId("test-chat")
    private val assistantMessageId = MessageId("a1")
    private val requestKey = InferenceEventBus.ChatRequestKey(chatId, assistantMessageId)

    @Test
    fun `execute receives terminal state emitted synchronously while service starts`() = runTest {
        every {
            serviceStarter.startService(any(), any(), any(), any(), any(), any())
        } answers {
            inferenceEventBus.tryEmitChatState(requestKey, MessageGenerationState.Finished(ModelType.FAST))
            Unit
        }
        val results = mutableListOf<MessageGenerationState>()

        val collectJob = launch {
            executor.execute(
                prompt = "hello",
                userMessageId = MessageId("u1"),
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = false,
                modelType = ModelType.FAST,
                backgroundInferenceEnabled = true,
            ).toList(results)
        }

        testScheduler.advanceUntilIdle()

        assertTrue(collectJob.isCompleted, "Flow should complete after synchronously emitted Finished state")
        assertEquals(1, results.size)
        assertTrue(results.single() is MessageGenerationState.Finished)
    }

    @Test
    fun `execute calls serviceStarter startService`() = runTest {
        val collectJob = launch {
            executor.execute(
                prompt = "hello",
                userMessageId = MessageId("u1"),
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = false,
                modelType = ModelType.FAST,
                backgroundInferenceEnabled = true,
            ).collect {}
        }

        testScheduler.advanceUntilIdle()

        verify { serviceStarter.startService(any(), any(), any(), any(), any(), any()) }

        collectJob.cancel()
    }

    @Test
    fun `execute flow completes after Finished state`() = runTest {
        val results = mutableListOf<MessageGenerationState>()
        val collectJob = launch {
            executor.execute(
                prompt = "hello",
                userMessageId = MessageId("u1"),
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = false,
                modelType = ModelType.FAST,
                backgroundInferenceEnabled = true,
            ).toList(results)
        }

        testScheduler.advanceUntilIdle()

        inferenceEventBus.emitChatState(requestKey, MessageGenerationState.Processing(ModelType.FAST))
        inferenceEventBus.emitChatState(requestKey, MessageGenerationState.GeneratingText("Hello!", ModelType.FAST))
        inferenceEventBus.emitChatState(requestKey, MessageGenerationState.Finished(ModelType.FAST))

        testScheduler.advanceUntilIdle()

        // The collectJob should have completed because transformWhile ends after Finished
        assertTrue(collectJob.isCompleted, "Flow should complete after Finished state")
        assertEquals(3, results.size)
        assertTrue(results[0] is MessageGenerationState.Processing)
        assertTrue(results[1] is MessageGenerationState.GeneratingText)
        assertTrue(results[2] is MessageGenerationState.Finished)

        verify { serviceStarter.startService(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `execute flow completes after Failed state`() = runTest {
        val results = mutableListOf<MessageGenerationState>()
        val collectJob = launch {
            executor.execute(
                prompt = "hello",
                userMessageId = MessageId("u1"),
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = false,
                modelType = ModelType.FAST,
                backgroundInferenceEnabled = true,
            ).toList(results)
        }

        testScheduler.advanceUntilIdle()

        inferenceEventBus.emitChatState(requestKey, MessageGenerationState.Processing(ModelType.FAST))
        inferenceEventBus.emitChatState(requestKey, MessageGenerationState.Failed(RuntimeException("test"), ModelType.FAST))

        testScheduler.advanceUntilIdle()

        assertTrue(collectJob.isCompleted, "Flow should complete after Failed state")
        assertEquals(2, results.size)
        assertTrue(results[0] is MessageGenerationState.Processing)
        assertTrue(results[1] is MessageGenerationState.Failed)
    }

    @Test
    fun `execute flow completes after Blocked state`() = runTest {
        val results = mutableListOf<MessageGenerationState>()
        val collectJob = launch {
            executor.execute(
                prompt = "hello",
                userMessageId = MessageId("u1"),
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = false,
                modelType = ModelType.FAST,
                backgroundInferenceEnabled = true,
            ).toList(results)
        }

        testScheduler.advanceUntilIdle()

        inferenceEventBus.emitChatState(requestKey, MessageGenerationState.Blocked("unsafe", ModelType.FAST))

        testScheduler.advanceUntilIdle()

        assertTrue(collectJob.isCompleted, "Flow should complete after Blocked state")
        assertEquals(1, results.size)
        assertTrue(results[0] is MessageGenerationState.Blocked)
    }

    @Test
    fun `execute filters states by chatId`() = runTest {
        val otherChatId = ChatId("other-chat")
        val results = mutableListOf<MessageGenerationState>()

        val collectJob = launch {
            executor.execute(
                prompt = "hello",
                userMessageId = MessageId("u1"),
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = false,
                modelType = ModelType.FAST,
                backgroundInferenceEnabled = true,
            ).toList(results)
        }

        testScheduler.advanceUntilIdle()

        inferenceEventBus.emitChatState(
            InferenceEventBus.ChatRequestKey(otherChatId, assistantMessageId),
            MessageGenerationState.GeneratingText("Other", ModelType.FAST),
        )
        inferenceEventBus.emitChatState(requestKey, MessageGenerationState.GeneratingText("Ours", ModelType.FAST))
        inferenceEventBus.emitChatState(requestKey, MessageGenerationState.Finished(ModelType.FAST))

        testScheduler.advanceUntilIdle()

        assertTrue(collectJob.isCompleted, "Flow should complete after Finished")
        assertEquals(2, results.size, "Should only contain states for our chatId")
        assertTrue(results[0] is MessageGenerationState.GeneratingText)
        assertTrue(results[1] is MessageGenerationState.Finished)

        verify { serviceStarter.startService(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `execute filters states by assistantMessageId in same chat`() = runTest {
        val otherAssistantMessageId = MessageId("a2")
        val results = mutableListOf<MessageGenerationState>()

        val collectJob = launch {
            executor.execute(
                prompt = "hello",
                userMessageId = MessageId("u1"),
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = false,
                modelType = ModelType.FAST,
                backgroundInferenceEnabled = true,
            ).toList(results)
        }

        testScheduler.advanceUntilIdle()

        inferenceEventBus.emitChatState(
            InferenceEventBus.ChatRequestKey(chatId, otherAssistantMessageId),
            MessageGenerationState.Finished(ModelType.FAST),
        )
        inferenceEventBus.emitChatState(requestKey, MessageGenerationState.GeneratingText("Current", ModelType.FAST))
        inferenceEventBus.emitChatState(requestKey, MessageGenerationState.Finished(ModelType.FAST))

        testScheduler.advanceUntilIdle()

        assertTrue(collectJob.isCompleted, "Flow should complete after current request finishes")
        assertEquals(2, results.size)
        assertEquals("Current", (results[0] as MessageGenerationState.GeneratingText).textDelta)
        assertTrue(results[1] is MessageGenerationState.Finished)
    }

    @Test
    fun `execute clears stream on service start exception`() = runTest {
        every {
            serviceStarter.startService(any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("foreground service start not allowed")

        val results = mutableListOf<MessageGenerationState>()
        val collectJob = launch {
            executor.execute(
                prompt = "hello",
                userMessageId = MessageId("u1"),
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = false,
                modelType = ModelType.FAST,
                backgroundInferenceEnabled = true,
            ).toList(results)
        }

        testScheduler.advanceUntilIdle()

        assertTrue(collectJob.isCompleted)
        assertEquals(1, results.size)
        assertTrue(results.single() is MessageGenerationState.Failed)
        assertFalse(inferenceEventBus.hasChatRequest(requestKey), "Stream must be cleared after exception")
    }

    @Test
    fun `execute clears stream on generic service start exception`() = runTest {
        every {
            serviceStarter.startService(any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("boom")

        val results = mutableListOf<MessageGenerationState>()
        val collectJob = launch {
            executor.execute(
                prompt = "hello",
                userMessageId = MessageId("u1"),
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = false,
                modelType = ModelType.FAST,
                backgroundInferenceEnabled = true,
            ).toList(results)
        }

        testScheduler.advanceUntilIdle()

        assertTrue(collectJob.isCompleted)
        assertEquals(1, results.size)
        assertTrue(results.single() is MessageGenerationState.Failed)
        assertFalse(inferenceEventBus.hasChatRequest(requestKey), "Stream must be cleared after exception")
    }

    @Test
    fun `execute clears stream on collector cancellation before terminal state`() = runTest {
        val results = mutableListOf<MessageGenerationState>()
        val collectJob = launch {
            executor.execute(
                prompt = "hello",
                userMessageId = MessageId("u1"),
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = false,
                modelType = ModelType.FAST,
                backgroundInferenceEnabled = true,
            ).toList(results)
        }

        testScheduler.advanceUntilIdle()

        // Emit non-terminal states only
        inferenceEventBus.emitChatState(requestKey, MessageGenerationState.Processing(ModelType.FAST))

        testScheduler.advanceUntilIdle()

        // Cancel collector before terminal state
        collectJob.cancel()
        testScheduler.advanceUntilIdle()

        assertFalse(inferenceEventBus.hasChatRequest(requestKey), "Stream must be cleared after collector cancellation")
    }

    @Test
    fun `execute ignores backgroundInferenceEnabled parameter`() = runTest {
        every {
            serviceStarter.startService(any(), any(), any(), any(), any(), any())
        } answers {
            inferenceEventBus.tryEmitChatState(requestKey, MessageGenerationState.Finished(ModelType.FAST))
            Unit
        }

        val collectJob = launch {
            executor.execute(
                prompt = "hello",
                userMessageId = MessageId("u1"),
                assistantMessageId = assistantMessageId,
                chatId = chatId,
                userHasImage = false,
                modelType = ModelType.FAST,
                backgroundInferenceEnabled = false, // Irrelevant for service executor
            ).collect {}
        }

        testScheduler.advanceUntilIdle()

        verify { serviceStarter.startService(any(), any(), any(), any(), any(), any()) }
        collectJob.cancel()
    }

    @Test
    fun `stop calls serviceStarter stopInference`() {
        executor.stop()
        verify { serviceStarter.stopInference() }
    }
}
