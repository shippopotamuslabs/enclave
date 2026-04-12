package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for MergeMessagesUseCase.
 */
class MergeMessagesUseCaseTest {

    private val useCase = MergeMessagesUseCase()

    @Test
    fun `given no inputs returns null`() {
        val result = useCase(null, null)
        assertNull(result)
    }

    @Test
    fun `given only db message returns it`() {
        val dbMessage = createMessage(id = MessageId("1"), state = MessageState.COMPLETE, content = "db content")
        val result = useCase(dbMessage, null)
        assertEquals(dbMessage, result)
    }

    @Test
    fun `given only in-flight returns it`() {
        val inFlightMessage = createMessage(id = MessageId("2"), state = MessageState.GENERATING, content = "in-flight content")
        val result = useCase(null, inFlightMessage)
        assertEquals(inFlightMessage, result)
    }

    @Test
    fun `given both and db is COMPLETE returns db`() {
        val db = createMessage(id = MessageId("1"), state = MessageState.COMPLETE, content = "db content")
        val inFlight = createMessage(id = MessageId("1"), state = MessageState.GENERATING, content = "in-flight content")
        val result = useCase(db, inFlight)
        assertEquals(db, result)
        assertEquals("db content", result?.content?.text)
    }

    @Test
    fun `given both and db is PROCESSING returns in-flight`() {
        val db = createMessage(id = MessageId("1"), state = MessageState.PROCESSING, content = "")
        val inFlight = createMessage(id = MessageId("1"), state = MessageState.GENERATING, content = "real content")
        val result = useCase(db, inFlight)
        assertEquals(inFlight, result)
        assertEquals("real content", result?.content?.text)
    }

    @Test
    fun `given both and db is THINKING returns in-flight`() {
        val db = createMessage(id = MessageId("1"), state = MessageState.THINKING, content = "")
        val inFlight = createMessage(id = MessageId("1"), state = MessageState.THINKING, content = "thinking content")
        val result = useCase(db, inFlight)
        assertEquals(inFlight, result)
    }

    @Test
    fun `given both and db is GENERATING returns in-flight`() {
        val db = createMessage(id = MessageId("1"), state = MessageState.GENERATING, content = "partial")
        val inFlight = createMessage(id = MessageId("1"), state = MessageState.GENERATING, content = "more content")
        val result = useCase(db, inFlight)
        assertEquals(inFlight, result)
    }

    private fun createMessage(
        id: MessageId,
        state: MessageState,
        content: String
    ): Message = Message(
        id = id,
        chatId = ChatId("1"),
        role = Role.ASSISTANT,
        content = Content(text = content, pipelineStep = null),
        thinkingRaw = null,
        thinkingDurationSeconds = null,
        thinkingStartTime = null,
        thinkingEndTime = null,
        createdAt = 0L,
        messageState = state,
        modelType = null
    )
}
