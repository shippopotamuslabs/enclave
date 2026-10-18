package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageFileEntity
import com.browntowndev.enclave.core.data.local.dao.message.MessageWithFiles
import com.browntowndev.enclave.core.data.remote.firestore.FakeEnclaveDatabase
import com.browntowndev.enclave.core.data.remote.firestore.FakeMessageDaoBundle
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.testSyncContext
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class MessageFileSyncHandlerTest {
    @Test
    fun `toFirestore includes chat id required by fromFirestore and dependency resolution`() =
        runTest {
            val messageDaoBundle = FakeMessageDaoBundle(FakeEnclaveDatabase())
            messageDaoBundle.stubMessageById(
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
                ),
            )
            val handler = MessageFileSyncHandler(createContext(messageDaoBundle.messageDao))

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

    private fun createContext(messageDao: MessageDao): FirestoreSyncContext =
        testSyncContext(daos = testFirestoreSyncDaos(messageDao = messageDao))
}
