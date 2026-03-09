package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository

/**
 * Fake implementation of ChatRepository for testing.
 * Allows controlling chat creation and verifying method calls.
 */
class FakeChatRepository : ChatRepository {

    private val createdChats = mutableListOf<Chat>()
    private var nextChatId = 1L
    var shouldThrowOnCreateChat = false
    private val savedAssistantMessages = mutableListOf<Pair<String, String>>()

    override suspend fun createChat(chat: Chat): Long {
        if (shouldThrowOnCreateChat) {
            throw RuntimeException("Simulated error on createChat")
        }
        val chatWithId = chat.copy(id = nextChatId)
        createdChats.add(chatWithId)
        return nextChatId++
    }

    override suspend fun saveAssistantMessage(
        messageId: String,
        content: String,
        thinkingData: ThinkingData?
    ) {
        savedAssistantMessages.add(messageId to content)
    }

    fun getCreatedChats(): List<Chat> = createdChats.toList()

    fun verifyChatCreated(times: Int) {
        org.junit.jupiter.api.Assertions.assertEquals(times, createdChats.size)
    }

    fun verifyChatName(expectedName: String) {
        org.junit.jupiter.api.Assertions.assertTrue(
            createdChats.any { it.name == expectedName },
            "No chat was created with name: $expectedName"
        )
    }

    fun reset() {
        createdChats.clear()
        nextChatId = 1L
        shouldThrowOnCreateChat = false
        savedAssistantMessages.clear()
    }
}

