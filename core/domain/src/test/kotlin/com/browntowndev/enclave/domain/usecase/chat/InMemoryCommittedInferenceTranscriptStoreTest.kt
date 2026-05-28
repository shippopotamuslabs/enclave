package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InMemoryCommittedInferenceTranscriptStoreTest {
    @Test
    fun commit_get_and_clear_preserve_inference_metadata() {
        val store = InMemoryCommittedInferenceTranscriptStore()
        val chatId = ChatId("chat-1")
        val transcript =
            listOf(
                ChatMessage(
                    role = Role.ASSISTANT,
                    content = "",
                    metadataJson = """{"raw_tool_calls":[{"id":"call_1"}]}""",
                ),
            )

        store.commit(chatId, transcript)

        assertEquals(transcript, store.get(chatId))
        store.clear(chatId)
        assertNull(store.get(chatId))
    }
}
