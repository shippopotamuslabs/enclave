package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MergeDbMessagesWithOptimisticUseCaseTest {
    private val useCase = MergeDbMessagesWithOptimisticUseCase()

    @Test
    @DisplayName("OSDA-D-08 — optimistic user visible when not yet in DB")
    fun osdaD08_appendsOptimisticUser() {
        val dbMessages =
            listOf(
                userMessage(id = "u-old", createdAt = 50),
            )
        val optimistic = userMessage(id = "u-new", createdAt = 100)

        val result = useCase.invoke(dbMessages = dbMessages, optimisticUserMessage = optimistic)

        assertEquals(2, result.size)
        assertEquals(MessageId("u-new"), result.last().id)
    }

    @Test
    @DisplayName("OSDA-D-09 — duplicate id prefers DB only")
    fun osdaD09_dropsOptimisticOnDbMatch() {
        val dbMessages = listOf(userMessage(id = "u-same", createdAt = 100))
        val optimistic = userMessage(id = "u-same", createdAt = 200)

        val result = useCase.invoke(dbMessages = dbMessages, optimisticUserMessage = optimistic)

        assertEquals(1, result.size)
        assertEquals(MessageId("u-same"), result.single().id)
        assertEquals(dbMessages, result)
    }

    @Test
    @DisplayName("OSDA-D-10 — optimistic inserted by createdAt ordering")
    fun osdaD10_chronologicalOrder() {
        val dbMessages = listOf(userMessage(id = "u1", createdAt = 50))
        val optimistic = userMessage(id = "u2", createdAt = 150)

        val result = useCase.invoke(dbMessages = dbMessages, optimisticUserMessage = optimistic)

        assertEquals(listOf(MessageId("u1"), MessageId("u2")), result.map { it.id })
    }

    private fun userMessage(
        id: String,
        createdAt: Long,
    ) = Message(
        id = MessageId(id),
        chatId = ChatId("chat-1"),
        content = Content(text = "text"),
        role = Role.USER,
        createdAt = createdAt,
    )
}
