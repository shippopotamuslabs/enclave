package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.remote.firestore.FakeChatDao
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.testSyncContext
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.domain.model.chat.ChatId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Date
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ChatSyncHandlerTest {
    private val chatDao = FakeChatDao()
    private lateinit var handler: ChatSyncHandler

    @BeforeEach
    fun setup() {
        handler = ChatSyncHandler(createContext(chatDao))
    }

    @Test
    @DisplayName("collectionPath is chats")
    fun collectionPath_isChats() {
        assertEquals("chats", handler.collectionPath)
    }

    @Test
    @DisplayName("getEntityId returns chat id value")
    fun getEntityId_returnsChatIdValue() {
        val chat =
            ChatEntity(
                id = ChatId("chat-123"),
                name = "encrypted",
                created = Date(0),
                lastModified = Date(0),
                pinned = false,
                updatedAt = 0L,
                deleted = false,
                pendingSync = 0,
            )

        assertEquals("chat-123", handler.getEntityId(chat))
    }

    @Test
    @DisplayName("markSynced forwards chat ids to dao")
    fun markSynced_forwardsIdsToDao() =
        runTest {
            val chat =
                ChatEntity(
                    id = ChatId("chat-abc"),
                    name = "encrypted",
                    created = Date(0),
                    lastModified = Date(0),
                    pinned = false,
                    updatedAt = 0L,
                    deleted = false,
                    pendingSync = 0,
                )

            handler.markSynced(listOf(chat))

            assertEquals(listOf(listOf(ChatId("chat-abc"))), chatDao.markSyncedCalls)
        }

    @Test
    @DisplayName("repairForSync returns same entity when already encrypted")
    fun repairForSync_returnsSameEntityWhenUnchanged() =
        runTest {
            val chat =
                ChatEntity(
                    id = ChatId("chat-1"),
                    name = "PCENC:v1:aes-gcm:enc:name",
                    created = Date(1),
                    lastModified = Date(2),
                    pinned = false,
                    updatedAt = 3L,
                    deleted = false,
                    pendingSync = 1,
                )

            val result = handler.repairForSync(chat)

            assertEquals(chat, result)
            assertTrue(chatDao.upsertedChats.isEmpty())
        }

    private fun createContext(chatDao: ChatDao): FirestoreSyncContext =
        testSyncContext(daos = testFirestoreSyncDaos(chatDao = chatDao))
}
