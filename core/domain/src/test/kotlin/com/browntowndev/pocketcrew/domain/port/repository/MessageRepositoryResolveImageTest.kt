package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.ResolvedImageTarget
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.usecase.FakeMessageRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MessageRepositoryResolveImageTest {

    private lateinit var repository: FakeMessageRepository

    @BeforeEach
    fun setUp() {
        repository = FakeMessageRepository()
    }

    @Test
    fun `resolves current user message image when present`() = runTest {
        repository.setResolvedImageTarget(
            ResolvedImageTarget(
                userMessageId = MessageId("current-user"),
                imageUri = "file:///photo.jpg",
            )
        )

        val result = repository.resolveLatestImageBearingUserMessage(
            chatId = ChatId("chat-1"),
            currentUserMessageId = MessageId("current-user"),
        )

        assertEquals(MessageId("current-user"), result!!.userMessageId)
        assertEquals("file:///photo.jpg", result.imageUri)
    }

    @Test
    fun `resolves latest prior user image when current message has none`() = runTest {
        repository.setResolvedImageTarget(
            ResolvedImageTarget(
                userMessageId = MessageId("prior-user"),
                imageUri = "file:///earlier-photo.jpg",
            )
        )

        val result = repository.resolveLatestImageBearingUserMessage(
            chatId = ChatId("chat-1"),
            currentUserMessageId = MessageId("current-user"),
        )

        assertEquals(MessageId("prior-user"), result!!.userMessageId)
        assertEquals("file:///earlier-photo.jpg", result.imageUri)
    }

    @Test
    fun `ignores assistant messages even if they had image URIs`() = runTest {
        repository.setResolvedImageTarget(null)

        val result = repository.resolveLatestImageBearingUserMessage(
            chatId = ChatId("chat-1"),
            currentUserMessageId = MessageId("current-user"),
        )

        assertNull(result)
    }

    @Test
    fun `returns null when no user image exists in the chat`() = runTest {
        repository.setResolvedImageTarget(null)

        val result = repository.resolveLatestImageBearingUserMessage(
            chatId = ChatId("chat-1"),
            currentUserMessageId = MessageId("current-user"),
        )

        assertNull(result)
    }

    @Test
    fun `handles multiple prior image-bearing messages by choosing the latest deterministically`() = runTest {
        repository.setResolvedImageTarget(
            ResolvedImageTarget(
                userMessageId = MessageId("latest-prior"),
                imageUri = "file:///latest-photo.jpg",
            )
        )

        val result = repository.resolveLatestImageBearingUserMessage(
            chatId = ChatId("chat-1"),
            currentUserMessageId = MessageId("current-user"),
        )

        assertEquals(MessageId("latest-prior"), result!!.userMessageId)
        assertEquals("file:///latest-photo.jpg", result.imageUri)
    }
}
