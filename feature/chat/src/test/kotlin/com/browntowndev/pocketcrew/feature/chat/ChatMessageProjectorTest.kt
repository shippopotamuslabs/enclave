package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * TDD tests for [ChatMessageProjector].
 *
 * RED before Phase 2 (class doesn't exist / no caching).
 * GREEN after converting projectChatMessages to a stateful projector with per-message cache.
 */
class ChatMessageProjectorTest {

    private val chatId = ChatId("chat")
    private val projector = ChatMessageProjector()

    // ---------------------------------------------------------------------------
    // Memo test — identical inputs → same result list reference
    // ---------------------------------------------------------------------------

    /**
     * Without caching: each call to projectChatMessages() returns a new List via sortedBy().
     * With ChatMessageProjector: identical inputs return the cached result reference.
     *
     * RED: assertSame fails because projectChatMessages returns a new list each time.
     * GREEN: projector returns the cached result when all inputs are equal.
     */
    @Test
    fun `project returns referentially equal result when called twice with identical inputs`() {
        val dbMessages = (1..5).map { i -> dbMessage(MessageId("msg-$i"), createdAt = i.toLong()) }

        val first = projector.project(dbMessages, null, null)
        val second = projector.project(dbMessages, null, null)

        assertSame(
            first.messages,
            second.messages,
            "Same inputs must return the same list reference — not a freshly allocated sortedBy() copy",
        )
    }

    /**
     * Changing a snapshot triggers a new result (cache miss at list level), but
     * same-inputs between two consecutive calls with the SAME snapshot also memoises.
     */
    @Test
    fun `project returns referentially equal result when snapshot is the same reference`() {
        val msgId = MessageId("msg-1")
        val dbMessages = listOf(dbMessage(msgId, state = MessageState.PROCESSING))
        val snapshot = makeSnapshot(msgId, "partial")
        val key = ActiveChatTurnKey(chatId, msgId)

        val first = projector.project(dbMessages, snapshot, key)
        val second = projector.project(dbMessages, snapshot, key)

        assertSame(first.messages, second.messages)
    }

    // ---------------------------------------------------------------------------
    // Incremental test — only changed messages are reallocated
    // ---------------------------------------------------------------------------

    /**
     * 50 messages all have an active generating snapshot (so every projected Message
     * comes from MessageSnapshot.toMessage(), not the DB reference directly).
     *
     * On the second projection only the last snapshot changes.
     *
     * Without per-message cache: ALL 50 Messages are newly allocated on each call.
     * With cache: first 49 keep the same Message reference; only index 49 is new.
     *
     * RED: assertSame on index 0–48 fails because toMessage() always allocates a new object.
     * GREEN: projector reuses the prior Message for unchanged (dbMessage, snapshot) pairs.
     */
    @Test
    fun `project reuses Message instances for unchanged db-message plus snapshot pairs`() {
        val msgIds = (1..50).map { i -> MessageId("msg-$i") }
        val dbMessages = msgIds.mapIndexed { i, id ->
            dbMessage(id, createdAt = i.toLong(), state = MessageState.PROCESSING)
        }
        val lastId = msgIds.last()
        val key = ActiveChatTurnKey(chatId, lastId)

        // All 50 messages have a GENERATING snapshot so toMessage() is called for each.
        val snapshot1 = AccumulatedMessages(
            messages = msgIds.associateWith { id ->
                generatingSnapshot(id, content = "initial")
            },
        )
        val first = projector.project(dbMessages, snapshot1, key)

        // Only change the last snapshot's content.
        val snapshot2 = AccumulatedMessages(
            messages = msgIds.associateWith { id ->
                generatingSnapshot(id, content = if (id == lastId) "new token" else "initial")
            },
        )
        val second = projector.project(dbMessages, snapshot2, key)

        for (i in 0 until 49) {
            assertSame(
                first.messages[i],
                second.messages[i],
                "Message at index $i should be the same reference (snapshot unchanged)",
            )
        }
        assertNotSame(
            first.messages[49],
            second.messages[49],
            "Last message must be a new instance (snapshot changed)",
        )
    }

    // ---------------------------------------------------------------------------
    // Ordering invariant — sort is preserved regardless of cache state
    // ---------------------------------------------------------------------------

    /**
     * Out-of-order createdAt values must still produce a chronologically sorted list.
     * Tests that the cache never returns results in insertion order when createdAt differs.
     */
    @Test
    fun `project produces chronologically sorted output for out-of-order db messages`() {
        val messages = listOf(
            dbMessage(MessageId("msg-c"), createdAt = 300L),
            dbMessage(MessageId("msg-a"), createdAt = 100L),
            dbMessage(MessageId("msg-b"), createdAt = 200L),
        )

        val result = projector.project(messages, null, null)

        assertEquals(
            listOf(MessageId("msg-a"), MessageId("msg-b"), MessageId("msg-c")),
            result.messages.map { it.id },
        )
    }

    /**
     * Sort order is still correct after a cache hit (memo path must not skip sorting).
     */
    @Test
    fun `project preserves sort order on cache-hit result`() {
        val messages = listOf(
            dbMessage(MessageId("msg-c"), createdAt = 300L),
            dbMessage(MessageId("msg-a"), createdAt = 100L),
            dbMessage(MessageId("msg-b"), createdAt = 200L),
        )
        projector.project(messages, null, null) // warm cache
        val result = projector.project(messages, null, null) // cache hit

        assertEquals(
            listOf(MessageId("msg-a"), MessageId("msg-b"), MessageId("msg-c")),
            result.messages.map { it.id },
        )
    }

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

    private fun dbMessage(
        id: MessageId,
        createdAt: Long = 1L,
        state: MessageState = MessageState.COMPLETE,
    ) = Message(
        id = id,
        chatId = chatId,
        content = Content("content for ${id.value}"),
        role = Role.ASSISTANT,
        messageState = state,
        createdAt = createdAt,
    )

    private fun makeSnapshot(id: MessageId, content: String): AccumulatedMessages =
        AccumulatedMessages(messages = mapOf(id to generatingSnapshot(id, content)))

    private fun generatingSnapshot(id: MessageId, content: String): MessageSnapshot =
        MessageSnapshot(
            messageId = id,
            modelType = ModelType.MAIN,
            content = content,
            thinkingRaw = "",
            isComplete = false,
        )
}
