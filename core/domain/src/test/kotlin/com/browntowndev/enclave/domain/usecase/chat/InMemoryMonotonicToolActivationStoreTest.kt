package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryMonotonicToolActivationStoreTest {
    @Test
    fun activatedNames_returns_empty_for_fresh_chatId() {
        val store = InMemoryMonotonicToolActivationStore()
        assertTrue(store.activatedNames(ChatId("chat-1")).isEmpty())
    }

    @Test
    fun recordActivation_merges_names() {
        val store = InMemoryMonotonicToolActivationStore()
        store.recordActivation(ChatId("chat-1"), setOf("tool_a"))
        store.recordActivation(ChatId("chat-1"), setOf("tool_b"))
        assertEquals(setOf("tool_a", "tool_b"), store.activatedNames(ChatId("chat-1")))
    }

    @Test
    fun activated_names_never_removed_across_turns() {
        val store = InMemoryMonotonicToolActivationStore()
        store.recordActivation(ChatId("chat-1"), setOf("tool_a", "tool_b"))
        assertEquals(setOf("tool_a", "tool_b"), store.activatedNames(ChatId("chat-1")))
    }

    @Test
    fun activations_isolated_across_chat_ids() {
        val store = InMemoryMonotonicToolActivationStore()
        store.recordActivation(ChatId("chat-1"), setOf("tool_a"))
        assertTrue(store.activatedNames(ChatId("chat-2")).isEmpty())
    }

    @Test
    fun clearSession_removes_record_for_chatId() {
        val store = InMemoryMonotonicToolActivationStore()
        store.recordActivation(ChatId("chat-1"), setOf("tool_a"))
        store.clearSession(ChatId("chat-1"))
        assertTrue(store.activatedNames(ChatId("chat-1")).isEmpty())
    }

    @Test
    fun clearSession_does_not_affect_other_chatIds() {
        val store = InMemoryMonotonicToolActivationStore()
        store.recordActivation(ChatId("chat-1"), setOf("tool_a"))
        store.recordActivation(ChatId("chat-2"), setOf("tool_b"))
        store.clearSession(ChatId("chat-1"))
        assertEquals(setOf("tool_b"), store.activatedNames(ChatId("chat-2")))
    }
}
