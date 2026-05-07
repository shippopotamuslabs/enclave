package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageWithFiles
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
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val testScope = TestScope(UnconfinedTestDispatcher())
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
        every { cryptoManager.decrypt(any()) } returns Result.success("decrypted")

        dataSource = FirestoreDataSource(
            firestore = firestore,
            chatDao = chatDao,
            messageDao = messageDao,
            cryptoManager = cryptoManager,
            loggingPort = loggingPort,
            externalScope = testScope,
            userIdProvider = { "test-user-id" }
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
                userIdProvider = { null }
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
        fun addedInsertsNewChat() = runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "chat-1"
            every { mockDoc.getBlob("name") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getLong("created") } returns 1000L
            every { mockDoc.getLong("lastModified") } returns 2000L
            every { mockDoc.getBoolean("pinned") } returns false

            coEvery { chatDao.getChatById(ChatId("chat-1")) } returns null

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            chatsListenerSlot.captured.onEvent(snapshot, null)

            coVerify {
                chatDao.insert(match {
                    it.id.value == "chat-1" && it.name == "decrypted"
                })
            }
        }

        @Test
        @DisplayName("ADDED skips insert when chat already exists and is NOT a placeholder")
        fun addedSkipsExistingChat() = runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "chat-1"
            every { mockDoc.getBlob("name") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getLong("created") } returns 1000L
            every { mockDoc.getLong("lastModified") } returns 2000L
            every { mockDoc.getBoolean("pinned") } returns false

            coEvery { chatDao.getChatById(ChatId("chat-1")) } returns ChatEntity(
                id = ChatId("chat-1"),
                name = "Existing",
                created = Date(1000L), // NOT a placeholder
                lastModified = Date(1100L),
                pinned = false
            )

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            chatsListenerSlot.captured.onEvent(snapshot, null)

            coVerify(exactly = 0) { chatDao.insert(any()) }
        }

        @Test
        @DisplayName("ADDED replaces chat when it exists locally as a placeholder")
        fun addedReplacesPlaceholderChat() = runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "chat-1"
            every { mockDoc.getBlob("name") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getLong("created") } returns 1000L
            every { mockDoc.getLong("lastModified") } returns 2000L
            every { mockDoc.getBoolean("pinned") } returns false

            coEvery { chatDao.getChatById(ChatId("chat-1")) } returns ChatEntity(
                id = ChatId("chat-1"),
                name = "Syncing...",
                created = Date(0L), // Placeholder sentinel value
                lastModified = Date(0L),
                pinned = false
            )

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            chatsListenerSlot.captured.onEvent(snapshot, null)

            coVerify {
                chatDao.insert(match {
                    it.id.value == "chat-1" && it.name == "decrypted" && it.created.time == 1000L
                })
            }
        }

        @Test
        @DisplayName("MODIFIED upserts chat into local database")
        fun modifiedUpsertsChat() = runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "chat-1"
            every { mockDoc.getBlob("name") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getLong("created") } returns 1000L
            every { mockDoc.getLong("lastModified") } returns 3000L
            every { mockDoc.getBoolean("pinned") } returns true

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.MODIFIED)
            chatsListenerSlot.captured.onEvent(snapshot, null)

            coVerify {
                chatDao.insert(match {
                    it.id.value == "chat-1" && it.name == "decrypted" && it.pinned
                })
            }
        }

        @Test
        @DisplayName("REMOVED deletes chat from local database")
        fun removedDeletesChat() = runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "chat-1"

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.REMOVED)
            chatsListenerSlot.captured.onEvent(snapshot, null)

            coVerify { chatDao.deleteById(ChatId("chat-1")) }
        }

        @Test
        @DisplayName("ignores snapshot on error")
        fun ignoresOnError() = runTest {
            chatsListenerSlot.captured.onEvent(
                null,
                mockk<FirebaseFirestoreException>(relaxed = true)
            )

            coVerify(exactly = 0) { chatDao.insert(any()) }
            coVerify(exactly = 0) { chatDao.deleteById(any()) }
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
        fun addedInsertsNewMessageAndPlaceholder() = runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "msg-1"
            every { mockDoc.getString("chatId") } returns "chat-1"
            every { mockDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getString("role") } returns "USER"
            every { mockDoc.getString("messageState") } returns "COMPLETE"
            every { mockDoc.getLong("createdAt") } returns null
            every { mockDoc.getString("modelType") } returns null

            coEvery { messageDao.getMessageById(MessageId("msg-1")) } returns null
            coEvery { chatDao.getChatById(ChatId("chat-1")) } returns null

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            messagesListenerSlot.captured.onEvent(snapshot, null)

            coVerify {
                chatDao.insert(match {
                    it.id.value == "chat-1" && it.created.time == 0L && it.name == "Syncing..."
                })
                messageDao.insert(match {
                    it.id.value == "msg-1" && it.chatId.value == "chat-1" && it.content == "decrypted"
                })
            }
        }

        @Test
        @DisplayName("ADDED skips insert when message already exists locally")
        fun addedSkipsExistingMessage() = runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "msg-1"
            every { mockDoc.getString("chatId") } returns "chat-1"
            every { mockDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getString("role") } returns "USER"
            every { mockDoc.getString("messageState") } returns "COMPLETE"
            every { mockDoc.getLong("createdAt") } returns null
            every { mockDoc.getString("modelType") } returns null

            coEvery { messageDao.getMessageById(MessageId("msg-1")) } returns mockk(relaxed = true)

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
            messagesListenerSlot.captured.onEvent(snapshot, null)

            coVerify(exactly = 0) { messageDao.insert(any<MessageEntity>()) }
        }

        @Test
        @DisplayName("MODIFIED upserts message into local database")
        fun modifiedUpsertsMessage() = runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "msg-1"
            every { mockDoc.getString("chatId") } returns "chat-1"
            every { mockDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
            every { mockDoc.getString("role") } returns "ASSISTANT"
            every { mockDoc.getString("messageState") } returns "COMPLETE"
            every { mockDoc.getLong("createdAt") } returns 1000L
            every { mockDoc.getString("modelType") } returns "MAIN"

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.MODIFIED)
            messagesListenerSlot.captured.onEvent(snapshot, null)

            coVerify {
                messageDao.insert(match {
                    it.id.value == "msg-1" && it.content == "decrypted"
                })
            }
        }

        @Test
        @DisplayName("REMOVED deletes message from local database")
        fun removedDeletesMessage() = runTest {
            val mockDoc = mockk<QueryDocumentSnapshot>()
            every { mockDoc.id } returns "msg-1"

            val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.REMOVED)
            messagesListenerSlot.captured.onEvent(snapshot, null)

            coVerify { messageDao.deleteById(MessageId("msg-1")) }
        }
    }

    @Nested
    @DisplayName("syncAllLocalDataToCloud")
    inner class SyncAllLocalDataToCloudTests {

        @Test
        @DisplayName("returns failure when user not signed in")
        fun returnsFailureWhenNotSignedIn() = runTest {
            val nullUserIdSource = FirestoreDataSource(
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { null }
            )

            val result = nullUserIdSource.syncAllLocalDataToCloud()

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

        @Test
        @DisplayName("succeeds with empty data")
        fun succeedsWithEmptyData() = runTest {
            coEvery { chatDao.getAllChats() } returns flowOf(emptyList())
            coEvery { messageDao.getAllMessages() } returns flowOf(emptyList())

            val result = dataSource.syncAllLocalDataToCloud()

            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("pushes chats and messages to Firestore with merge")
        fun pushesDataToFirestore() = runTest {
            val chatEntity = ChatEntity(
                id = ChatId("chat-1"),
                name = "Test Chat",
                created = Date(1000L),
                lastModified = Date(2000L),
                pinned = false
            )
            coEvery { chatDao.getAllChats() } returns flowOf(listOf(chatEntity))

            val messageWithFiles = mockk<MessageWithFiles>(relaxed = true)
            every { messageWithFiles.message } returns mockk(relaxed = true) {
                every { id } returns MessageId("msg-1")
                every { chatId } returns ChatId("chat-1")
                every { content } returns "Hello"
                every { role.name } returns "USER"
                every { messageState.name } returns "COMPLETE"
                every { createdAt } returns 1000L
                every { modelType } returns null
            }
            coEvery { messageDao.getAllMessages() } returns flowOf(listOf(messageWithFiles))

            val chatDocRef = mockk<DocumentReference>()
            val msgDocRef = mockk<DocumentReference>()
            val chatsCollection = mockk<CollectionReference>()
            val messagesCollection = mockk<CollectionReference>()
            every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
            every { chatsCollection.document("chat-1") } returns chatDocRef
            every { firestore.collection("users/test-user-id/messages") } returns messagesCollection
            every { messagesCollection.document("msg-1") } returns msgDocRef

            every { chatDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)
            every { msgDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)

            val result = dataSource.syncAllLocalDataToCloud()

            assertTrue(result.isSuccess)
            verify { chatDocRef.set(any(), any<SetOptions>()) }
            verify { msgDocRef.set(any(), any<SetOptions>()) }
        }

        @Test
        @DisplayName("returns failure on Firestore write error")
        fun returnsFailureOnWriteError() = runTest {
            val chatEntity = ChatEntity(
                id = ChatId("chat-1"),
                name = "Test Chat",
                created = Date(1000L),
                lastModified = Date(2000L),
                pinned = false
            )
            coEvery { chatDao.getAllChats() } returns flowOf(listOf(chatEntity))
            coEvery { messageDao.getAllMessages() } returns flowOf(emptyList())

            val chatDocRef = mockk<DocumentReference>()
            val chatsCollection = mockk<CollectionReference>()
            every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
            every { chatsCollection.document("chat-1") } returns chatDocRef

            val exception = RuntimeException("Firestore write failed")
            every { chatDocRef.set(any(), any<SetOptions>()) } returns Tasks.forException(exception)

            val result = dataSource.syncAllLocalDataToCloud()

            assertTrue(result.isFailure)
        }
    }

        @Test
        @DisplayName("syncEntitiesToCloud uses multiple batches for more than 500 items")
        fun syncEntitiesToCloudUsesMultipleBatches() = runTest {
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
        fun updateVaultSaltSucceeds() = runTest {
            every { userDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)

            val result = dataSource.updateVaultSalt("test-salt")

            assertTrue(result.isSuccess)
            verify { userDocRef.set(mapOf("vaultSalt" to "test-salt"), SetOptions.merge()) }
        }

        @Test
        @DisplayName("getVaultSalt retrieves the salt from the user document")
        fun getVaultSaltSucceeds() = runTest {
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
        fun getVaultSaltReturnsNullIfNoDoc() = runTest {
            val snapshot = mockk<DocumentSnapshot>()
            every { snapshot.exists() } returns false
            every { userDocRef.get() } returns Tasks.forResult(snapshot)

            val result = dataSource.getVaultSalt()

            assertTrue(result.isSuccess)
            assertEquals(null, result.getOrNull())
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
