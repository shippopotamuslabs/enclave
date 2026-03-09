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

    // Methods to simulate errors
    var shouldThrowOnSaveMessage = false

    override suspend fun saveMessage(message: Message) {
        if (shouldThrowOnSaveMessage) throw RuntimeException("Simulated error on saveMessage")
        savedMessages.add(message)
    }

    override suspend fun getMessageById(id: Long): Message? {
        return getMessageByIdResult
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
    }
}

