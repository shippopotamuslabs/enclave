package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.TavilySource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveChatTurnStoreTest {
    private val key = ActiveChatTurnKey(
        chatId = ChatId("chat"),
        assistantMessageId = MessageId("assistant"),
    )

    @Test
    fun `publish then observe replays latest snapshot to late observer`() = runTest {
        val store = ActiveChatTurnStore()
        val snapshot = accumulated("streaming answer")

        store.publish(key, snapshot)

        assertEquals(snapshot, store.observe(key).first())
    }

    @Test
    fun `publish replaces current snapshot without content heuristics`() = runTest {
        val store = ActiveChatTurnStore()

        store.publish(key, accumulated("longer answer"))
        store.publish(key, accumulated("short"))

        assertEquals("short", store.snapshotValue(key)?.messages?.get(MessageId("assistant"))?.content)
    }

    @Test
    fun `terminal publish replaces current snapshot without hybridizing content and state`() = runTest {
        val store = ActiveChatTurnStore()

        store.publish(key, accumulated("longer partial answer", MessageState.GENERATING))
        store.publish(key, accumulated("short", MessageState.COMPLETE))

        val snapshot = store.snapshotValue(key)?.messages?.get(MessageId("assistant"))

        assertEquals("short", snapshot?.content)
        assertEquals(MessageState.COMPLETE, snapshot?.messageState)
    }

    @Test
    fun `richer snapshot after premature complete replaces truncated complete content`() = runTest {
        val store = ActiveChatTurnStore()

        store.publish(key, accumulated("want de", MessageState.GENERATING))
        store.publish(key, accumulated("want de", MessageState.COMPLETE))
        store.publish(key, accumulated("want deets on grok-3 benchmarks", MessageState.COMPLETE))

        val snapshot = store.snapshotValue(key)?.messages?.get(MessageId("assistant"))

        assertEquals("want deets on grok-3 benchmarks", snapshot?.content)
        assertEquals(MessageState.COMPLETE, snapshot?.messageState)
    }

    @Test
    fun `same length non-prefix snapshot replaces current snapshot`() = runTest {
        val store = ActiveChatTurnStore()

        store.publish(key, accumulated("abcde"))
        store.publish(key, accumulated("vwxyz"))

        assertEquals("vwxyz", store.snapshotValue(key)?.messages?.get(MessageId("assistant"))?.content)
    }

    @Test
    fun `equal length snapshot with additional source metadata is accepted`() = runTest {
        val store = ActiveChatTurnStore()
        val source = TavilySource(
            messageId = MessageId("assistant"),
            title = "Source",
            url = "https://example.com",
            content = "content",
        )

        store.publish(key, accumulated("answer"))
        store.publish(key, accumulated("answer", sources = listOf(source)))

        assertEquals(
            listOf(source),
            store.snapshotValue(key)?.messages?.get(MessageId("assistant"))?.tavilySources,
        )
    }

    @Test
    fun `markSourcesExtracted updates active snapshot explicitly`() = runTest {
        val store = ActiveChatTurnStore()
        val source = TavilySource(
            messageId = MessageId("assistant"),
            title = "Source",
            url = "https://example.com",
            content = "content",
        )

        store.publish(key, accumulated("answer", sources = listOf(source)))
        store.markSourcesExtracted(key, listOf("https://example.com"))

        val updatedSource = store.snapshotValue(key)
            ?.messages
            ?.get(MessageId("assistant"))
            ?.tavilySources
            ?.single()
        assertEquals("answer", store.snapshotValue(key)?.messages?.get(MessageId("assistant"))?.content)
        assertEquals(true, updatedSource?.extracted)
    }

    @Test
    fun `acknowledgeHandoff emits null and removes snapshot`() = runTest {
        val store = ActiveChatTurnStore()
        val observed = mutableListOf<AccumulatedMessages?>()
        val collectJob = launch {
            store.observe(key).collect { snapshot -> observed.add(snapshot) }
        }

        store.publish(key, accumulated("answer"))
        runCurrent()
        store.acknowledgeHandoff(key)
        runCurrent()

        assertEquals(null, observed.last())
        assertNull(store.snapshotValue(key))
        collectJob.cancel()
    }

    @Test
    fun `clear removes snapshot`() = runTest {
        val store = ActiveChatTurnStore()

        store.publish(key, accumulated("answer"))
        store.clear(key)

        assertNull(store.snapshotValue(key))
    }

    private fun accumulated(
        content: String,
        state: MessageState = MessageState.GENERATING,
        sources: List<TavilySource> = emptyList(),
    ): AccumulatedMessages {
        return AccumulatedMessages(
            messages = mapOf(
                MessageId("assistant") to MessageSnapshot(
                    messageId = MessageId("assistant"),
                    modelType = ModelType.FAST,
                    content = content,
                    thinkingRaw = "",
                    messageState = state,
                    tavilySources = sources,
                )
            )
        )
    }
}
