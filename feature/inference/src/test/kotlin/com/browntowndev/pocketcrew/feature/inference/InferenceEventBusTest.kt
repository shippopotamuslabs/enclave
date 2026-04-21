package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `observeChatSnapshot replays latest accumulated messages to late observer`() = runTest {
        val snapshot = AccumulatedMessages(
            messages = mapOf(
                MessageId("assistant-id") to MessageSnapshot(
                    messageId = MessageId("assistant-id"),
                    modelType = ModelType.FAST,
                    content = "streamed while viewmodel was gone",
                    thinkingRaw = "",
                )
            )
        )

        bus.emitChatSnapshot(key, snapshot)

        val result = bus.observeChatSnapshot(key).first()

        assertEquals(snapshot, result)
    }
}
