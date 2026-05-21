package com.browntowndev.pocketcrew.core.data.remote.firestore.handlers

import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageFileEntity
import com.browntowndev.pocketcrew.core.data.local.dao.message.MessageWithFiles
import com.browntowndev.pocketcrew.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.pocketcrew.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.pocketcrew.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class MessageFileSyncHandlerTest {
    @Test
    fun `toFirestore includes chat id required by fromFirestore and dependency resolution`() =
        runTest {
            val messageDao = mockk<MessageDao>(relaxed = true)
            coEvery { messageDao.getMessageById(MessageId("message-1")) } returns
                MessageWithFiles(
                    message =
                        MessageEntity(
                            id = MessageId("message-1"),
                            chatId = ChatId("chat-1"),
                            content = "content",
                            role = Role.USER,
                            messageState = MessageState.COMPLETE,
                        ),
                    files = emptyList(),
                )
            val handler = MessageFileSyncHandler(createContext(messageDao))

            val payload =
                handler.toFirestore(
                    MessageFileEntity(
                        id = "file-1",
                        messageId = "message-1",
                        name = "notes.txt",
                        content = "notes",
                        mimeType = MimeType.PLAIN_TEXT,
                    ),
                )

            assertEquals("message-1", payload["message_id"])
            assertEquals("chat-1", payload["chat_id"])
        }

    private fun createContext(messageDao: MessageDao): FirestoreSyncContext {
        val localEncryption = FakeLocalDataEncryptionService()
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        return FirestoreSyncContext(
            firestore = mockk<FirebaseFirestore>(relaxed = true),
            daos = testFirestoreSyncDaos(messageDao = messageDao),
            localEncryption = localEncryption,
            sensitiveSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort),
            firestoreSensitiveFieldRepairer = mockk(relaxed = true),
            cryptoManager = mockk<CryptoManagerPort>(relaxed = true),
            loggingPort = loggingPort,
            apiKeyManager = mockk(relaxed = true),
            embeddingCoordinator = mockk(relaxed = true),
            userIdProvider = { "user-1" },
            purgeInProgress = AtomicBoolean(false),
        )
    }
}
