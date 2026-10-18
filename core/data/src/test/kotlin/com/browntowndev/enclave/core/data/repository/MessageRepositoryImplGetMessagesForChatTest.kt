package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import org.robolectric.annotation.Config as RoboConfig

/**
 * Translated from MockK to a real in-memory Room database (ENC-28): messages
 * are seeded through the real DAOs and read back through the production
 * repository, so ordering, role mapping, and read-repair behavior are the
 * real persistence behavior rather than stubbed DAO outputs.
 */
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class MessageRepositoryImplGetMessagesForChatTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var repository: MessageRepositoryImpl

    @BeforeEach
    fun setup() {
        database = createInMemoryEnclaveDatabase()
        repository = createMessageRepositoryImpl(database)
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    private suspend fun seedMessage(
        id: String,
        chatId: ChatId,
        content: String,
        role: Role,
        createdAt: Long? = null,
    ) {
        database.seedChat(chatId)
        database.messageDao().upsert(
            MessageEntity(
                id = MessageId(id),
                chatId = chatId,
                content = content,
                role = role,
                createdAt = createdAt,
            ),
        )
    }

    // ========== Basic Functionality Tests ==========

    @Test
    fun `getMessagesForChat returns messages for specific chat`() =
        runTest {
            // Given
            val chatId = ChatId("5")
            seedMessage("1", chatId, "Hello", Role.USER, createdAt = 1L)
            seedMessage("2", chatId, "Hi there!", Role.ASSISTANT, createdAt = 2L)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then
            assertEquals(2, result.size)
            assertEquals("Hello", result[0].content.text)
            assertEquals("Hi there!", result[1].content.text)
        }

    @Test
    fun `getMessagesForChat returns messages in chronological order by created_at ASC`() =
        runTest {
            // Given - seeded out of order; the DAO query orders by created_at ASC
            val chatId = ChatId("10")
            seedMessage("8", chatId, "Message 3", Role.USER, createdAt = 3L)
            seedMessage("3", chatId, "Message 1", Role.USER, createdAt = 1L)
            seedMessage("5", chatId, "Message 2", Role.ASSISTANT, createdAt = 2L)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then - the repository preserves the order from DAO (which has ORDER BY created_at ASC)
            assertEquals(3, result.size)
            assertEquals(MessageId("3"), result[0].id)
            assertEquals(MessageId("5"), result[1].id)
            assertEquals(MessageId("8"), result[2].id)
        }

    @Test
    fun `getMessagesForChat returns empty list for chat with no messages`() =
        runTest {
            // Given
            val chatId = ChatId("999")

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then
            assertTrue(result.isEmpty())
        }

    // ========== Role Mapping Tests ==========

    @Test
    fun `getMessagesForChat maps USER role correctly`() =
        runTest {
            // Given
            val chatId = ChatId("1")
            seedMessage("1", chatId, "User message", Role.USER)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then
            assertEquals(Role.USER, result[0].role)
        }

    @Test
    fun `getMessagesForChat maps ASSISTANT role correctly`() =
        runTest {
            // Given
            val chatId = ChatId("1")
            seedMessage("2", chatId, "Assistant response", Role.ASSISTANT)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then
            assertEquals(Role.ASSISTANT, result[0].role)
        }

    @Test
    fun `getMessagesForChat maps SYSTEM role correctly`() =
        runTest {
            // Given
            val chatId = ChatId("1")
            seedMessage("3", chatId, "System message", Role.SYSTEM)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then
            assertEquals(Role.SYSTEM, result[0].role)
        }

    // ========== Content Preservation Tests ==========

    @Test
    fun `getMessagesForChat preserves message content exactly`() =
        runTest {
            // Given - content with special characters that could break parsing
            val chatId = ChatId("1")
            val specialContent = "Hello! \"Quotes\" & <special> chars\nnewlines\ttabs"
            seedMessage("1", chatId, specialContent, Role.USER)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then
            assertEquals(specialContent, result[0].content.text)
        }

    @Test
    fun `getMessagesForChat preserves empty content`() =
        runTest {
            // Given
            val chatId = ChatId("1")
            seedMessage("1", chatId, "", Role.ASSISTANT)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then
            assertEquals("", result[0].content.text)
        }

    // ========== Chat ID Tests ==========

    @Test
    fun `getMessagesForChat calls dao with correct chatId`() =
        runTest {
            // Given - messages in two chats; only the requested chat's rows may come back
            val chatId = ChatId("42")
            seedMessage("1", chatId, "In chat 42", Role.USER)
            seedMessage("2", ChatId("43"), "In chat 43", Role.USER)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then
            assertEquals(1, result.size)
            assertEquals("In chat 42", result.single().content.text)
            assertEquals(chatId, result.single().chatId)
        }

    @Test
    fun `getMessagesForChat works with chatId of 1`() =
        runTest {
            // Given - edge case: minimum valid ID
            val chatId = ChatId("1")
            seedMessage("1", chatId, "First chat", Role.USER)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then
            assertEquals(1, result.size)
            assertEquals(chatId, result[0].chatId)
        }

    @Test
    fun `getMessagesForChat works with large chatId`() =
        runTest {
            // Given - edge case: large ID
            val chatId = ChatId("MAX")
            seedMessage("1", chatId, "Large ID chat", Role.USER)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then
            assertEquals(1, result.size)
            assertEquals(chatId, result[0].chatId)
        }

    // ========== Multi-turn Conversation Tests ==========

    @Test
    fun `getMessagesForChat returns full conversation history in order`() =
        runTest {
            // Given - simulate a multi-turn conversation
            val chatId = ChatId("1")
            seedMessage("1", chatId, "Hello", Role.USER, createdAt = 1L)
            seedMessage("2", chatId, "Hi! How can I help?", Role.ASSISTANT, createdAt = 2L)
            seedMessage("3", chatId, "Tell me a joke", Role.USER, createdAt = 3L)
            seedMessage("4", chatId, "Why did the chicken cross the road?", Role.ASSISTANT, createdAt = 4L)
            seedMessage("5", chatId, "I don't know, why?", Role.USER, createdAt = 5L)
            seedMessage("6", chatId, "To get to the other side!", Role.ASSISTANT, createdAt = 6L)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then - all 6 messages in order
            assertEquals(6, result.size)
            assertEquals("Hello", result[0].content.text)
            assertEquals("Hi! How can I help?", result[1].content.text)
            assertEquals("Tell me a joke", result[2].content.text)
            assertEquals("Why did the chicken cross the road?", result[3].content.text)
            assertEquals("I don't know, why?", result[4].content.text)
            assertEquals("To get to the other side!", result[5].content.text)

            // Verify alternating user/assistant pattern
            assertEquals(Role.USER, result[0].role)
            assertEquals(Role.ASSISTANT, result[1].role)
            assertEquals(Role.USER, result[2].role)
            assertEquals(Role.ASSISTANT, result[3].role)
            assertEquals(Role.USER, result[4].role)
            assertEquals(Role.ASSISTANT, result[5].role)
        }

    // ========== Edge Cases ==========

    @Test
    fun `getMessagesForChat handles single message chat`() =
        runTest {
            // Given
            val chatId = ChatId("1")
            seedMessage("1", chatId, "Only message", Role.USER)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then
            assertEquals(1, result.size)
            assertEquals("Only message", result[0].content.text)
        }

    @Test
    fun `getMessagesForChat applies read repairs after decrypting rows`() =
        runTest {
            // Given - content stored as plaintext at rest, which the read path must repair to encrypted form
            val chatId = ChatId("repair-chat")
            seedMessage("1", chatId, "secret", Role.USER)

            val result = repository.getMessagesForChat(chatId)

            assertEquals("secret", result.single().content.text)
            // The read repair persisted the encrypted form back to the row.
            val stored = database.messageQueryDao().getMessageById(MessageId("1"))
            assertEquals("PCENC:v1:aes-gcm:secret", stored?.message?.content)
        }

    @Test
    fun `getMessagesForChat handles messages with unicode content`() =
        runTest {
            // Given
            val chatId = ChatId("1")
            val unicodeContent = "Hello 世界 🌍 مرحبا"
            seedMessage("1", chatId, unicodeContent, Role.USER)

            // When
            val result = repository.getMessagesForChat(chatId)

            // Then
            assertEquals(unicodeContent, result[0].content.text)
        }
}
