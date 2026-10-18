package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.UUID
import org.robolectric.annotation.Config as RoboConfig

/**
 * Translated from MockK to a real in-memory Room database (ENC-28):
 * persistence assertions read the stored rows back through the real DAOs,
 * and the transaction test forces the file insert to fail with a SQLite
 * trigger so the message insert must roll back.
 */
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class MessageRepositoryImplSaveMessageTest {
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

    @Test
    fun `saveMessage with null chatId throws IllegalArgumentException`() =
        runTest {
            // Given - a message without a chatId (null)
            val message =
                Message(
                    id = MessageId(UUID.randomUUID().toString()),
                    chatId = null,
                    content = Content(text = "Test message"),
                    role = Role.USER,
                )

            // When & Then - expect IllegalArgumentException before DAO insertion
            val exception =
                assertThrows<IllegalArgumentException> {
                    repository.saveMessage(message)
                }

            // Verify the error message is informative
            assert(exception.message?.contains("must have a chatId before persisting") == true)
            assert(exception.message?.contains("CreateUserMessageUseCase") == true)

            // Verify nothing was persisted
            assert(database.messageQueryDao().getAllIds().isEmpty())
        }

    @Test
    fun `saveMessage with non-null chatId succeeds`() =
        runTest {
            // Given - a valid message with a chatId
            val messageId = MessageId(UUID.randomUUID().toString())
            val chatId = ChatId(UUID.randomUUID().toString())
            database.seedChat(chatId)
            val message =
                Message(
                    id = messageId,
                    chatId = chatId,
                    content = Content(text = "Test message"),
                    role = Role.USER,
                )

            // When
            val result = repository.saveMessage(message)

            // Then
            assert(result == messageId)
            val stored = database.messageQueryDao().getMessageById(messageId)
            assert(stored?.message?.content == "PCENC:v1:aes-gcm:Test message")
        }

    @Test
    fun `saveMessage stores encrypted attachment content`() =
        runTest {
            val messageId = MessageId(UUID.randomUUID().toString())
            val chatId = ChatId(UUID.randomUUID().toString())
            database.seedChat(chatId)
            val attachment =
                com.browntowndev.enclave.domain.model.chat.FileAttachment(
                    name = "secret.txt",
                    content = "private attachment",
                )
            val message =
                Message(
                    id = messageId,
                    chatId = chatId,
                    content = Content(text = "body", fileAttachments = listOf(attachment)),
                    role = Role.USER,
                )

            repository.saveMessage(message)

            val storedFiles = database.messageDao().getMessageById(messageId)?.files.orEmpty()
            assert(storedFiles.size == 1)
            assert(storedFiles.single().name == "PCENC:v1:aes-gcm:secret.txt")
            assert(storedFiles.single().content == "PCENC:v1:aes-gcm:private attachment")
        }

    @Test
    fun `saveMessage with attachments uses transaction and fails if file insertion fails`() =
        runTest {
            // Given
            val messageId = MessageId(UUID.randomUUID().toString())
            val chatId = ChatId(UUID.randomUUID().toString())
            database.seedChat(chatId)
            val attachment =
                com.browntowndev.enclave.domain.model.chat.FileAttachment(
                    name = "test.txt",
                    content = "test content",
                )
            val message =
                Message(
                    id = messageId,
                    chatId = chatId,
                    content = Content(text = "Test message", fileAttachments = listOf(attachment)),
                    role = Role.USER,
                )

            // Force every message_file insert to abort, so the surrounding
            // transaction must roll back the message insert with it.
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_message_file_insert BEFORE INSERT ON message_file " +
                    "BEGIN SELECT RAISE(ABORT, 'Database error'); END",
            )

            // When & Then
            assertThrows<RuntimeException> {
                repository.saveMessage(message)
            }

            // The transaction rolled back: no message row survives the failed file insert.
            assert(database.messageQueryDao().getMessageById(messageId) == null)
            assert(database.messageQueryDao().getAllIds().isEmpty())
        }
}
