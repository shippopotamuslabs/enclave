package com.browntowndev.enclave.core.data.security

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ApiCredentialsEntity
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class SensitiveRoomReadRepairApplierTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var applier: SensitiveRoomReadRepairApplier

    @BeforeEach
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                EnclaveDatabase::class.java,
            )
            .allowMainThreadQueries()
            .build()

        applier = SensitiveRoomReadRepairApplier(
            chatDao = database.chatDao(),
            messageDao = database.messageDao(),
            messageFileDao = database.messageFileDao(),
            chatSummaryDao = database.chatSummaryDao(),
            memoriesDao = database.memoriesDao(),
            messageVisionAnalysisDao = database.messageVisionAnalysisDao(),
            webSearchSourceDao = database.webSearchSourceDao(),
            messageTranscriptSegmentDao = database.messageTranscriptSegmentDao(),
            documentDao = database.documentDao(),
            apiCredentialsDao = database.apiCredentialsDao(),
            apiModelConfigurationsDao = database.apiModelConfigurationsDao(),
            localModelConfigurationsDao = database.localModelConfigurationsDao(),
            ttsProviderDao = database.ttsProviderDao(),
            mediaProviderDao = database.mediaProviderDao(),
            studioAlbumDao = database.studioAlbumDao(),
            studioMediaDao = database.studioMediaDao(),
        )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("Scenario 1.1: Batching Repairs for Different Entity Owners")
    fun testBatchRepairsForDifferentOwners() = runTest {
        // Given: Seed Chat and ApiCredentials
        val chatId = ChatId("chat-1")
        val chatEntity = ChatEntity(
            id = chatId,
            name = "Old Chat Name",
            created = Date(),
            lastModified = Date(),
            pinned = false,
            pendingSync = 0,
            updatedAt = 1000L
        )
        database.chatDao().upsert(chatEntity)

        val credentialId = ApiCredentialsId("cred-1")
        val credEntity = ApiCredentialsEntity(
            id = credentialId,
            displayName = "Old Display Name",
            provider = ApiProvider.OPENAI,
            credentialAlias = "openai-main",
            pendingSync = 0,
            updatedAt = 1000L
        )
        database.apiCredentialsDao().upsert(credEntity)

        // When: Execute repairs for both
        val repairs = listOf(
            SensitiveRoomReadRepair(
                ref = SensitiveFieldRef(owner = "ChatEntity", field = "name", boundary = PersistenceBoundary.ROOM),
                entityId = chatId.value,
                encryptedValue = "Repaired Chat Name"
            ),
            SensitiveRoomReadRepair(
                ref = SensitiveFieldRef(owner = "ApiCredentialsEntity", field = "displayName", boundary = PersistenceBoundary.ROOM),
                entityId = credentialId.value,
                encryptedValue = "Repaired Display Name"
            )
        )
        applier.apply(repairs)

        // Then: Verify both entities were updated
        val updatedChat = database.chatDao().getChatById(chatId)
        assertNotNull(updatedChat)
        assertEquals("Repaired Chat Name", updatedChat?.name)
        assertEquals(1, updatedChat?.pendingSync)
        assertTrue(updatedChat!!.updatedAt > 1000L)

        val updatedCred = database.apiCredentialsDao().getById(credentialId)
        assertNotNull(updatedCred)
        assertEquals("Repaired Display Name", updatedCred?.displayName)
        assertEquals(1, updatedCred?.pendingSync)
        assertTrue(updatedCred!!.updatedAt > 1000L)
    }

    @Test
    @DisplayName("Scenario 1.2: Empty Batch Early-Exit")
    fun testEmptyBatchEarlyExit() = runTest {
        // Given: No repairs
        val repairs = emptyList<SensitiveRoomReadRepair>()

        // When/Then: apply should execute cleanly without interacting with DB
        applier.apply(repairs)
    }

    @Test
    @DisplayName("Scenario 1.3: Entity Missing from Database")
    fun testEntityMissingFromDatabase() = runTest {
        // Given: A repair referencing a non-existent entity ID
        val repairs = listOf(
            SensitiveRoomReadRepair(
                ref = SensitiveFieldRef(owner = "ChatEntity", field = "name", boundary = PersistenceBoundary.ROOM),
                entityId = "missing-chat-id",
                encryptedValue = "Repaired Name"
            )
        )

        // When/Then: apply should log and exit without throwing
        applier.apply(repairs)

        val chat = database.chatDao().getChatById(ChatId("missing-chat-id"))
        assertNull(chat)
    }

    @Test
    @DisplayName("Scenario 1.4: MessageEntity Multi-Field Repair")
    fun testMessageEntityMultiFieldRepair() = runTest {
        // Given: Seed a Chat and a Message
        val chatId = ChatId("chat-1")
        val chatEntity = ChatEntity(
            id = chatId,
            name = "Chat",
            created = Date(),
            lastModified = Date(),
            pinned = false
        )
        database.chatDao().upsert(chatEntity)

        val msgId = MessageId("msg-1")
        val message = MessageEntity(
            id = msgId,
            chatId = chatId,
            content = "Old Content",
            thinkingRaw = "Old Thinking",
            imageUrisJson = "Old Image Uris",
            role = Role.ASSISTANT,
            pendingSync = 0,
            updatedAt = 1000L
        )
        database.messageDao().upsert(message)

        // When: Execute multiple repairs on the same MessageEntity
        val repairs = listOf(
            SensitiveRoomReadRepair(
                ref = SensitiveFieldRef(owner = "MessageEntity", field = "content", boundary = PersistenceBoundary.ROOM),
                entityId = msgId.value,
                encryptedValue = "Repaired Content"
            ),
            SensitiveRoomReadRepair(
                ref = SensitiveFieldRef(owner = "MessageEntity", field = "thinkingRaw", boundary = PersistenceBoundary.ROOM),
                entityId = msgId.value,
                encryptedValue = "Repaired Thinking"
            ),
            SensitiveRoomReadRepair(
                ref = SensitiveFieldRef(owner = "MessageEntity", field = "imageUrisJson", boundary = PersistenceBoundary.ROOM),
                entityId = msgId.value,
                encryptedValue = "Repaired Image Uris"
            )
        )
        applier.apply(repairs)

        // Then: All fields are updated in a single run
        val updatedRow = database.messageDao().getMessageById(msgId)
        assertNotNull(updatedRow)
        val updated = updatedRow!!.message
        assertEquals("Repaired Content", updated.content)
        assertEquals("Repaired Thinking", updated.thinkingRaw)
        assertEquals("Repaired Image Uris", updated.imageUrisJson)
        assertEquals(1, updated.pendingSync)
        assertTrue(updated.updatedAt > 1000L)
    }

    @Test
    @DisplayName("Scenario 1.5: Ignored Field Handling")
    fun testIgnoredFieldHandling() = runTest {
        // Given: Seed Chat and Message
        val chatId = ChatId("chat-1")
        val chatEntity = ChatEntity(
            id = chatId,
            name = "Chat",
            created = Date(),
            lastModified = Date(),
            pinned = false
        )
        database.chatDao().upsert(chatEntity)

        val msgId = MessageId("msg-1")
        val message = MessageEntity(
            id = msgId,
            chatId = chatId,
            content = "Old Content",
            role = Role.ASSISTANT,
            pendingSync = 0,
            updatedAt = 1000L
        )
        database.messageDao().upsert(message)

        // When: Execute repair on an unmapped field
        val repairs = listOf(
            SensitiveRoomReadRepair(
                ref = SensitiveFieldRef(owner = "MessageEntity", field = "nonexistentField", boundary = PersistenceBoundary.ROOM),
                entityId = msgId.value,
                encryptedValue = "Repaired Value"
            )
        )
        applier.apply(repairs)

        // Then: Entity is not modified
        val updatedRow = database.messageDao().getMessageById(msgId)
        assertNotNull(updatedRow)
        val updated = updatedRow!!.message
        assertEquals("Old Content", updated.content)
        assertEquals(0, updated.pendingSync)
        assertEquals(1000L, updated.updatedAt)
    }

    @Test
    @DisplayName("Scenario 1.6: Security Rollback on DB Write Failure")
    fun testSecurityRollbackOnDbWriteFailure() = runTest {
        // Given: Seed Chat and Message
        val chatId = ChatId("chat-1")
        val chatEntity = ChatEntity(
            id = chatId,
            name = "Chat",
            created = Date(),
            lastModified = Date(),
            pinned = false
        )
        database.chatDao().upsert(chatEntity)

        val msgId = MessageId("msg-1")
        val message = MessageEntity(
            id = msgId,
            chatId = chatId,
            content = "Old Content",
            role = Role.ASSISTANT,
            pendingSync = 0,
            updatedAt = 1000L
        )
        database.messageDao().upsert(message)

        // Spy the MessageDao and make it throw on update
        val spiedMessageDao = spyk(database.messageDao())
        coEvery { spiedMessageDao.update(any()) } throws RuntimeException("Database write failure")

        val errorApplier = SensitiveRoomReadRepairApplier(
            chatDao = database.chatDao(),
            messageDao = spiedMessageDao,
            messageFileDao = database.messageFileDao(),
            chatSummaryDao = database.chatSummaryDao(),
            memoriesDao = database.memoriesDao(),
            messageVisionAnalysisDao = database.messageVisionAnalysisDao(),
            webSearchSourceDao = database.webSearchSourceDao(),
            messageTranscriptSegmentDao = database.messageTranscriptSegmentDao(),
            documentDao = database.documentDao(),
            apiCredentialsDao = database.apiCredentialsDao(),
            apiModelConfigurationsDao = database.apiModelConfigurationsDao(),
            localModelConfigurationsDao = database.localModelConfigurationsDao(),
            ttsProviderDao = database.ttsProviderDao(),
            mediaProviderDao = database.mediaProviderDao(),
            studioAlbumDao = database.studioAlbumDao(),
            studioMediaDao = database.studioMediaDao(),
        )

        // When/Then: apply throws exception and database state is not updated
        val repairs = listOf(
            SensitiveRoomReadRepair(
                ref = SensitiveFieldRef(owner = "MessageEntity", field = "content", boundary = PersistenceBoundary.ROOM),
                entityId = msgId.value,
                encryptedValue = "Repaired Content"
            )
        )

        assertThrows<RuntimeException> {
            errorApplier.apply(repairs)
        }

        // Verify that database content remains unchanged
        val originalRow = database.messageDao().getMessageById(msgId)
        assertNotNull(originalRow)
        assertEquals("Old Content", originalRow!!.message.content)
    }
}
