package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DbMessagesFingerprintTest {
    @Test
    fun `fingerprint ignores content when durable updatedAt is unchanged`() {
        val initial =
            listOf(
                message(
                    id = MessageId("assistant-1"),
                    text = "visible",
                    updatedAt = 100L,
                ),
            )
        val redundant =
            listOf(
                initial.single().copy(content = Content(text = "changed but stale"), updatedAt = 100L),
            )

        assertEquals(initial.dbMessagesFingerprintForTesting(), redundant.dbMessagesFingerprintForTesting())
    }

    @Test
    fun `fingerprint changes when any message updatedAt changes`() {
        val initial =
            listOf(
                message(id = MessageId("user-1"), text = "prompt", updatedAt = 100L),
                message(id = MessageId("assistant-1"), text = "answer", updatedAt = 200L),
            )
        val editedEarlierMessage =
            listOf(
                initial.first().copy(content = Content(text = "edited prompt"), updatedAt = 300L),
                initial.last(),
            )

        assertNotEquals(
            initial.dbMessagesFingerprintForTesting(),
            editedEarlierMessage.dbMessagesFingerprintForTesting(),
        )
    }

    @Test
    fun `fingerprint falls back to structural message equality when updatedAt is absent`() {
        val initial =
            listOf(
                message(
                    id = MessageId("assistant-1"),
                    text = "visible",
                    updatedAt = null,
                ),
            )
        val advancedText =
            listOf(
                initial.single().copy(content = Content(text = "visible answer")),
            )

        assertNotEquals(initial.dbMessagesFingerprintForTesting(), advancedText.dbMessagesFingerprintForTesting())
    }

    private fun message(
        id: MessageId,
        text: String,
        updatedAt: Long?,
    ): Message =
        Message(
            id = id,
            chatId = ChatId("chat-1"),
            content = Content(text = text),
            role = Role.ASSISTANT,
            messageState = MessageState.PROCESSING,
            createdAt = 1L,
            updatedAt = updatedAt,
        )
}
