package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncReconciliationTest {
    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val chatSummaryDao = mockk<ChatSummaryDao>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataSource: FirestoreDataSource
    private val chatListenerSlot = slot<EventListener<QuerySnapshot>>()

    @Before
    fun setup() {
        every { cryptoManager.hasCachedKey() } returns true
        every { cryptoManager.decrypt(any()) } answers { Result.success(String(it.invocation.args[0] as ByteArray)) }
        dataSource =
            FirestoreDataSource(
                documentDao = mockk(relaxed = true),
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                messageTranscriptSegmentDao = mockk(relaxed = true),
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { "test-user-id" },
                apiCredentialsDao = mockk(relaxed = true),
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                localModelConfigurationsDao = mockk(relaxed = true),
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = mockk(relaxed = true),
                studioAlbumDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiKeyManager = mockk(relaxed = true),
                messageVisionAnalysisDao = mockk(relaxed = true),
                messageFileDao = mockk(relaxed = true),
                webSearchSourceDao = mockk(relaxed = true),
                chatSummaryDao = chatSummaryDao,
                embeddingCoordinator = mockk(relaxed = true),
                embeddingDao = mockk(relaxed = true),
            )

        val chatCollection = mockk<CollectionReference>(relaxed = true)
        every { firestore.collection("users/test-user-id/chats") } returns chatCollection
        every { chatCollection.addSnapshotListener(capture(chatListenerSlot)) } returns mockk(relaxed = true)

        dataSource.startListening()
    }

    @Test
    fun remoteNewerWins() =
        testScope.runTest {
            val chatId = ChatId("chat-1")
            val localChat =
                ChatEntity(
                    id = chatId,
                    name = "Old Local Name",
                    created = java.util.Date(0),
                    lastModified = java.util.Date(0),
                    pinned = false,
                    updatedAt = 1000L,
                    deleted = false,
                )
            val remoteDoc = mockChatDoc(chatId, "Newer Remote", 2000L, false)

            coEvery { chatDao.getChatById(chatId) } returns localChat

            val snapshot = mockSnapshot(listOf(remoteDoc), DocumentChange.Type.ADDED)
            chatListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify {
                chatDao.upsert(
                    match {
                        it.id.value == chatId.value && it.name == "Newer Remote" && it.updatedAt == 2000L
                    },
                )
            }
        }

    @Test
    fun localNewerWins() =
        testScope.runTest {
            val chatId = ChatId("chat-1")
            val localChat =
                ChatEntity(
                    id = chatId,
                    name = "New Local Name",
                    created = java.util.Date(0),
                    lastModified = java.util.Date(0),
                    pinned = false,
                    updatedAt = 2000L,
                    deleted = false,
                )
            val remoteDoc = mockChatDoc(chatId, "Old Remote Name", 1000L, false)

            coEvery { chatDao.getChatById(chatId) } returns localChat

            val snapshot = mockSnapshot(listOf(remoteDoc), DocumentChange.Type.ADDED)
            chatListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify(exactly = 0) {
                chatDao.upsert(any())
            }
        }

    @Test
    fun localTombstoneWins() =
        testScope.runTest {
            val chatId = ChatId("chat-1")
            val localChat =
                ChatEntity(
                    id = chatId,
                    name = "Deleted Local",
                    created = java.util.Date(0),
                    lastModified = java.util.Date(0),
                    pinned = false,
                    updatedAt = 2000L,
                    deleted = true,
                )
            val remoteDoc = mockChatDoc(chatId, "Zombie Update", 1000L, false)

            coEvery { chatDao.getChatById(chatId) } returns localChat

            val snapshot = mockSnapshot(listOf(remoteDoc), DocumentChange.Type.ADDED)
            chatListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify(exactly = 0) {
                chatDao.upsert(any())
            }
        }

    @Test
    fun remoteTombstoneWins() =
        testScope.runTest {
            val chatId = ChatId("chat-1")
            val localChat =
                ChatEntity(
                    id = chatId,
                    name = "Active Local",
                    created = java.util.Date(0),
                    lastModified = java.util.Date(0),
                    pinned = false,
                    updatedAt = 1000L,
                    deleted = false,
                )
            val remoteDoc = mockChatDoc(chatId, "Active Local", 2000L, true)

            coEvery { chatDao.getChatById(chatId) } returns localChat

            val snapshot = mockSnapshot(listOf(remoteDoc), DocumentChange.Type.MODIFIED)
            chatListenerSlot.captured.onEvent(snapshot, null)

            advanceUntilIdle()
            coVerify {
                chatDao.upsert(
                    match {
                        it.id.value == chatId.value && it.deleted == true && it.updatedAt == 2000L
                    },
                )
            }
        }

    private fun mockChatDoc(
        id: ChatId,
        name: String,
        updatedAt: Long,
        deleted: Boolean,
    ): QueryDocumentSnapshot {
        val doc = mockk<QueryDocumentSnapshot>(relaxed = true)
        every { doc.id } returns id.value
        every { doc.getBlob("name") } returns Blob.fromBytes(name.toByteArray())
        every { doc.getLong("updated_at") } returns updatedAt
        every { doc.getBoolean("deleted") } returns deleted
        // Mock other required fields if any
        every { doc.getLong("last_modified") } returns 0L
        every { doc.getBoolean("pinned") } returns false
        every { doc.getLong("pending_sync") } returns 0L
        return doc
    }

    private fun mockSnapshot(
        docs: List<QueryDocumentSnapshot>,
        changeType: DocumentChange.Type,
    ): QuerySnapshot {
        val snapshot = mockk<QuerySnapshot>()
        val changes =
            docs.map { doc ->
                val change = mockk<DocumentChange>()
                every { change.type } returns changeType
                every { change.document } returns doc
                change
            }
        every { snapshot.documentChanges } returns changes
        every { snapshot.size() } returns docs.size
        return snapshot
    }
}
