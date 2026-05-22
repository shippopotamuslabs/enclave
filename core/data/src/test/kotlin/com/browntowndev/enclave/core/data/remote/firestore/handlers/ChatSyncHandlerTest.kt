package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

class ChatSyncHandlerTest {
    private val chatDao = mockk<ChatDao>(relaxed = true)
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
            coEvery { chatDao.markSynced(any()) } returns Unit

            handler.markSynced(listOf(chat))

            coVerify { chatDao.markSynced(listOf(ChatId("chat-abc"))) }
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
            coVerify(exactly = 0) { chatDao.upsert(any()) }
        }

    private fun createContext(chatDao: ChatDao): FirestoreSyncContext {
        val localEncryption = FakeLocalDataEncryptionService()
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        return FirestoreSyncContext(
            firestore = mockk<FirebaseFirestore>(relaxed = true),
            daos = testFirestoreSyncDaos(chatDao = chatDao),
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
