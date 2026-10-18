package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SearchCurrentChatUseCaseTest {
    private lateinit var messageRepository: FakeMessageRepository
    private lateinit var embeddingEngine: FakeEmbeddingEngine
    private lateinit var useCase: SearchCurrentChatUseCase

    @BeforeEach
    fun setup() {
        messageRepository = FakeMessageRepository()
        embeddingEngine = FakeEmbeddingEngine(dimensions = 2)
        useCase = SearchCurrentChatUseCase(messageRepository, embeddingEngine)
    }

    @Test
    fun `invoke delegates to messageRepository searchMessagesInChat`() =
        runTest {
            val chatId = ChatId("chat-1")
            val query = "test query"
            val messages =
                listOf(
                    Message(id = MessageId("m1"), chatId = chatId, content = Content(text = "test content"), role = Role.USER),
                )
            messageRepository.seed(messages)

            val result = useCase(chatId, query)

            assertEquals(messages, result)
            // The repository must be searched exactly once, with the engine's embedding of the query.
            val expectedVector = FakeEmbeddingEngine(dimensions = 2).getEmbedding(query)
            assertEquals(1, messageRepository.searchMessagesInChatCalls.size)
            val recorded = messageRepository.searchMessagesInChatCalls.single()
            assertEquals(chatId, recorded.chatId)
            assertTrue(recorded.queryVector.contentEquals(expectedVector))
        }

    @Test
    fun `invoke returns empty list when no messages match`() =
        runTest {
            val chatId = ChatId("chat-2")
            val query = "nonexistent"

            val result = useCase(chatId, query)

            assertEquals(emptyList<Message>(), result)
        }
}
