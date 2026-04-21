package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InferenceEventBusTest {

    private val bus = InferenceEventBus()
    private val key = InferenceEventBus.ChatRequestKey(
        chatId = ChatId("chat-id"),
        assistantMessageId = MessageId("assistant-id"),
    )

    @Test
    fun `observeChatRequest replays state emitted before observer subscribes`() = runTest {
        bus.emitChatState(key, MessageGenerationState.Finished(ModelType.FAST))

        val result = bus.observeChatRequest(key).first()

        assertTrue(result is MessageGenerationState.Finished)
    }

    @Test
    fun `openChatRequest starts a fresh stream for an existing key`() = runTest {
        bus.emitChatState(key, MessageGenerationState.Finished(ModelType.FAST))
        val results = mutableListOf<MessageGenerationState>()

        val collectJob = launch {
            bus.openChatRequest(key).toList(results)
        }
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList<MessageGenerationState>(), results)

        bus.emitChatState(key, MessageGenerationState.GeneratingText("fresh", ModelType.FAST))
        testScheduler.advanceUntilIdle()

        assertEquals(1, results.size)
        assertEquals("fresh", (results.single() as MessageGenerationState.GeneratingText).textDelta)
        collectJob.cancel()
    }

    @Test
    fun `clearChatRequest removes stale replay cache`() = runTest {
        bus.emitChatState(key, MessageGenerationState.Finished(ModelType.FAST))
        bus.clearChatRequest(key)
        val results = mutableListOf<MessageGenerationState>()

        val collectJob = launch {
            bus.observeChatRequest(key).toList(results)
        }
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList<MessageGenerationState>(), results)
        collectJob.cancel()
    }

    // --- Pipeline stream tests ---
    @Test
    fun `hasPipelineStream returns false when no stream opened`() {
        assertFalse(bus.hasPipelineStream("no-such-chat"))
    }

    @Test
    fun `hasPipelineStream returns true after openPipelineRequest`() {
        bus.openPipelineRequest("chat1")
        assertTrue(bus.hasPipelineStream("chat1"))
    }

    @Test
    fun `hasPipelineStream returns false after clearPipelineRequest`() {
        bus.openPipelineRequest("chat1")
        bus.clearPipelineRequest("chat1")
        assertFalse(bus.hasPipelineStream("chat1"))
    }

    @Test
    fun `hasChatRequest returns false when no stream opened`() {
        assertFalse(bus.hasChatRequest(key))
    }

    @Test
    fun `hasChatRequest returns true after openChatRequest`() {
        bus.openChatRequest(key)
        assertTrue(bus.hasChatRequest(key))
    }

    @Test
    fun `hasChatRequest returns false after clearChatRequest`() {
        bus.openChatRequest(key)
        bus.clearChatRequest(key)
        assertFalse(bus.hasChatRequest(key))
    }

    @Test
    fun `hasChatRequest returns true when collector cancels before terminal state`() = runTest {
        val collectJob = launch {
            bus.openChatRequest(key).toList(mutableListOf())
        }
        testScheduler.advanceUntilIdle()

        bus.emitChatState(key, MessageGenerationState.GeneratingText("non-terminal", ModelType.FAST))
        testScheduler.advanceUntilIdle()

        collectJob.cancel()
        testScheduler.advanceUntilIdle()

        // Assert: key should be cleared when collector cancels
        assertFalse(bus.hasChatRequest(key), "Key must be cleared when collector cancels before terminal state")
    }
}
