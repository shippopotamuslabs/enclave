package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.MessageId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InMemoryCommittedInferencePromptStoreTest {
    @Test
    fun commit_get_returns_exact_content_for_messageId() {
        val store = InMemoryCommittedInferencePromptStore()
        val messageId = MessageId("user-1")
        val content = "# RELEVANT FACTS & CONTEXT:\n- fact\n\nhello"

        store.commit(messageId, content)

        assertEquals(content, store.get(messageId))
    }

    @Test
    fun clear_removes_content_for_messageId() {
        val store = InMemoryCommittedInferencePromptStore()
        val messageId = MessageId("user-1")
        store.commit(messageId, "hello")

        store.clear(messageId)

        assertNull(store.get(messageId))
    }
}
