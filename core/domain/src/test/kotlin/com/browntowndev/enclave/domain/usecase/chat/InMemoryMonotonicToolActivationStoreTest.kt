package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryMonotonicToolActivationStoreTest {
    @Test
    fun union_empty_newNames_returns_empty_for_fresh_chatId() {
        val store = InMemoryMonotonicToolActivationStore()
        val result = store.union(ChatId("chat-1"), emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun union_adds_names_and_returns_union() {
        val store = InMemoryMonotonicToolActivationStore()
        val result1 = store.union(ChatId("chat-1"), setOf("tool_a"))
        assertEquals(setOf("tool_a"), result1)
        val result2 = store.union(ChatId("chat-1"), setOf("tool_b"))
        assertEquals(setOf("tool_a", "tool_b"), result2)
    }

    @Test
    fun union_names_never_removed_across_turns() {
        val store = InMemoryMonotonicToolActivationStore()
        store.union(ChatId("chat-1"), setOf("tool_a", "tool_b"))
        val result = store.union(ChatId("chat-1"), emptySet())
        assertEquals(setOf("tool_a", "tool_b"), result)
    }

    @Test
    fun union_isolated_across_chat_ids() {
        val store = InMemoryMonotonicToolActivationStore()
        store.union(ChatId("chat-1"), setOf("tool_a"))
        val result = store.union(ChatId("chat-2"), emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun clearSession_removes_record_for_chatId() {
        val store = InMemoryMonotonicToolActivationStore()
        store.union(ChatId("chat-1"), setOf("tool_a"))
        store.clearSession(ChatId("chat-1"))
        val result = store.union(ChatId("chat-1"), emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun clearSession_does_not_affect_other_chatIds() {
        val store = InMemoryMonotonicToolActivationStore()
        store.union(ChatId("chat-1"), setOf("tool_a"))
        store.union(ChatId("chat-2"), setOf("tool_b"))
        store.clearSession(ChatId("chat-1"))
        val result = store.union(ChatId("chat-2"), emptySet())
        assertEquals(setOf("tool_b"), result)
    }
}
