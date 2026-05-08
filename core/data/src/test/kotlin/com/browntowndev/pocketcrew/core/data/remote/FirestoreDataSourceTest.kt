package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageWithFiles
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelEntity
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Blob
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreDataSourceTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val apiCredentialsDao = mockk<com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao>(relaxed = true)
    private val apiKeyManager = mockk<com.browntowndev.pocketcrew.core.data.security.ApiKeyManager>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val embeddingCoordinator = mockk<com.browntowndev.pocketcrew.core.data.sync.EmbeddingRegenerationCoordinator>(relaxed = true)
    private val embeddingDao = mockk<com.browntowndev.pocketcrew.core.data.local.EmbeddingDao>(relaxed = true)
    private lateinit var dataSource: FirestoreDataSource

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        every { cryptoManager.hasCachedKey() } returns true
        every { cryptoManager.encrypt(any()) } returns Result.success("encrypted".toByteArray())
        every { cryptoManager.decrypt(any()) } answers { Result.success(String(it.invocation.args[0] as ByteArray)) }

        every { firestore.collection(any()) } returns mockk(relaxed = true)
        every { firestore.document(any()) } returns mockk(relaxed = true)

        dataSource = FirestoreDataSource(
            firestore = firestore,
            chatDao = chatDao,
            messageDao = messageDao,
            cryptoManager = cryptoManager,
            loggingPort = loggingPort,
            externalScope = testScope,
            userIdProvider = { "test-user-id" },
            apiCredentialsDao = apiCredentialsDao,
            apiModelConfigurationsDao = mockk(relaxed = true),
            localModelsDao = mockk(relaxed = true),
            localModelConfigurationsDao = mockk(relaxed = true),
            defaultModelsDao = mockk(relaxed = true),
            studioMediaDao = mockk(relaxed = true),
            studioAlbumDao = mockk(relaxed = true),
            memoriesDao = mockk(relaxed = true),
            ttsProviderDao = mockk(relaxed = true),
            mediaProviderDao = mockk(relaxed = true),
            apiKeyManager = apiKeyManager,
            messageVisionAnalysisDao = mockk(relaxed = true),
            messageFileDao = mockk(relaxed = true),
            webSearchSourceDao = mockk(relaxed = true),
            embeddingCoordinator = embeddingCoordinator,
            embeddingDao = embeddingDao
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Nested
    @DisplayName("startListening")
    inner class StartListeningTests {

        @Test
        @DisplayName("registers snapshot listeners for chats and messages collections")
        fun registersSnapshotListeners() {
            val chatsCollection = mockk<CollectionReference>(relaxed = true)
            val messagesCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
            every { firestore.collection("users/test-user-id/messages") } returns messagesCollection

            dataSource.startListening()

            verify { chatsCollection.addSnapshotListener(any()) }
            verify { messagesCollection.addSnapshotListener(any()) }
        }

        @Test
        @DisplayName("does nothing when userId is null")
        fun doesNothingWhenUserIdNull() {
            val nullUserIdSource = FirestoreDataSource(
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { null },
                apiCredentialsDao = apiCredentialsDao,
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                localModelConfigurationsDao = mockk(relaxed = true),
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = mockk(relaxed = true),
                studioAlbumDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiKeyManager = apiKeyManager,
                messageVisionAnalysisDao = mockk(relaxed = true),
                messageFileDao = mockk(relaxed = true),
                webSearchSourceDao = mockk(relaxed = true),
                embeddingCoordinator = embeddingCoordinator,
                embeddingDao = embeddingDao
            )

            nullUserIdSource.startListening()

            verify(exactly = 0) { firestore.collection(any()) }
        }
    }

    @Nested
    @DisplayName("stopListening")
    inner class StopListeningTests {

        @Test
        @DisplayName("removes all snapshot listeners")
        fun removesAllListeners() {
            val chatsCollection = mockk<CollectionReference>(relaxed = true)
            val messagesCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
            every { firestore.collection("users/test-user-id/messages") } returns messagesCollection
            val chatsListenerReg = mockk<ListenerRegistration>(relaxed = true)
            val messagesListenerReg = mockk<ListenerRegistration>(relaxed = true)
            every { chatsCollection.addSnapshotListener(any()) } returns chatsListenerReg
            every { messagesCollection.addSnapshotListener(any()) } returns messagesListenerReg

            dataSource.startListening()
            dataSource.stopListening()

            verify { chatsListenerReg.remove() }
            verify { messagesListenerReg.remove() }
        }

        @Test
        @DisplayName("clears listener registrations after stopping")
        fun clearsRegistrations() {
            val chatsCollection = mockk<CollectionReference>(relaxed = true)
            val messagesCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
            every { firestore.collection("users/test-user-id/messages") } returns messagesCollection
            every { chatsCollection.addSnapshotListener(any()) } returns mockk(relaxed = true)
            every { messagesCollection.addSnapshotListener(any()) } returns mockk(relaxed = true)

            dataSource.startListening()
            dataSource.stopListening()

            // Calling stopListening again should not crash (registrations already cleared)
            dataSource.stopListening()
        }
    }

    @Nested
    @DisplayName("chats snapshot listener")
    inner class ChatsSnapshotTests {

        private val chatsListenerSlot = slot<EventListener<QuerySnapshot>>()

        @BeforeEach
        fun setupChatsListener() {
            val chatsCollection = mockk<CollectionReference>(relaxed = true)
            val messagesCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
            every { firestore.collection("users/test-user-id/messages") } returns messagesCollection
            every { chatsCollection.addSnapshotListener(capture(chatsListenerSlot)) } returns mockk(relaxed = true)
            every { messagesCollection.addSnapshotListener(any()) } returns mockk(relaxed = true)

            dataSource.startListening()
        }

        @Test
        @DisplayName("ADDED inserts new chat when not in local DB")
        fun addedInsertsNewChat() = testScope.runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "chat-1"
            every { mockDoc.getBlob("name") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getLong("created_at") } returns 1000L
            every { mockDoc.getLong("last_modified") } returns 2000L
            every { mockDoc.getBoolean("pinned") } returns false
            every { mockDoc.getLong("updated_at") } returns 3000L
            every { mockDoc.getBoolean("deleted") } returns false

            coEvery { chatDao.getChatById(ChatId("chat-1")) } returns null

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            chatsListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify {
                chatDao.insert(any())
            }
        }

        @Test
        @DisplayName("ADDED skips insert when chat already exists and is NOT a placeholder")
        fun addedSkipsExistingChat() = testScope.runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "chat-1"
            every { mockDoc.getBlob("name") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getLong("created_at") } returns 1000L
            every { mockDoc.getLong("last_modified") } returns 2000L
            every { mockDoc.getBoolean("pinned") } returns false
            every { mockDoc.getLong("updated_at") } returns 3000L
            every { mockDoc.getBoolean("deleted") } returns false

            coEvery { chatDao.getChatById(ChatId("chat-1")) } returns ChatEntity(
                id = ChatId("chat-1"),
                name = "Existing",
                created = Date(1000L), // NOT a placeholder
                lastModified = Date(1100L),
                pinned = false
            )

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            chatsListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify(exactly = 0) { chatDao.insert(any()) }
        }

        @Test
        @DisplayName("ADDED replaces chat when it exists locally as a placeholder")
        fun addedReplacesPlaceholderChat() = testScope.runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
            every { mockDoc.id } returns "chat-1"
            every { mockDoc.getBlob("name") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getLong("created_at") } returns 1000L
            every { mockDoc.getLong("last_modified") } returns 2000L
            every { mockDoc.getBoolean("pinned") } returns false
            every { mockDoc.getLong("updated_at") } returns 3000L
            every { mockDoc.getBoolean("deleted") } returns false

            coEvery { chatDao.getChatById(ChatId("chat-1")) } returns ChatEntity(
                id = ChatId("chat-1"),
                name = "Syncing...",
                created = Date(0L), // Placeholder sentinel value
                lastModified = Date(0L),
                pinned = false,
                updatedAt = 0L
            )

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            chatsListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify {
                chatDao.insert(match { it.id.value == "chat-1" && it.name == "encrypted" && it.created.time == 1000L })
            }
        }

        @Test
        @DisplayName("MODIFIED upserts chat into local database")
        fun modifiedUpsertsChat() = testScope.runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "chat-1"
            every { mockDoc.getBlob("name") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getLong("created_at") } returns 1000L
            every { mockDoc.getLong("last_modified") } returns 2000L
            every { mockDoc.getBoolean("pinned") } returns true
            every { mockDoc.getLong("updated_at") } returns 3000L
            every { mockDoc.getBoolean("deleted") } returns false

            coEvery { chatDao.getChatById(ChatId("chat-1")) } returns ChatEntity(
                id = ChatId("chat-1"),
                name = "Old",
                created = java.util.Date(1000L),
                lastModified = java.util.Date(1100L),
                pinned = false,
                updatedAt = 2000L
            )

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.MODIFIED)
            chatsListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify {
                chatDao.insert(match {
                    it.id.value == "chat-1" && it.name == "encrypted" && it.pinned
                })
            }
        }

        @Test
        @DisplayName("REMOVED deletes chat from local database")
        fun removedDeletesChat() = testScope.runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "chat-1"

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.REMOVED)
            chatsListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify { chatDao.softDeleteById(ChatId("chat-1"), any()) }
        }

        @Test
        @DisplayName("ignores snapshot on error")
        fun ignoresOnError() = testScope.runTest {
            chatsListenerSlot.captured.onEvent(
                null,
                mockk<FirebaseFirestoreException>(relaxed = true)
            )

            coVerify(exactly = 0) { chatDao.insert(any()) }
            coVerify(exactly = 0) { chatDao.softDeleteById(any(), any()) }
        }
    }

    @Nested
    @DisplayName("messages snapshot listener")
    inner class MessagesSnapshotTests {

        private val messagesListenerSlot = slot<EventListener<QuerySnapshot>>()

        @BeforeEach
        fun setupMessagesListener() {
            val chatsCollection = mockk<CollectionReference>(relaxed = true)
            val messagesCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
            every { firestore.collection("users/test-user-id/messages") } returns messagesCollection
            every { chatsCollection.addSnapshotListener(any()) } returns mockk(relaxed = true)
            every { messagesCollection.addSnapshotListener(capture(messagesListenerSlot)) } returns mockk(relaxed = true)

            dataSource.startListening()
        }

        @Test
        @DisplayName("ADDED inserts new message and placeholder chat when chat not in local DB")
        fun addedInsertsNewMessageAndPlaceholder() = testScope.runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "msg-1"
            every { mockDoc.getString("chat_id") } returns "chat-1"
            every { mockDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getString("role") } returns "user"
            every { mockDoc.getString("message_state") } returns "COMPLETE"
            every { mockDoc.getLong("created_at") } returns null
            every { mockDoc.getString("model_type") } returns null
            every { mockDoc.getLong("updated_at") } returns 1000L
            every { mockDoc.getBoolean("deleted") } returns false

            coEvery { messageDao.getMessageById(MessageId("msg-1")) } returns null
            coEvery { chatDao.getChatById(ChatId("chat-1")) } returns null

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            messagesListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify {
                chatDao.insertIfAbsent(match {
                    it.id.value == "chat-1" && it.created.time == 0L && it.name == "Syncing..."
                })
                messageDao.insert(match {
                    it.id.value == "msg-1" && it.chatId.value == "chat-1" && it.content == "encrypted"
                })
            }
        }

        @Test
        @DisplayName("ADDED skips insert when message already exists locally")
        fun addedSkipsExistingMessage() = testScope.runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "msg-1"
            every { mockDoc.getString("chatId") } returns "chat-1"
            every { mockDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getString("role") } returns "USER"
            every { mockDoc.getString("messageState") } returns "COMPLETE"
            every { mockDoc.getLong("createdAt") } returns null
            every { mockDoc.getString("modelType") } returns null

            coEvery { messageDao.getMessageById(MessageId("msg-1")) } returns MessageWithFiles(
                message = MessageEntity(
                    id = MessageId("msg-1"),
                    chatId = ChatId("chat-1"),
                    content = "Existing",
                    role = Role.USER,
                    createdAt = 1000L,
                    pendingSync = 0
                ),
                files = emptyList()
            )

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            messagesListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify(exactly = 0) { messageDao.insert(any<MessageEntity>()) }
        }

        @Test
        @DisplayName("MODIFIED upserts message into local database")
        fun modifiedUpsertsMessage() = testScope.runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "msg-1"
            every { mockDoc.getString("chat_id") } returns "chat-1"
            every { mockDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getString("role") } returns "assistant"
            every { mockDoc.getString("message_state") } returns "COMPLETE"
            every { mockDoc.getLong("created_at") } returns 1000L
            every { mockDoc.getString("model_type") } returns "main"
            every { mockDoc.getLong("updated_at") } returns 2000L
            every { mockDoc.getBoolean("deleted") } returns false

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.MODIFIED)
            messagesListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify {
                messageDao.insert(match {
                    it.id.value == "msg-1" && it.content == "encrypted"
                })
            }
        }

        @Test
        @DisplayName("REMOVED deletes message from local database")
        fun removedDeletesMessage() = testScope.runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "msg-1"

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.REMOVED)
            messagesListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify { messageDao.softDeleteById(MessageId("msg-1"), any()) }
        }
    }

    @Nested
    @DisplayName("syncAllLocalDataToCloud")
    inner class SyncAllLocalDataToCloudTests {

        @Test
        @DisplayName("returns failure when user not signed in")
        fun returnsFailureWhenNotSignedIn() = testScope.runTest {
            val nullUserIdSource = FirestoreDataSource(
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { null },
                apiCredentialsDao = apiCredentialsDao,
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                localModelConfigurationsDao = mockk(relaxed = true),
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = mockk(relaxed = true),
                studioAlbumDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiKeyManager = apiKeyManager,
                messageVisionAnalysisDao = mockk(relaxed = true),
                messageFileDao = mockk(relaxed = true),
                webSearchSourceDao = mockk(relaxed = true),
                embeddingCoordinator = embeddingCoordinator,
                embeddingDao = embeddingDao
            )

            val result = nullUserIdSource.syncAllLocalDataToCloud()

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

        @Test
        @DisplayName("succeeds with empty data")
        fun succeedsWithEmptyData() = testScope.runTest {
            val result = dataSource.syncAllLocalDataToCloud()
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("pushes chats and messages to Firestore with merge")
        fun pushesDataToFirestore() = testScope.runTest {
            val chatId = ChatId("chat-1")
            val messageId = MessageId("msg-1")
            val chatEntity = ChatEntity(chatId, "Test Chat", java.util.Date(1000L), java.util.Date(2000L), false, pendingSync = 1)
            val msgEntity = MessageEntity(messageId, chatId, "Hello", role = com.browntowndev.pocketcrew.domain.model.chat.Role.USER, pendingSync = 1)
            val msgWithFiles = MessageWithFiles(msgEntity, emptyList())

            coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
            coEvery { messageDao.getClaimedForSync() } returns listOf(msgWithFiles)
            every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns Tasks.forResult(null)

            val result = dataSource.syncAllLocalDataToCloud()

            assertTrue(result.isSuccess)
            verify { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) }
            coVerify { chatDao.markSynced(listOf(chatId)) }
            coVerify { messageDao.markSynced(listOf(messageId)) }
        }

        @Test
        @DisplayName("returns failure on Firestore write error")
        fun returnsFailureOnWriteError() = testScope.runTest {
            val chatEntity = ChatEntity(ChatId("chat-1"), "Test Chat", java.util.Date(1000L), java.util.Date(2000L), false, pendingSync = 1)
            coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
            coEvery { messageDao.getClaimedForSync() } returns emptyList()
            every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                Tasks.forException(RuntimeException("Firestore write failed"))

            val result = dataSource.syncAllLocalDataToCloud()

            assertTrue(result.isFailure)
        }
    }

        @Nested
    @DisplayName("syncPendingChanges")
    inner class SyncPendingChangesTests {

        @Test
        @DisplayName("returns failure when user not signed in")
        fun returnsFailureWhenNotSignedIn() = testScope.runTest {
            val nullUserSource = FirestoreDataSource(
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { null },
                apiCredentialsDao = apiCredentialsDao,
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                localModelConfigurationsDao = mockk(relaxed = true),
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = mockk(relaxed = true),
                studioAlbumDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiKeyManager = apiKeyManager,
                messageVisionAnalysisDao = mockk(relaxed = true),
                messageFileDao = mockk(relaxed = true),
                webSearchSourceDao = mockk(relaxed = true),
                embeddingCoordinator = embeddingCoordinator,
                embeddingDao = embeddingDao
            )

            val result = nullUserSource.syncPendingChanges()

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

        @Test
        @DisplayName("returns failure when vault is locked")
        fun returnsFailureWhenVaultLocked() = testScope.runTest {
            every { cryptoManager.hasCachedKey() } returns false

            val result = dataSource.syncPendingChanges()

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("returns success and marks synced when no pending data")
        fun returnsSuccessWithNoPendingData() = testScope.runTest {
            coEvery { chatDao.getClaimedForSync() } returns emptyList()
            coEvery { messageDao.getClaimedForSync() } returns emptyList()

            val result = dataSource.syncPendingChanges()

            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { chatDao.markSynced(any()) }
            coVerify(exactly = 0) { messageDao.markSynced(any()) }
        }

        @Test
        @DisplayName("pushes pending chats and messages then marks them synced")
        fun pushesPendingDataAndMarksSynced() = testScope.runTest {
            val chatId = ChatId("chat-1")
            val messageId = MessageId("msg-1")
            val chatEntity = ChatEntity(chatId, "Test Chat", java.util.Date(1000L), java.util.Date(2000L), false, pendingSync = 1)
            val msgEntity = MessageEntity(messageId, chatId, "Hello", role = com.browntowndev.pocketcrew.domain.model.chat.Role.USER, pendingSync = 1)
            val msgWithFiles = com.browntowndev.pocketcrew.core.data.local.MessageWithFiles(msgEntity, emptyList())

            coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
            coEvery { messageDao.getClaimedForSync() } returns listOf(msgWithFiles)
            every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns com.google.android.gms.tasks.Tasks.forResult(null)

            val result = dataSource.syncPendingChanges()

            assertTrue(result.isSuccess)
            verify { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) }
            coVerify { chatDao.markSynced(listOf(chatId)) }
            coVerify { messageDao.markSynced(listOf(messageId)) }
        }

        @Test
        @DisplayName("uses multiple 500-op batches for large pending sets")
        fun usesMultipleBatchesForLargePayloads() = testScope.runTest {
            val chatId = ChatId("chat-x")
            val chats = (1..550).map { i ->
                ChatEntity(ChatId("chat-$i"), "Chat $i", java.util.Date(i.toLong()), java.util.Date(i.toLong()), false, pendingSync = 1)
            }
            coEvery { chatDao.getClaimedForSync() } returns chats
            coEvery { messageDao.getClaimedForSync() } returns emptyList()
            every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns com.google.android.gms.tasks.Tasks.forResult(null)

            val result = dataSource.syncPendingChanges()

            assertTrue(result.isSuccess)
            verify(exactly = 2) { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) }
        }

        @Test
        @DisplayName("returns failure on Firestore batch error")
        fun returnsFailureOnBatchError() = testScope.runTest {
            val chatEntity = ChatEntity(ChatId("c1"), "Chat", java.util.Date(), java.util.Date(), false, pendingSync = 1)
            coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
            coEvery { messageDao.getClaimedForSync() } returns emptyList()
            every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                com.google.android.gms.tasks.Tasks.forException(RuntimeException("Batch failed"))

            val result = dataSource.syncPendingChanges()

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { chatDao.markSynced(any()) }
        }

        @Test
        @DisplayName("existing syncEntitiesToCloud still works for backwards compat")
        fun existingSyncEntitiesToCloudStillWorks() = testScope.runTest {
            val messages = (1..550).map { i ->
                com.browntowndev.pocketcrew.domain.model.chat.Message(
                    id = MessageId("msg-$i"),
                    chatId = ChatId("chat1"),
                    content = com.browntowndev.pocketcrew.domain.model.chat.Content(text = "Hello $i"),
                    role = com.browntowndev.pocketcrew.domain.model.chat.Role.USER,
                    createdAt = 1000L + i
                )
            }
            every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns com.google.android.gms.tasks.Tasks.forResult(null)

            val result = dataSource.syncEntitiesToCloud(emptyList(), messages)

            assertTrue(result.isSuccess)
            verify(exactly = 2) { firestore.runBatch(any()) }
        }

        @Test
        @DisplayName("pushes pending api_credentials then marks them synced")
        fun pushesPendingApiCredentialsAndMarksSynced() = testScope.runTest {
            val id = com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId("cred-1")
            val encryptedKey = "encrypted-key".toByteArray()
            val entity = com.browntowndev.pocketcrew.core.data.local.ApiCredentialsEntity(
                id = id,
                displayName = "OpenAI",
                provider = com.browntowndev.pocketcrew.domain.model.inference.ApiProvider.OPENAI,
                modelId = "gpt-4",
                credentialAlias = "openai_main",
                encryptedApiKey = encryptedKey,
                pendingSync = 1
            )

            coEvery { apiCredentialsDao.getClaimedForSync() } returns listOf(entity)
            coEvery { chatDao.getClaimedForSync() } returns emptyList()
            coEvery { messageDao.getClaimedForSync() } returns emptyList()
            
            every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns Tasks.forResult(null)

            val result = dataSource.syncPendingChanges()

            assertTrue(result.isSuccess)
            coVerify { apiCredentialsDao.markSynced(listOf(id)) }
        }

        @Test
        @DisplayName("pulls api_credentials from Firestore and upserts to local DB")
        fun pullsApiCredentialsFromFirestore() = testScope.runTest {
            val messagesListenerSlot = slot<EventListener<QuerySnapshot>>()
            val credsCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/api_credentials") } returns credsCollection
            every { credsCollection.addSnapshotListener(capture(messagesListenerSlot)) } returns mockk(relaxed = true)
            
            dataSource.startListening()

            val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
            every { mockDoc.id } returns "cred-1"
            every { mockDoc.getString("display_name") } returns "OpenAI"
            every { mockDoc.getString("provider") } returns "OPENAI"
            every { mockDoc.getString("model_id") } returns "gpt-4"
            every { mockDoc.getString("credential_alias") } returns "openai_main"
            every { mockDoc.getBlob("encrypted_api_key") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getLong("updated_at") } returns 1000L
            
            coEvery { apiCredentialsDao.getById(any()) } returns null

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            messagesListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify {
                apiCredentialsDao.upsert(match {
                    it.id.value == "cred-1" && it.displayName == "OpenAI" && it.encryptedApiKey?.contentEquals("encrypted".toByteArray()) == true
                })
            }
        }
    }

    @Test
        @DisplayName("syncEntitiesToCloud uses multiple batches for more than 500 items")
        fun syncEntitiesToCloudUsesMultipleBatches() = testScope.runTest {
            val messages = (1..550).map { i ->
                Message(
                    id = MessageId("msg-$i"),
                    chatId = ChatId("chat1"),
                    content = com.browntowndev.pocketcrew.domain.model.chat.Content(text = "Hello $i"),
                    role = Role.USER,
                    createdAt = 1000L + i
                )
            }

            every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns Tasks.forResult(null)

            val result = dataSource.syncEntitiesToCloud(emptyList(), messages)

            assertTrue(result.isSuccess)
            // Verify runBatch was called twice
            verify(exactly = 2) { firestore.runBatch(any()) }
        }
    inner class VaultSaltTests {
        private val userDocRef = mockk<DocumentReference>(relaxed = true)

        @BeforeEach
        fun setup() {
            every { firestore.document("users/test-user-id") } returns userDocRef
        }

        @Test
        @DisplayName("updateVaultSalt updates the user document with the salt")
        fun updateVaultSaltSucceeds() = testScope.runTest {
            every { userDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)

            val result = dataSource.updateVaultSalt("test-salt")

            assertTrue(result.isSuccess)
            verify { userDocRef.set(mapOf("vaultSalt" to "test-salt"), SetOptions.merge()) }
        }

        @Test
        @DisplayName("getVaultSalt retrieves the salt from the user document")
        fun getVaultSaltSucceeds() = testScope.runTest {
            val snapshot = mockk<DocumentSnapshot>()
            every { snapshot.exists() } returns true
            every { snapshot.getString("vaultSalt") } returns "test-salt"
            every { userDocRef.get() } returns Tasks.forResult(snapshot)

            val result = dataSource.getVaultSalt()

            assertTrue(result.isSuccess)
            assertEquals("test-salt", result.getOrNull())
        }

        @Test
        @DisplayName("getVaultSalt returns null if document doesn't exist")
        fun getVaultSaltReturnsNullIfNoDoc() = testScope.runTest {
            val snapshot = mockk<DocumentSnapshot>()
            every { snapshot.exists() } returns false
            every { userDocRef.get() } returns Tasks.forResult(snapshot)

            val result = dataSource.getVaultSalt()

            assertTrue(result.isSuccess)
            assertEquals(null, result.getOrNull())
        }
    }

    @Nested
    @DisplayName("local model configuration snapshot listener")
    inner class LocalModelConfigurationSnapshotTests {
        private val listenerSlot = slot<EventListener<QuerySnapshot>>()
        private val localModelConfigurationsDao = mockk<com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao>(relaxed = true)

        @BeforeEach
        fun setupListener() {
            dataSource = FirestoreDataSource(
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { "test-user-id" },
                apiCredentialsDao = apiCredentialsDao,
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                localModelConfigurationsDao = localModelConfigurationsDao,
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = mockk(relaxed = true),
                studioAlbumDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiKeyManager = apiKeyManager,
                messageVisionAnalysisDao = mockk(relaxed = true),
                messageFileDao = mockk(relaxed = true),
                webSearchSourceDao = mockk(relaxed = true),
                embeddingCoordinator = embeddingCoordinator,
                embeddingDao = embeddingDao
            )

            val configCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/local_model_configs") } returns configCollection
            every { configCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

            dataSource.startListening()
        }

        @Test
        @DisplayName("ADDED updates existing configuration when ID is different but logical key matches")
        fun addedUpdatesExistingOnIdMismatch() = testScope.runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "remote-id"
            every { mockDoc.getString("local_model_id") } returns "model-1"
            every { mockDoc.getString("display_name") } returns "Logic Match"
            every { mockDoc.getDouble("temperature") } returns 0.7
            every { mockDoc.getLong("top_k") } returns 40L
            every { mockDoc.getDouble("top_p") } returns 0.9
            every { mockDoc.getDouble("min_p") } returns 0.05
            every { mockDoc.getDouble("repetition_penalty") } returns 1.1
            every { mockDoc.getLong("max_tokens") } returns 4096L
            every { mockDoc.getLong("context_window") } returns 4096L
            every { mockDoc.getBoolean("thinking_enabled") } returns false
            every { mockDoc.getString("system_prompt") } returns ""
            every { mockDoc.getLong("updated_at") } returns 2000L

            val existingEntity = LocalModelConfigurationEntity(
                id = LocalModelConfigurationId("local-id"),
                localModelId = LocalModelId("model-1"),
                displayName = "Logic Match",
                temperature = 0.5,
                updatedAt = 1000L
            )

            coEvery { localModelConfigurationsDao.getById(LocalModelConfigurationId("remote-id")) } returns null
            coEvery { localModelConfigurationsDao.getByModelIdAndName(LocalModelId("model-1"), "Logic Match") } returns existingEntity

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            listenerSlot.captured.onEvent(snapshot, null)
            testScope.testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { loggingPort.error(any(), any(), any()) }
            coVerify {
                localModelConfigurationsDao.getById(LocalModelConfigurationId("remote-id"))
                localModelConfigurationsDao.getByModelIdAndName(LocalModelId("model-1"), "Logic Match")
                localModelConfigurationsDao.upsert(match {
                    it.id.value == "local-id" && it.temperature == 0.7 && it.pendingSync == 0
                })
            }
        }
    }

    @Nested
    @DisplayName("vision analysis snapshot listener")
    inner class VisionAnalysisSnapshotTests {
        private val listenerSlot = slot<EventListener<QuerySnapshot>>()
        private val messageVisionAnalysisDao = mockk<com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisDao>(relaxed = true)

        @BeforeEach
        fun setupListener() {
            // Re-create dataSource with mocked vision analysis DAO
            dataSource = FirestoreDataSource(
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { "test-user-id" },
                apiCredentialsDao = apiCredentialsDao,
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                localModelConfigurationsDao = mockk(relaxed = true),
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = mockk(relaxed = true),
                studioAlbumDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiKeyManager = apiKeyManager,
                messageVisionAnalysisDao = messageVisionAnalysisDao,
                messageFileDao = mockk(relaxed = true),
                webSearchSourceDao = mockk(relaxed = true),
                embeddingCoordinator = embeddingCoordinator,
                embeddingDao = embeddingDao
            )

            val visionCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/vision_analysis") } returns visionCollection
            every { visionCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

            dataSource.startListening()
        }

        @Test
        @DisplayName("ADDED inserts vision analysis and placeholder message when message not in local DB")
        fun addedInsertsPlaceholderMessage() = testScope.runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "vision-1"
            every { mockDoc.getString("user_message_id") } returns "msg-1"
            every { mockDoc.getString("image_uri") } returns "file://img.jpg"
            every { mockDoc.getBlob("prompt_text") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getBlob("analysis_text") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getString("model_type") } returns "VISION"
            every { mockDoc.getString("chat_id") } returns "chat-1"
            every { mockDoc.getLong("created_at") } returns 1000L
            every { mockDoc.getLong("updated_at") } returns 2000L

            coEvery { messageDao.getMessageById(MessageId("msg-1")) } returns null
            coEvery { chatDao.getChatById(any()) } returns mockk() // Assume chat exists for placeholder message
            coEvery { messageVisionAnalysisDao.getById("vision-1") } returns null

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            listenerSlot.captured.onEvent(snapshot, null)

            coVerify {
                // Should insert a placeholder message
                messageDao.insertIfAbsent(match {
                    it.id.value == "msg-1" && it.content == "Syncing..."
                })
                // Then insert the vision analysis
                messageVisionAnalysisDao.upsert(match {
                    it.id == "vision-1" && it.userMessageId.value == "msg-1"
                })
            }
        }
    }

    @Nested
    @DisplayName("Deserializer hardening — blank FK fields return null")
    inner class DeserializerHardeningTests {

        @Test
        @DisplayName("messageEntityFromDoc returns null when chat_id is blank")
        fun messageEntityFromDoc_blankChatId_returnsNull() = testScope.runTest {
            // Fire a ADDED message event where chat_id is blank — upsertLocal should NOT be called
            val messagesListenerSlot = slot<EventListener<QuerySnapshot>>()
            val chatsCollection = mockk<CollectionReference>(relaxed = true)
            val messagesCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
            every { firestore.collection("users/test-user-id/messages") } returns messagesCollection
            every { chatsCollection.addSnapshotListener(any()) } returns mockk(relaxed = true)
            every { messagesCollection.addSnapshotListener(capture(messagesListenerSlot)) } returns mockk(relaxed = true)

            dataSource.startListening()

            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "msg-blank"
            every { mockDoc.getString("chat_id") } returns ""
            every { mockDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getString("role") } returns "user"
            every { mockDoc.getString("message_state") } returns "COMPLETE"
            every { mockDoc.getLong("created_at") } returns null
            every { mockDoc.getString("model_type") } returns null
            every { mockDoc.getLong("updated_at") } returns 1000L
            every { mockDoc.getBoolean("deleted") } returns false

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            messagesListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            // No message should be inserted because fromFirestore returned null
            coVerify(exactly = 0) { messageDao.insert(any<MessageEntity>()) }
            coVerify(exactly = 0) { messageDao.insertIfAbsent(any()) }
        }

        @Test
        @DisplayName("messageEntityFromDoc returns null when chat_id is null")
        fun messageEntityFromDoc_nullChatId_returnsNull() = testScope.runTest {
            val messagesListenerSlot = slot<EventListener<QuerySnapshot>>()
            val chatsCollection = mockk<CollectionReference>(relaxed = true)
            val messagesCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
            every { firestore.collection("users/test-user-id/messages") } returns messagesCollection
            every { chatsCollection.addSnapshotListener(any()) } returns mockk(relaxed = true)
            every { messagesCollection.addSnapshotListener(capture(messagesListenerSlot)) } returns mockk(relaxed = true)

            dataSource.startListening()

            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "msg-null"
            every { mockDoc.getString("chat_id") } returns null
            every { mockDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getString("role") } returns "user"
            every { mockDoc.getString("message_state") } returns "COMPLETE"
            every { mockDoc.getLong("created_at") } returns null
            every { mockDoc.getString("model_type") } returns null
            every { mockDoc.getLong("updated_at") } returns 1000L
            every { mockDoc.getBoolean("deleted") } returns false

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            messagesListenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify(exactly = 0) { messageDao.insert(any<MessageEntity>()) }
            coVerify(exactly = 0) { messageDao.insertIfAbsent(any()) }
        }

        @Test
        @DisplayName("vision analysis fromFirestore returns null when user_message_id is blank")
        fun visionAnalysis_fromFirestore_blankUserMessageId_returnsNull() = testScope.runTest {
            val messageVisionAnalysisDao = mockk<com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisDao>(relaxed = true)
            val localDataSource = FirestoreDataSource(
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { "test-user-id" },
                apiCredentialsDao = apiCredentialsDao,
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                localModelConfigurationsDao = mockk(relaxed = true),
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = mockk(relaxed = true),
                studioAlbumDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiKeyManager = apiKeyManager,
                messageVisionAnalysisDao = messageVisionAnalysisDao,
                messageFileDao = mockk(relaxed = true),
                webSearchSourceDao = mockk(relaxed = true),
                embeddingCoordinator = embeddingCoordinator,
                embeddingDao = embeddingDao
            )

            val listenerSlot = slot<EventListener<QuerySnapshot>>()
            val visionCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/vision_analysis") } returns visionCollection
            every { visionCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

            localDataSource.startListening()

            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "vision-blank"
            every { mockDoc.getString("user_message_id") } returns ""
            every { mockDoc.getString("image_uri") } returns "file://img.jpg"
            every { mockDoc.getBlob("prompt_text") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getBlob("analysis_text") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getString("model_type") } returns "VISION"
            every { mockDoc.getString("chat_id") } returns "chat-1"
            every { mockDoc.getLong("created_at") } returns 1000L
            every { mockDoc.getLong("updated_at") } returns 2000L

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            listenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify(exactly = 0) { messageVisionAnalysisDao.upsert(any()) }
        }

        @Test
        @DisplayName("message file fromFirestore returns null when message_id is blank")
        fun messageFile_fromFirestore_blankMessageId_returnsNull() = testScope.runTest {
            val messageFileDao = mockk<com.browntowndev.pocketcrew.core.data.local.MessageFileDao>(relaxed = true)
            val localDataSource = FirestoreDataSource(
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { "test-user-id" },
                apiCredentialsDao = apiCredentialsDao,
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                localModelConfigurationsDao = mockk(relaxed = true),
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = mockk(relaxed = true),
                studioAlbumDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiKeyManager = apiKeyManager,
                messageVisionAnalysisDao = mockk(relaxed = true),
                messageFileDao = messageFileDao,
                webSearchSourceDao = mockk(relaxed = true),
                embeddingCoordinator = embeddingCoordinator,
                embeddingDao = embeddingDao
            )

            val listenerSlot = slot<EventListener<QuerySnapshot>>()
            val fileCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/message_files") } returns fileCollection
            every { fileCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

            localDataSource.startListening()

            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "file-blank"
            every { mockDoc.getString("message_id") } returns ""
            every { mockDoc.getBlob("name") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getString("chat_id") } returns "chat-1"

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            listenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify(exactly = 0) { messageFileDao.insert(any()) }
        }

        @Test
        @DisplayName("web search source fromFirestore returns null when message_id is blank")
        fun webSearchSource_fromFirestore_blankMessageId_returnsNull() = testScope.runTest {
            val webSearchSourceDao = mockk<com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao>(relaxed = true)
            val localDataSource = FirestoreDataSource(
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { "test-user-id" },
                apiCredentialsDao = apiCredentialsDao,
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                localModelConfigurationsDao = mockk(relaxed = true),
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = mockk(relaxed = true),
                studioAlbumDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiKeyManager = apiKeyManager,
                messageVisionAnalysisDao = mockk(relaxed = true),
                messageFileDao = mockk(relaxed = true),
                webSearchSourceDao = webSearchSourceDao,
                embeddingCoordinator = embeddingCoordinator,
                embeddingDao = embeddingDao
            )

            val listenerSlot = slot<EventListener<QuerySnapshot>>()
            val searchCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/web_search_sources") } returns searchCollection
            every { searchCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

            localDataSource.startListening()

            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "search-blank"
            every { mockDoc.getString("message_id") } returns ""
            every { mockDoc.getBlob("title") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getBlob("url") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getDouble("score") } returns 0.9
            every { mockDoc.getBoolean("extracted") } returns true
            every { mockDoc.getString("chat_id") } returns "chat-1"

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            listenerSlot.captured.onEvent(snapshot, null)
            advanceUntilIdle()

            coVerify(exactly = 0) { webSearchSourceDao.upsert(any()) }
        }
    }

    private fun mockSnapshot(
        docs: List<QueryDocumentSnapshot>,
        changeType: DocumentChange.Type
    ): QuerySnapshot {
        val snapshot = mockk<QuerySnapshot>()
        val changes = docs.map { doc ->
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
