package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository

/**
 * Fake implementation of MessageRepository for testing.
 * Allows controlling message saving and verifying method calls.
 */
class FakeMessageRepository : MessageRepository {

    private val savedMessages = mutableListOf<Message>()
    private var getMessageByIdResult: Message? = null
    private var getMessagesForChatResult: List<Message> = emptyList()
    private var nextMessageId = 1L

    // Methods to simulate errors
    var shouldThrowOnSaveMessage = false

    override suspend fun saveMessage(message: Message): Long {
        if (shouldThrowOnSaveMessage) throw RuntimeException("Simulated error on saveMessage")
        savedMessages.add(message)
        val id = nextMessageId++
        return id
    }

    override suspend fun getMessageById(id: Long): Message? {
        return getMessageByIdResult
    }

    override suspend fun getMessagesForChat(chatId: Long): List<Message> {
        return getMessagesForChatResult
    }

    fun setMessagesForChat(messages: List<Message>) {
        getMessagesForChatResult = messages
    }

    fun getSavedMessages(): List<Message> = savedMessages.toList()

    fun verifySaveMessageCalled(times: Int) {
        org.junit.jupiter.api.Assertions.assertEquals(times, savedMessages.size)
    }

    fun verifyMessageSaved(message: Message) {
        org.junit.jupiter.api.Assertions.assertTrue(
            savedMessages.any { it.id == message.id && it.content == message.content && it.role == message.role },
            "Message was not saved: $message"
        )
    }

    fun reset() {
        savedMessages.clear()
        shouldThrowOnSaveMessage = false
        getMessageByIdResult = null
        getMessagesForChatResult = emptyList()
    }
}

