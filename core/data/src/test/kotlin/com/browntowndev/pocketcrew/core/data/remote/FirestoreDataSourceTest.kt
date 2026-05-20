package com.browntowndev.pocketcrew.core.data.remote

import android.util.Log
import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryDao
import com.browntowndev.pocketcrew.core.data.local.DocumentDao
import com.browntowndev.pocketcrew.core.data.local.DocumentEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageWithFiles
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

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
    private val chatSummaryDao = mockk<ChatSummaryDao>(relaxed = true)
    private val documentDao = mockk<DocumentDao>(relaxed = true)
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

        dataSource =
            FirestoreDataSource(
                documentDao = documentDao,
                firestore = firestore,
                chatDao = chatDao,
                messageDao = messageDao,
                messageTranscriptSegmentDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                webSearchSourceDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                apiCredentialsDao = apiCredentialsDao,
                localModelConfigurationsDao = mockk(relaxed = true),
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = mockk(relaxed = true),
                studioAlbumDao = mockk(relaxed = true),
                messageFileDao = mockk(relaxed = true),
                messageVisionAnalysisDao = mockk(relaxed = true),
                chatSummaryDao = chatSummaryDao,
                embeddingDao = embeddingDao,
                localEncryption = FakeLocalDataEncryptionService(),
                apiKeyManager = apiKeyManager,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { "test-user-id" },
                embeddingCoordinator = embeddingCoordinator,
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
            val nullUserIdSource =
                FirestoreDataSource(
                    documentDao = mockk(relaxed = true),
                    firestore = firestore,
                    chatDao = chatDao,
                    messageDao = messageDao,
                    messageTranscriptSegmentDao = mockk(relaxed = true),
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
                    chatSummaryDao = chatSummaryDao,
                    embeddingCoordinator = embeddingCoordinator,
                    embeddingDao = embeddingDao,
                    localEncryption = FakeLocalDataEncryptionService(),
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
        fun addedInsertsNewChat() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
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
                    chatDao.upsert(any())
                }
            }

        @Test
        @DisplayName("ADDED skips insert when chat already exists and is NOT a placeholder")
        fun addedSkipsExistingChat() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "chat-1"
                every { mockDoc.getBlob("name") } returns Blob.fromBytes("encrypted".toByteArray())
                every { mockDoc.getLong("created_at") } returns 1000L
                every { mockDoc.getLong("last_modified") } returns 2000L
                every { mockDoc.getBoolean("pinned") } returns false
                every { mockDoc.getLong("updated_at") } returns 3000L
                every { mockDoc.getBoolean("deleted") } returns false

                coEvery { chatDao.getChatById(ChatId("chat-1")) } returns
                    ChatEntity(
                        id = ChatId("chat-1"),
                        name = "Existing",
                        created = Date(1000L), // NOT a placeholder
                        lastModified = Date(1100L),
                        pinned = false,
                    )

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                chatsListenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify(exactly = 0) { chatDao.upsert(any()) }
            }

        @Test
        @DisplayName("ADDED replaces chat when it exists locally as a placeholder")
        fun addedReplacesPlaceholderChat() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "chat-1"
                every { mockDoc.getBlob("name") } returns Blob.fromBytes("encrypted".toByteArray())
                every { mockDoc.getLong("created_at") } returns 1000L
                every { mockDoc.getLong("last_modified") } returns 2000L
                every { mockDoc.getBoolean("pinned") } returns false
                every { mockDoc.getLong("updated_at") } returns 3000L
                every { mockDoc.getBoolean("deleted") } returns false

                coEvery { chatDao.getChatById(ChatId("chat-1")) } returns
                    ChatEntity(
                        id = ChatId("chat-1"),
                        name = "Syncing...",
                        created = Date(0L), // Placeholder sentinel value
                        lastModified = Date(0L),
                        pinned = false,
                        updatedAt = 0L,
                    )

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                chatsListenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    chatDao.upsert(match { it.id.value == "chat-1" && it.name == "encrypted" && it.created.time == 1000L })
                }
            }

        @Test
        @DisplayName("MODIFIED upserts chat into local database")
        fun modifiedUpsertsChat() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "chat-1"
                every { mockDoc.getBlob("name") } returns Blob.fromBytes("encrypted".toByteArray())
                every { mockDoc.getLong("created_at") } returns 1000L
                every { mockDoc.getLong("last_modified") } returns 2000L
                every { mockDoc.getBoolean("pinned") } returns true
                every { mockDoc.getLong("updated_at") } returns 3000L
                every { mockDoc.getBoolean("deleted") } returns false

                coEvery { chatDao.getChatById(ChatId("chat-1")) } returns
                    ChatEntity(
                        id = ChatId("chat-1"),
                        name = "Old",
                        created = java.util.Date(1000L),
                        lastModified = java.util.Date(1100L),
                        pinned = false,
                        updatedAt = 2000L,
                    )

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.MODIFIED)
                chatsListenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    chatDao.upsert(
                        match {
                            it.id.value == "chat-1" && it.name == "encrypted" && it.pinned
                        },
                    )
                }
            }

        @Test
        @DisplayName("REMOVED deletes chat from local database")
        fun removedDeletesChat() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "chat-1"

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.REMOVED)
                chatsListenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    chatDao.softDeleteById(ChatId("chat-1"), any())
                    documentDao.softDeleteByChatId(ChatId("chat-1"), any())
                }
            }

        @Test
        @DisplayName("ignores snapshot on error")
        fun ignoresOnError() =
            testScope.runTest {
                chatsListenerSlot.captured.onEvent(
                    null,
                    mockk<FirebaseFirestoreException>(relaxed = true),
                )

                coVerify(exactly = 0) { chatDao.upsert(any()) }
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
        fun addedInsertsNewMessageAndPlaceholder() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
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
                    chatDao.insertIfAbsent(
                        match {
                            it.id.value == "chat-1" && it.created.time == 0L && it.name == "PCENC:v1:aes-gcm:Syncing..."
                        },
                    )
                    messageDao.upsert(
                        match {
                            it.id.value == "msg-1" && it.chatId.value == "chat-1" && it.content == "encrypted"
                        },
                    )
                }
            }

        @Test
        @DisplayName("ADDED skips insert when message already exists locally")
        fun addedSkipsExistingMessage() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "msg-1"
                every { mockDoc.getString("chat_id") } returns "chat-1"
                every { mockDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
                every { mockDoc.getString("role") } returns "USER"
                every { mockDoc.getString("message_state") } returns "COMPLETE"
                every { mockDoc.getLong("created_at") } returns null
                every { mockDoc.getString("model_type") } returns null

                coEvery { messageDao.getMessageById(MessageId("msg-1")) } returns
                    MessageWithFiles(
                        message =
                            MessageEntity(
                                id = MessageId("msg-1"),
                                chatId = ChatId("chat-1"),
                                content = "Existing",
                                role = Role.USER,
                                createdAt = 1000L,
                                pendingSync = 0,
                            ),
                        files = emptyList(),
                    )

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                messagesListenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify(exactly = 0) { messageDao.upsert(any<MessageEntity>()) }
            }

        @Test
        @DisplayName("ADDED skips no-op message write when remote equals local clean row")
        fun addedSkipsNoOpMessageWriteWhenRemoteEqualsLocal() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "msg-1"
                every { mockDoc.getString("chat_id") } returns "chat-1"
                every { mockDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
                every { mockDoc.getString("role") } returns "USER"
                every { mockDoc.getString("message_state") } returns "COMPLETE"
                every { mockDoc.getLong("created_at") } returns 1000L
                every { mockDoc.getString("model_type") } returns null
                every { mockDoc.getString("user_message_id") } returns null
                every { mockDoc.getString("image_uris_json") } returns null
                every { mockDoc.getBlob("image_uris_json") } returns null
                every { mockDoc.getLong("thinking_duration_seconds") } returns null
                every { mockDoc.getString("thinking_raw") } returns null
                every { mockDoc.getBlob("thinking_raw") } returns null
                every { mockDoc.getLong("thinking_start_time") } returns null
                every { mockDoc.getLong("thinking_end_time") } returns null
                every { mockDoc.getString("pipeline_step") } returns null
                every { mockDoc.getString("artifacts_json") } returns null
                every { mockDoc.getBlob("artifacts_json") } returns null
                every { mockDoc.getString("generated_media_uris_json") } returns null
                every { mockDoc.getBlob("generated_media_uris_json") } returns null
                every { mockDoc.getString("generated_media_asset_ids_json") } returns null
                every { mockDoc.getBlob("generated_media_asset_ids_json") } returns null
                every { mockDoc.getString("generated_media_types_json") } returns null
                every { mockDoc.getString("generated_media_prompts_json") } returns null
                every { mockDoc.getBlob("generated_media_prompts_json") } returns null
                every { mockDoc.getLong("updated_at") } returns 2000L
                every { mockDoc.getBoolean("deleted") } returns false

                val existing =
                    MessageEntity(
                        id = MessageId("msg-1"),
                        chatId = ChatId("chat-1"),
                        content = "encrypted",
                        role = Role.USER,
                        messageState = com.browntowndev.pocketcrew.domain.model.chat.MessageState.COMPLETE,
                        createdAt = 1000L,
                        pendingSync = 0,
                        updatedAt = 2000L,
                    )
                coEvery { messageDao.getMessageById(MessageId("msg-1")) } returns
                    MessageWithFiles(
                        message = existing,
                        files = emptyList(),
                    )

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                messagesListenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify(exactly = 0) { messageDao.upsert(any<MessageEntity>()) }
            }

        @Test
        @DisplayName("MODIFIED upserts message into local database")
        fun modifiedUpsertsMessage() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
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
                    messageDao.upsert(
                        match {
                            it.id.value == "msg-1" && it.content == "encrypted"
                        },
                    )
                }
            }

        @Test
        @DisplayName("REMOVED deletes message from local database")
        fun removedDeletesMessage() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "msg-1"

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.REMOVED)
                messagesListenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    messageDao.softDeleteById(MessageId("msg-1"), any())
                    documentDao.softDeleteByMessageId(MessageId("msg-1"), any())
                }
            }
    }

    @Nested
    @DisplayName("syncAllLocalDataToCloud")
    inner class SyncAllLocalDataToCloudTests {
        @Test
        @DisplayName("returns failure when user not signed in")
        fun returnsFailureWhenNotSignedIn() =
            testScope.runTest {
                val nullUserIdSource =
                    FirestoreDataSource(
                        documentDao = mockk(relaxed = true),
                        firestore = firestore,
                        chatDao = chatDao,
                        messageDao = messageDao,
                        messageTranscriptSegmentDao = mockk(relaxed = true),
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
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )

                val result = nullUserIdSource.syncAllLocalDataToCloud()

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IllegalStateException)
            }

        @Test
        @DisplayName("succeeds with empty data")
        fun succeedsWithEmptyData() =
            testScope.runTest {
                val result = dataSource.syncAllLocalDataToCloud()
                assertTrue(result.isSuccess)
            }

        @Test
        @DisplayName("pushes chats and messages to Firestore with merge")
        fun pushesDataToFirestore() =
            testScope.runTest {
                val chatId = ChatId("chat-1")
                val messageId = MessageId("msg-1")
                val chatEntity =
                    ChatEntity(
                        chatId,
                        "PCENC:v1:aes-gcm:Test Chat",
                        java.util.Date(1000L),
                        java.util.Date(2000L),
                        false,
                        pendingSync = 1,
                    )
                val msgEntity =
                    MessageEntity(
                        messageId,
                        chatId,
                        "PCENC:v1:aes-gcm:Hello",
                        role = com.browntowndev.pocketcrew.domain.model.chat.Role.USER,
                        pendingSync = 1,
                    )
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
        fun returnsFailureOnWriteError() =
            testScope.runTest {
                val chatEntity =
                    ChatEntity(ChatId("chat-1"), "Test Chat", java.util.Date(1000L), java.util.Date(2000L), false, pendingSync = 1)
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
        fun returnsFailureWhenNotSignedIn() =
            testScope.runTest {
                val nullUserSource =
                    FirestoreDataSource(
                        documentDao = mockk(relaxed = true),
                        firestore = firestore,
                        chatDao = chatDao,
                        messageDao = messageDao,
                        messageTranscriptSegmentDao = mockk(relaxed = true),
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
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )

                val result = nullUserSource.syncPendingChanges()

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IllegalStateException)
            }

        @Test
        @DisplayName("returns failure when vault is locked")
        fun returnsFailureWhenVaultLocked() =
            testScope.runTest {
                every { cryptoManager.hasCachedKey() } returns false

                val result = dataSource.syncPendingChanges()

                assertTrue(result.isFailure)
            }

        @Test
        @DisplayName("returns success and marks synced when no pending data")
        fun returnsSuccessWithNoPendingData() =
            testScope.runTest {
                coEvery { chatDao.getClaimedForSync() } returns emptyList()
                coEvery { messageDao.getClaimedForSync() } returns emptyList()

                val result = dataSource.syncPendingChanges()

                assertTrue(result.isSuccess)
                coVerify(exactly = 0) { chatDao.markSynced(any()) }
                coVerify(exactly = 0) { messageDao.markSynced(any()) }
            }

        @Test
        @DisplayName("pushes pending chats and messages then marks them synced")
        fun pushesPendingDataAndMarksSynced() =
            testScope.runTest {
                val chatId = ChatId("chat-1")
                val messageId = MessageId("msg-1")
                val chatEntity =
                    ChatEntity(
                        chatId,
                        "PCENC:v1:aes-gcm:Test Chat",
                        java.util.Date(1000L),
                        java.util.Date(2000L),
                        false,
                        pendingSync = 1,
                    )
                val msgEntity =
                    MessageEntity(
                        messageId,
                        chatId,
                        "PCENC:v1:aes-gcm:Hello",
                        role = com.browntowndev.pocketcrew.domain.model.chat.Role.USER,
                        pendingSync = 1,
                    )
                val msgWithFiles =
                    com.browntowndev.pocketcrew.core.data.local
                        .MessageWithFiles(msgEntity, emptyList())

                coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
                coEvery { messageDao.getClaimedForSync() } returns listOf(msgWithFiles)
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    com.google.android.gms.tasks.Tasks
                        .forResult(null)

                val result = dataSource.syncPendingChanges()

                assertTrue(result.isSuccess)
                verify { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) }
                coVerify { chatDao.markSynced(listOf(chatId)) }
                coVerify { messageDao.markSynced(listOf(messageId)) }
            }

        @Test
        @DisplayName("uploads locally encrypted field envelopes without re-encrypting for Firestore")
        fun uploadsLocallyEncryptedFieldEnvelopesWithoutReEncryptingForFirestore() =
            testScope.runTest {
                val source =
                    FirestoreDataSource(
                        documentDao = documentDao,
                        firestore = firestore,
                        chatDao = chatDao,
                        messageDao = messageDao,
                        messageTranscriptSegmentDao = mockk(relaxed = true),
                        memoriesDao = mockk(relaxed = true),
                        webSearchSourceDao = mockk(relaxed = true),
                        ttsProviderDao = mockk(relaxed = true),
                        mediaProviderDao = mockk(relaxed = true),
                        apiModelConfigurationsDao = mockk(relaxed = true),
                        localModelsDao = mockk(relaxed = true),
                        apiCredentialsDao = apiCredentialsDao,
                        localModelConfigurationsDao = mockk(relaxed = true),
                        defaultModelsDao = mockk(relaxed = true),
                        studioMediaDao = mockk(relaxed = true),
                        studioAlbumDao = mockk(relaxed = true),
                        messageFileDao = mockk(relaxed = true),
                        messageVisionAnalysisDao = mockk(relaxed = true),
                        chatSummaryDao = chatSummaryDao,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                        apiKeyManager = apiKeyManager,
                        cryptoManager = cryptoManager,
                        loggingPort = loggingPort,
                        externalScope = testScope,
                        userIdProvider = { "test-user-id" },
                        embeddingCoordinator = embeddingCoordinator,
                    )
                val chatId = ChatId("chat-local")
                val chatEntity =
                    ChatEntity(
                        chatId,
                        "PCENC:v1:aes-gcm:Plain Chat",
                        java.util.Date(1000L),
                        java.util.Date(2000L),
                        false,
                        pendingSync = 1,
                    )

                coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
                coEvery { messageDao.getClaimedForSync() } returns emptyList()
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    com.google.android.gms.tasks.Tasks
                        .forResult(null)

                val result = source.syncPendingChanges()

                assertTrue(result.isSuccess)
                verify(exactly = 0) { cryptoManager.encrypt(any()) }
                coVerify { chatDao.markSynced(listOf(chatId)) }
            }

        @Test
        @DisplayName("self-heals pending plaintext Room fields before Firestore upload")
        fun selfHealsPendingPlaintextRoomFieldsBeforeFirestoreUpload() =
            testScope.runTest {
                val source =
                    FirestoreDataSource(
                        documentDao = documentDao,
                        firestore = firestore,
                        chatDao = chatDao,
                        messageDao = messageDao,
                        messageTranscriptSegmentDao = mockk(relaxed = true),
                        memoriesDao = mockk(relaxed = true),
                        webSearchSourceDao = mockk(relaxed = true),
                        ttsProviderDao = mockk(relaxed = true),
                        mediaProviderDao = mockk(relaxed = true),
                        apiModelConfigurationsDao = mockk(relaxed = true),
                        localModelsDao = mockk(relaxed = true),
                        apiCredentialsDao = apiCredentialsDao,
                        localModelConfigurationsDao = mockk(relaxed = true),
                        defaultModelsDao = mockk(relaxed = true),
                        studioMediaDao = mockk(relaxed = true),
                        studioAlbumDao = mockk(relaxed = true),
                        messageFileDao = mockk(relaxed = true),
                        messageVisionAnalysisDao = mockk(relaxed = true),
                        chatSummaryDao = chatSummaryDao,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                        apiKeyManager = apiKeyManager,
                        cryptoManager = cryptoManager,
                        loggingPort = loggingPort,
                        externalScope = testScope,
                        userIdProvider = { "test-user-id" },
                        embeddingCoordinator = embeddingCoordinator,
                    )
                val chatId = ChatId("chat-plain")
                val chatEntity =
                    ChatEntity(
                        chatId,
                        "Plain Chat",
                        java.util.Date(1000L),
                        java.util.Date(2000L),
                        false,
                        pendingSync = 1,
                    )

                coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
                coEvery { messageDao.getClaimedForSync() } returns emptyList()
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    com.google.android.gms.tasks.Tasks
                        .forResult(null)

                val result = source.syncPendingChanges()

                assertTrue(result.isSuccess)
                coVerify { chatDao.upsert(match { it.id == chatId && it.name == "PCENC:v1:aes-gcm:Plain Chat" }) }
                verify { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) }
                coVerify { chatDao.markSynced(listOf(chatId)) }
            }

        @Test
        @DisplayName("uses multiple 500-op batches for large pending sets")
        fun usesMultipleBatchesForLargePayloads() =
            testScope.runTest {
                val chatId = ChatId("chat-x")
                val chats =
                    (1..550).map { i ->
                        ChatEntity(
                            ChatId("chat-$i"),
                            "PCENC:v1:aes-gcm:Chat $i",
                            java.util.Date(i.toLong()),
                            java.util.Date(i.toLong()),
                            false,
                            pendingSync = 1,
                        )
                    }
                coEvery { chatDao.getClaimedForSync() } returns chats
                coEvery { messageDao.getClaimedForSync() } returns emptyList()
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    com.google.android.gms.tasks.Tasks
                        .forResult(null)

                val result = dataSource.syncPendingChanges()

                assertTrue(result.isSuccess)
                verify(exactly = 2) { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) }
            }

        @Test
        @DisplayName("returns failure on Firestore batch error")
        fun returnsFailureOnBatchError() =
            testScope.runTest {
                val chatEntity =
                    ChatEntity(ChatId("c1"), "PCENC:v1:aes-gcm:Chat", java.util.Date(), java.util.Date(), false, pendingSync = 1)
                coEvery { chatDao.getClaimedForSync() } returns listOf(chatEntity)
                coEvery { messageDao.getClaimedForSync() } returns emptyList()
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    com.google.android.gms.tasks.Tasks
                        .forException(RuntimeException("Batch failed"))

                val result = dataSource.syncPendingChanges()

                assertTrue(result.isFailure)
                coVerify(exactly = 0) { chatDao.markSynced(any()) }
            }

        @Test
        @DisplayName("existing syncEntitiesToCloud still works for backwards compat")
        fun existingSyncEntitiesToCloudStillWorks() =
            testScope.runTest {
                val messages =
                    (1..550).map { i ->
                        com.browntowndev.pocketcrew.domain.model.chat.Message(
                            id = MessageId("msg-$i"),
                            chatId = ChatId("chat1"),
                            content =
                                com.browntowndev.pocketcrew.domain.model.chat
                                    .Content(text = "Hello $i"),
                            role = com.browntowndev.pocketcrew.domain.model.chat.Role.USER,
                            createdAt = 1000L + i,
                        )
                    }
                every { firestore.runBatch(any<com.google.firebase.firestore.WriteBatch.Function>()) } returns
                    com.google.android.gms.tasks.Tasks
                        .forResult(null)

                val result = dataSource.syncEntitiesToCloud(emptyList(), messages)

                assertTrue(result.isSuccess)
                verify(exactly = 2) { firestore.runBatch(any()) }
            }

        @Test
        @DisplayName("pushes pending api_credentials then marks them synced")
        fun pushesPendingApiCredentialsAndMarksSynced() =
            testScope.runTest {
                val id =
                    com.browntowndev.pocketcrew.domain.model.config
                        .ApiCredentialsId("cred-1")
                val encryptedKey = "encrypted-key".toByteArray()
                val entity =
                    com.browntowndev.pocketcrew.core.data.local.ApiCredentialsEntity(
                        id = id,
                        displayName = "PCENC:v1:aes-gcm:OpenAI",
                        provider = com.browntowndev.pocketcrew.domain.model.inference.ApiProvider.OPENAI,
                        modelId = "gpt-4",
                        credentialAlias = "openai_main",
                        encryptedApiKey = encryptedKey,
                        pendingSync = 1,
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
        fun pullsApiCredentialsFromFirestore() =
            testScope.runTest {
                val messagesListenerSlot = slot<EventListener<QuerySnapshot>>()
                val credsCollection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/api_credentials") } returns credsCollection
                every { credsCollection.addSnapshotListener(capture(messagesListenerSlot)) } returns mockk(relaxed = true)

                dataSource.startListening()

                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "cred-1"
                every { mockDoc.getBlob("display_name") } returns Blob.fromBytes("OpenAI".toByteArray())
                every { mockDoc.getString("provider") } returns "OPENAI"
                every { mockDoc.getBlob("base_url") } returns Blob.fromBytes("https://api.openai.com".toByteArray())
                every { mockDoc.getString("model_id") } returns "gpt-4"
                every { mockDoc.getString("credential_alias") } returns "openai_main"
                every { mockDoc.getBlob("encrypted_api_key") } returns Blob.fromBytes("encrypted".toByteArray())
                every { mockDoc.getLong("updated_at") } returns 1000L
                every { mockDoc.getBoolean("deleted") } returns false

                coEvery { apiCredentialsDao.getById(any()) } returns null

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                messagesListenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    apiCredentialsDao.upsert(
                        match {
                            it.id.value == "cred-1" && it.displayName == "OpenAI" && it.baseUrl == "https://api.openai.com"
                        },
                    )
                }
            }
    }

    @Test
    @DisplayName("syncEntitiesToCloud uses multiple batches for more than 500 items")
    fun syncEntitiesToCloudUsesMultipleBatches() =
        testScope.runTest {
            val messages =
                (1..550).map { i ->
                    Message(
                        id = MessageId("msg-$i"),
                        chatId = ChatId("chat1"),
                        content =
                            com.browntowndev.pocketcrew.domain.model.chat
                                .Content(text = "Hello $i"),
                        role = Role.USER,
                        createdAt = 1000L + i,
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
        fun updateVaultSaltSucceeds() =
            testScope.runTest {
                every { userDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)

                val result = dataSource.updateVaultSalt("test-salt")

                assertTrue(result.isSuccess)
                verify { userDocRef.set(mapOf("vaultSalt" to "test-salt"), SetOptions.merge()) }
            }

        @Test
        @DisplayName("getVaultSalt retrieves the salt from the user document")
        fun getVaultSaltSucceeds() =
            testScope.runTest {
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
        fun getVaultSaltReturnsNullIfNoDoc() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns false
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.getVaultSalt()

                assertTrue(result.isSuccess)
                assertEquals(null, result.getOrNull())
            }

        @Test
        @DisplayName("updateWrappedDek writes the wrappedDek field to the user document")
        fun updateWrappedDekWritesField() =
            testScope.runTest {
                every { userDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)

                val result = dataSource.updateWrappedDek("wrapped-blob")

                assertTrue(result.isSuccess)
                verify { userDocRef.set(mapOf("wrappedDek" to "wrapped-blob"), SetOptions.merge()) }
            }

        @Test
        @DisplayName("updateWrappedDek with null deletes the wrappedDek field")
        fun updateWrappedDekWithNullDeletes() =
            testScope.runTest {
                every { userDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)
                val payloadSlot = slot<Map<String, Any>>()

                val result = dataSource.updateWrappedDek(null)

                assertTrue(result.isSuccess)
                verify { userDocRef.set(capture(payloadSlot), SetOptions.merge()) }
                assertTrue(payloadSlot.captured["wrappedDek"] is com.google.firebase.firestore.FieldValue)
            }

        @Test
        @DisplayName("getWrappedDek returns the wrappedDek string when present")
        fun getWrappedDekSucceeds() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns true
                every { snapshot.getString("wrappedDek") } returns "wrapped-blob"
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.getWrappedDek()

                assertTrue(result.isSuccess)
                assertEquals("wrapped-blob", result.getOrNull())
            }

        @Test
        @DisplayName("getWrappedDek returns null when field absent")
        fun getWrappedDekReturnsNullWhenAbsent() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns true
                every { snapshot.getString("wrappedDek") } returns null
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.getWrappedDek()

                assertTrue(result.isSuccess)
                assertEquals(null, result.getOrNull())
            }

        @Test
        @DisplayName("uploadVaultMetadata writes salt, verification, and wrappedDek atomically")
        fun uploadVaultMetadataWritesAllFields() =
            testScope.runTest {
                every { userDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)
                val payloadSlot = slot<Map<String, Any>>()

                val result =
                    dataSource.uploadVaultMetadata(
                        com.browntowndev.pocketcrew.domain.model.security.VaultMetadata(
                            salt = "the-salt",
                            verification = "the-verification",
                            wrappedDek = "the-wrapped-dek",
                        ),
                    )

                assertTrue(result.isSuccess)
                verify { userDocRef.set(capture(payloadSlot), SetOptions.merge()) }
                assertEquals("the-salt", payloadSlot.captured["vaultSalt"])
                assertEquals("the-verification", payloadSlot.captured["vaultVerification"])
                assertEquals("the-wrapped-dek", payloadSlot.captured["wrappedDek"])
            }

        @Test
        @DisplayName("uploadVaultMetadata writes FieldValue.delete() for null verification or wrappedDek")
        fun uploadVaultMetadataDeletesNullFields() =
            testScope.runTest {
                every { userDocRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)
                val payloadSlot = slot<Map<String, Any>>()

                val result =
                    dataSource.uploadVaultMetadata(
                        com.browntowndev.pocketcrew.domain.model.security.VaultMetadata(
                            salt = "the-salt",
                            verification = null,
                            wrappedDek = null,
                        ),
                    )

                assertTrue(result.isSuccess)
                verify { userDocRef.set(capture(payloadSlot), SetOptions.merge()) }
                assertEquals("the-salt", payloadSlot.captured["vaultSalt"])
                assertTrue(payloadSlot.captured["vaultVerification"] is com.google.firebase.firestore.FieldValue)
                assertTrue(payloadSlot.captured["wrappedDek"] is com.google.firebase.firestore.FieldValue)
            }

        @Test
        @DisplayName("fetchVaultMetadata returns null when user doc has no salt")
        fun fetchVaultMetadataReturnsNullWhenNoSalt() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns true
                every { snapshot.getString("vaultSalt") } returns null
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.fetchVaultMetadata()

                assertTrue(result.isSuccess)
                assertEquals(null, result.getOrNull())
            }

        @Test
        @DisplayName("fetchVaultMetadata returns null when document doesn't exist")
        fun fetchVaultMetadataReturnsNullWhenNoDoc() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns false
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.fetchVaultMetadata()

                assertTrue(result.isSuccess)
                assertEquals(null, result.getOrNull())
            }

        @Test
        @DisplayName("fetchVaultMetadata returns full metadata when all fields present")
        fun fetchVaultMetadataReturnsAllFields() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns true
                every { snapshot.getString("vaultSalt") } returns "the-salt"
                every { snapshot.getString("vaultVerification") } returns "the-ver"
                every { snapshot.getString("wrappedDek") } returns "the-wrapped"
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.fetchVaultMetadata()

                assertTrue(result.isSuccess)
                val metadata = result.getOrNull()!!
                assertEquals("the-salt", metadata.salt)
                assertEquals("the-ver", metadata.verification)
                assertEquals("the-wrapped", metadata.wrappedDek)
            }

        @Test
        @DisplayName("fetchVaultMetadata returns metadata with null wrappedDek when field absent")
        fun fetchVaultMetadataReturnsNullWrappedDekWhenAbsent() =
            testScope.runTest {
                val snapshot = mockk<DocumentSnapshot>()
                every { snapshot.exists() } returns true
                every { snapshot.getString("vaultSalt") } returns "the-salt"
                every { snapshot.getString("vaultVerification") } returns "the-ver"
                every { snapshot.getString("wrappedDek") } returns null
                every { userDocRef.get() } returns Tasks.forResult(snapshot)

                val result = dataSource.fetchVaultMetadata()

                assertTrue(result.isSuccess)
                val metadata = result.getOrNull()!!
                assertEquals(null, metadata.wrappedDek)
                assertEquals("the-ver", metadata.verification)
            }
    }

    @Nested
    @DisplayName("local model configuration snapshot listener")
    inner class LocalModelConfigurationSnapshotTests {
        private val listenerSlot = slot<EventListener<QuerySnapshot>>()
        private val localModelConfigurationsDao =
            mockk<com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao>(relaxed = true)

        @BeforeEach
        fun setupListener() {
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
                    chatSummaryDao = chatSummaryDao,
                    embeddingCoordinator = embeddingCoordinator,
                    embeddingDao = embeddingDao,
                    localEncryption = FakeLocalDataEncryptionService(),
                )

            val configCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/local_model_configs") } returns configCollection
            every { configCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

            dataSource.startListening()
        }

        @Test
        @DisplayName("ADDED updates existing configuration when ID is different but logical key matches")
        fun addedUpdatesExistingOnIdMismatch() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "remote-id"
                every { mockDoc.getString("local_model_id") } returns "model-1"
                every { mockDoc.getBlob("display_name") } returns Blob.fromBytes("Logic Match".toByteArray())
                every { mockDoc.getDouble("temperature") } returns 0.7
                every { mockDoc.getLong("top_k") } returns 40L
                every { mockDoc.getDouble("top_p") } returns 0.9
                every { mockDoc.getDouble("min_p") } returns 0.05
                every { mockDoc.getDouble("repetition_penalty") } returns 1.1
                every { mockDoc.getLong("max_tokens") } returns 4096L
                every { mockDoc.getLong("context_window") } returns 4096L
                every { mockDoc.getBoolean("thinking_enabled") } returns false
                every { mockDoc.getBlob("system_prompt") } returns Blob.fromBytes("".toByteArray())
                every { mockDoc.getLong("updated_at") } returns 2000L
                every { mockDoc.getBoolean("deleted") } returns false

                val existingEntity =
                    LocalModelConfigurationEntity(
                        id = LocalModelConfigurationId("local-id"),
                        localModelId = LocalModelId("model-1"),
                        displayName = "Logic Match",
                        temperature = 0.5,
                        updatedAt = 1000L,
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
                    localModelConfigurationsDao.upsert(
                        match {
                            it.id.value == "local-id" && it.temperature == 0.7 && it.pendingSync == 0
                        },
                    )
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
                    chatSummaryDao = chatSummaryDao,
                    embeddingCoordinator = embeddingCoordinator,
                    embeddingDao = embeddingDao,
                    localEncryption = FakeLocalDataEncryptionService(),
                )

            val visionCollection = mockk<CollectionReference>(relaxed = true)
            every { firestore.collection("users/test-user-id/vision_analysis") } returns visionCollection
            every { visionCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

            dataSource.startListening()
        }

        @Test
        @DisplayName("ADDED inserts vision analysis and placeholder message when message not in local DB")
        fun addedInsertsPlaceholderMessage() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "vision-1"
                every { mockDoc.getString("user_message_id") } returns "msg-1"
                every { mockDoc.getBlob("image_uri") } returns Blob.fromBytes("file://img.jpg".toByteArray())
                every { mockDoc.getBlob("prompt_text") } returns Blob.fromBytes("encrypted".toByteArray())
                every { mockDoc.getBlob("analysis_text") } returns Blob.fromBytes("encrypted".toByteArray())
                every { mockDoc.getString("model_type") } returns "VISION"
                every { mockDoc.getString("chat_id") } returns "chat-1"
                every { mockDoc.getLong("created_at") } returns 1000L
                every { mockDoc.getLong("updated_at") } returns 2000L
                every { mockDoc.getBoolean("deleted") } returns false

                coEvery { messageDao.getMessageById(MessageId("msg-1")) } returns null
                coEvery { chatDao.getChatById(any()) } returns mockk()
                coEvery { messageVisionAnalysisDao.getById("vision-1") } returns null

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                listenerSlot.captured.onEvent(snapshot, null)

                coVerify {
                    messageDao.insertIfAbsent(
                        match {
                            it.id.value == "msg-1" && it.content == "PCENC:v1:aes-gcm:Syncing..."
                        },
                    )
                    messageVisionAnalysisDao.upsert(
                        match {
                            it.id == "vision-1" && it.userMessageId.value == "msg-1"
                        },
                    )
                }
            }

        @Test
        @DisplayName("decrypts image_uri from blob when syncing down")
        fun decryptsImageUriFromBlob() =
            testScope.runTest {
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "vision-2"
                every { mockDoc.getString("user_message_id") } returns "msg-2"
                every { mockDoc.getBlob("image_uri") } returns Blob.fromBytes("file:///data/img.jpg".toByteArray())
                every { mockDoc.getBlob("prompt_text") } returns Blob.fromBytes("a cat".toByteArray())
                every { mockDoc.getBlob("analysis_text") } returns Blob.fromBytes("analysis".toByteArray())
                every { mockDoc.getString("model_type") } returns "VISION"
                every { mockDoc.getString("chat_id") } returns "chat-1"
                every { mockDoc.getLong("created_at") } returns 1000L
                every { mockDoc.getLong("updated_at") } returns 2000L
                every { mockDoc.getBoolean("deleted") } returns false

                coEvery { messageVisionAnalysisDao.getById("vision-2") } returns null

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                listenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    messageVisionAnalysisDao.upsert(
                        match {
                            it.id == "vision-2" && it.imageUri == "file:///data/img.jpg"
                        },
                    )
                }
            }
    }

    @Nested
    @DisplayName("Deserializer hardening — blank FK fields return null")
    inner class DeserializerHardeningTests {
        @Test
        @DisplayName("messageEntityFromDoc returns null when chat_id is blank")
        fun messageEntityFromDoc_blankChatId_returnsNull() =
            testScope.runTest {
                // Fire a ADDED message event where chat_id is blank — upsertLocal should NOT be called
                val messagesListenerSlot = slot<EventListener<QuerySnapshot>>()
                val chatsCollection = mockk<CollectionReference>(relaxed = true)
                val messagesCollection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
                every { firestore.collection("users/test-user-id/messages") } returns messagesCollection
                every { chatsCollection.addSnapshotListener(any()) } returns mockk(relaxed = true)
                every { messagesCollection.addSnapshotListener(capture(messagesListenerSlot)) } returns mockk(relaxed = true)

                dataSource.startListening()

                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
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
                coVerify(exactly = 0) { messageDao.upsert(any<MessageEntity>()) }
                coVerify(exactly = 0) { messageDao.insertIfAbsent(any()) }
            }

        @Test
        @DisplayName("messageEntityFromDoc returns null when chat_id is null")
        fun messageEntityFromDoc_nullChatId_returnsNull() =
            testScope.runTest {
                val messagesListenerSlot = slot<EventListener<QuerySnapshot>>()
                val chatsCollection = mockk<CollectionReference>(relaxed = true)
                val messagesCollection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
                every { firestore.collection("users/test-user-id/messages") } returns messagesCollection
                every { chatsCollection.addSnapshotListener(any()) } returns mockk(relaxed = true)
                every { messagesCollection.addSnapshotListener(capture(messagesListenerSlot)) } returns mockk(relaxed = true)

                dataSource.startListening()

                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
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

                coVerify(exactly = 0) { messageDao.upsert(any<MessageEntity>()) }
                coVerify(exactly = 0) { messageDao.insertIfAbsent(any()) }
            }

        @Test
        @DisplayName("vision analysis fromFirestore returns null when user_message_id is blank")
        fun visionAnalysis_fromFirestore_blankUserMessageId_returnsNull() =
            testScope.runTest {
                val messageVisionAnalysisDao = mockk<com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisDao>(relaxed = true)
                val localDataSource =
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
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )

                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val visionCollection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/vision_analysis") } returns visionCollection
                every { visionCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

                localDataSource.startListening()

                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "vision-blank"
                every { mockDoc.getString("user_message_id") } returns ""
                every { mockDoc.getBlob("image_uri") } returns Blob.fromBytes("file://img.jpg".toByteArray())
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
        fun messageFile_fromFirestore_blankMessageId_returnsNull() =
            testScope.runTest {
                val messageFileDao = mockk<com.browntowndev.pocketcrew.core.data.local.MessageFileDao>(relaxed = true)
                val localDataSource =
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
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )

                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val fileCollection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/message_files") } returns fileCollection
                every { fileCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

                localDataSource.startListening()

                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
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
        fun webSearchSource_fromFirestore_blankMessageId_returnsNull() =
            testScope.runTest {
                val webSearchSourceDao = mockk<com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao>(relaxed = true)
                val localDataSource =
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
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )

                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val searchCollection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/web_search_sources") } returns searchCollection
                every { searchCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

                localDataSource.startListening()

                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
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

    @Nested
    @DisplayName("messageSyncAdapter")
    inner class MessageSyncTests {
        @Test
        @DisplayName("correctly syncs Message with artifactsJson and userMessageId")
        fun syncsMessage() =
            runTest {
                val localDataSource =
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
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )

                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val collection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/messages") } returns collection
                every { collection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

                localDataSource.startListening()

                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "msg-1"
                every { mockDoc.getString("chat_id") } returns "chat-1"
                every { mockDoc.getBlob("content") } returns Blob.fromBytes("Hello".toByteArray())
                every { mockDoc.getString("role") } returns "assistant"
                every { mockDoc.getString("message_state") } returns "complete"
                every { mockDoc.getString("user_message_id") } returns "msg-user"
                every { mockDoc.getBlob("artifacts_json") } returns Blob.fromBytes("[artifact]".toByteArray())
                every { mockDoc.getBlob("image_uris_json") } returns Blob.fromBytes("""["file:///1.jpg"]""".toByteArray())
                every { mockDoc.getLong("updated_at") } returns 123L
                every { mockDoc.getBoolean("deleted") } returns false

                coEvery { messageDao.getMessageById(any()) } returns null

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                listenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    messageDao.upsert(
                        match {
                            it.id.value == "msg-1" &&
                                it.userMessageId?.value == "msg-user" &&
                                it.artifactsJson == "[artifact]" &&
                                it.imageUrisJson == """["file:///1.jpg"]"""
                        },
                    )
                }
            }

        @Test
        @DisplayName("decrypts generatedMediaAssetIdsJson from blob when syncing down")
        fun decryptsGeneratedMediaAssetIdsJsonFromBlob() =
            runTest {
                val localDataSource =
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
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )
                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val collection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/messages") } returns collection
                every { collection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)
                localDataSource.startListening()

                val assetIds = """["gs://bucket/users/uid/studio/abc.jpg"]"""
                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "msg-1"
                every { mockDoc.getString("chat_id") } returns "chat-1"
                every { mockDoc.getBlob("content") } returns Blob.fromBytes("Hello".toByteArray())
                every { mockDoc.getString("role") } returns "user"
                every { mockDoc.getString("message_state") } returns "complete"
                every { mockDoc.getBlob("generated_media_asset_ids_json") } returns Blob.fromBytes(assetIds.toByteArray())
                every { mockDoc.getLong("updated_at") } returns 123L
                every { mockDoc.getBoolean("deleted") } returns false
                coEvery { messageDao.getMessageById(any()) } returns null

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                listenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    messageDao.upsert(
                        match {
                            it.id.value == "msg-1" && it.generatedMediaAssetIdsJson == assetIds
                        },
                    )
                }
            }
    }

    @Nested
    @DisplayName("mediaProviderSyncAdapter")
    inner class MediaProviderSyncTests {
        @Test
        @DisplayName("correctly syncs MediaProvider with modelName and baseUrl")
        fun syncsMediaProvider() =
            runTest {
                val mediaProviderDao = mockk<com.browntowndev.pocketcrew.core.data.local.MediaProviderDao>(relaxed = true)
                val localDataSource =
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
                        apiCredentialsDao = apiCredentialsDao,
                        apiModelConfigurationsDao = mockk(relaxed = true),
                        localModelsDao = mockk(relaxed = true),
                        localModelConfigurationsDao = mockk(relaxed = true),
                        defaultModelsDao = mockk(relaxed = true),
                        studioMediaDao = mockk(relaxed = true),
                        studioAlbumDao = mockk(relaxed = true),
                        memoriesDao = mockk(relaxed = true),
                        ttsProviderDao = mockk(relaxed = true),
                        mediaProviderDao = mediaProviderDao,
                        apiKeyManager = apiKeyManager,
                        messageVisionAnalysisDao = mockk(relaxed = true),
                        messageFileDao = mockk(relaxed = true),
                        webSearchSourceDao = mockk(relaxed = true),
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )

                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val collection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/media_providers") } returns collection
                every { collection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

                localDataSource.startListening()

                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "media-1"
                every { mockDoc.getBlob("display_name") } returns Blob.fromBytes("Name".toByteArray())
                every { mockDoc.getString("provider") } returns "GOOGLE"
                every { mockDoc.getString("capability") } returns "IMAGE"
                every { mockDoc.getBlob("model_name") } returns Blob.fromBytes("imagen-3".toByteArray())
                every { mockDoc.getBlob("base_url") } returns Blob.fromBytes("https://api.google.com".toByteArray())
                every { mockDoc.getString("credential_alias") } returns "alias"
                every { mockDoc.getLong("updated_at") } returns 123L
                every { mockDoc.getBoolean("deleted") } returns false

                coEvery { mediaProviderDao.getMediaProvider(any()) } returns null

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                listenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    mediaProviderDao.upsert(
                        match {
                            it.id == "media-1" &&
                                it.modelName == "imagen-3" &&
                                it.baseUrl == "https://api.google.com" &&
                                it.capability == com.browntowndev.pocketcrew.domain.model.config.MediaCapability.IMAGE
                        },
                    )
                }
            }
    }

    @Nested
    @DisplayName("ttsProviderSyncAdapter")
    inner class TtsProviderSyncTests {
        @Test
        @DisplayName("correctly syncs TtsProvider with modelName and baseUrl")
        fun syncsTtsProvider() =
            runTest {
                val ttsProviderDao = mockk<com.browntowndev.pocketcrew.core.data.local.TtsProviderDao>(relaxed = true)
                val localDataSource =
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
                        apiCredentialsDao = apiCredentialsDao,
                        apiModelConfigurationsDao = mockk(relaxed = true),
                        localModelsDao = mockk(relaxed = true),
                        localModelConfigurationsDao = mockk(relaxed = true),
                        defaultModelsDao = mockk(relaxed = true),
                        studioMediaDao = mockk(relaxed = true),
                        studioAlbumDao = mockk(relaxed = true),
                        memoriesDao = mockk(relaxed = true),
                        ttsProviderDao = ttsProviderDao,
                        mediaProviderDao = mockk(relaxed = true),
                        apiKeyManager = apiKeyManager,
                        messageVisionAnalysisDao = mockk(relaxed = true),
                        messageFileDao = mockk(relaxed = true),
                        webSearchSourceDao = mockk(relaxed = true),
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )

                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val collection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/tts_providers") } returns collection
                every { collection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

                localDataSource.startListening()

                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "tts-1"
                every { mockDoc.getBlob("display_name") } returns Blob.fromBytes("Name".toByteArray())
                every { mockDoc.getString("provider") } returns "OPENAI"
                every { mockDoc.getBlob("voice_name") } returns Blob.fromBytes("alloy".toByteArray())
                every { mockDoc.getBlob("model_name") } returns Blob.fromBytes("tts-1".toByteArray())
                every { mockDoc.getBlob("base_url") } returns Blob.fromBytes("https://api.openai.com".toByteArray())
                every { mockDoc.getString("credential_alias") } returns "alias"
                every { mockDoc.getLong("updated_at") } returns 123L
                every { mockDoc.getBoolean("deleted") } returns false

                coEvery { ttsProviderDao.getTtsProvider(any()) } returns null

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                listenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    ttsProviderDao.upsertTtsProvider(
                        match {
                            it.id == "tts-1" &&
                                it.modelName == "tts-1" &&
                                it.baseUrl == "https://api.openai.com" &&
                                it.voiceName == "alloy"
                        },
                    )
                }
            }
    }

    @Nested
    @DisplayName("apiModelConfigSyncAdapter")
    inner class ApiModelConfigSyncTests {
        @Test
        @DisplayName("correctly syncs ApiModelConfiguration with OpenRouter fields")
        fun syncsApiModelConfig() =
            runTest {
                val apiModelConfigurationsDao = mockk<com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao>(relaxed = true)
                val localDataSource =
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
                        apiCredentialsDao = apiCredentialsDao,
                        apiModelConfigurationsDao = apiModelConfigurationsDao,
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
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )

                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val collection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/api_model_configs") } returns collection
                every { collection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

                localDataSource.startListening()

                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "config-1"
                every { mockDoc.getString("api_credentials_id") } returns "cred-1"
                every { mockDoc.getBlob("display_name") } returns Blob.fromBytes("Name".toByteArray())
                every { mockDoc.getString("openrouter_provider_sort") } returns "throughput"
                every { mockDoc.getBoolean("openrouter_allow_fallbacks") } returns true
                every { mockDoc.getBoolean("openrouter_require_parameters") } returns false
                every { mockDoc.getString("openrouter_data_collection") } returns "allow"
                every { mockDoc.getBoolean("openrouter_zdr") } returns true
                every { mockDoc.getLong("updated_at") } returns 123L
                every { mockDoc.getBoolean("deleted") } returns false

                coEvery { apiModelConfigurationsDao.getById(any()) } returns null

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                listenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    apiModelConfigurationsDao.upsert(
                        match {
                            it.id.value == "config-1" &&
                                it.openRouterProviderSort == "throughput" &&
                                it.openRouterAllowFallbacks == true &&
                                it.openRouterRequireParameters == false &&
                                it.openRouterDataCollectionPolicy == "allow" &&
                                it.openRouterZeroDataRetention == true
                        },
                    )
                }
            }
    }

    @Nested
    @DisplayName("localModelConfigSyncAdapter")
    inner class LocalModelConfigSyncTests {
        @Test
        @DisplayName("correctly syncs LocalModelConfiguration with isSystemPreset")
        fun syncsLocalModelConfig() =
            runTest {
                val localModelConfigurationsDao =
                    mockk<com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao>(relaxed = true)
                val localDataSource =
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
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )

                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val collection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/local_model_configs") } returns collection
                every { collection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)

                localDataSource.startListening()

                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { mockDoc.id } returns "config-1"
                every { mockDoc.getString("local_model_id") } returns "model-1"
                every { mockDoc.getBlob("display_name") } returns Blob.fromBytes("Name".toByteArray())
                every { mockDoc.getBoolean("is_system_preset") } returns true
                every { mockDoc.getLong("updated_at") } returns 123L
                every { mockDoc.getBoolean("deleted") } returns false

                coEvery { localModelConfigurationsDao.getById(any()) } returns null
                coEvery { localModelConfigurationsDao.getByModelIdAndName(any(), any()) } returns null

                val snapshot = mockSnapshot(listOf(mockDoc), DocumentChange.Type.ADDED)
                listenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    localModelConfigurationsDao.upsert(
                        match {
                            it.id.value == "config-1" &&
                                it.isSystemPreset == true
                        },
                    )
                }
            }
    }

    @Nested
    @DisplayName("per-document error isolation")
    inner class PerDocumentErrorIsolationTests {
        @Test
        @DisplayName("documents snapshot self-heals plaintext payloads before Room insert")
        fun documentsSnapshot_selfHealsPlaintextPayloadsBeforeRoomInsert() =
            testScope.runTest {
                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val documentsCollection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/documents") } returns documentsCollection
                every { documentsCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)
                dataSource.startListening()

                val plaintextDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { plaintextDoc.id } returns "artifact-plain"
                every { plaintextDoc.getString("json") } returns """{"version":"1.0","metadata":{"title":"Plain"},"blocks":[]}"""
                every { plaintextDoc.getString("title") } returns "Plain"
                every { plaintextDoc.getString("author") } returns null
                every { plaintextDoc.getString("message_id") } returns "assistant-1"
                every { plaintextDoc.getString("target_format") } returns null
                every { plaintextDoc.getString("chat_id") } returns null
                every { plaintextDoc.getLong("created_at") } returns 1000L
                every { plaintextDoc.getLong("updated_at") } returns 2000L
                every { plaintextDoc.getBoolean("deleted") } returns false
                every { plaintextDoc.data } returns
                    mapOf(
                        "json" to """{"version":"1.0","metadata":{"title":"Plain"},"blocks":[]}""",
                        "title" to "Plain",
                        "message_id" to "assistant-1",
                    )
                every { plaintextDoc.reference.set(any<Map<String, Any?>>(), any<SetOptions>()) } returns Tasks.forResult(null)
                coEvery { documentDao.getDocumentById("artifact-plain") } returns null

                val encryptedDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { encryptedDoc.id } returns "artifact-encrypted"
                every { encryptedDoc.getString("json") } returns
                    """PCENC:v1:aes-gcm:{"version":"1.0","metadata":{"title":"Encrypted"},"blocks":[]}"""
                every { encryptedDoc.getString("title") } returns "PCENC:v1:aes-gcm:Encrypted"
                every { encryptedDoc.getString("author") } returns null
                every { encryptedDoc.getString("message_id") } returns "assistant-2"
                every { encryptedDoc.getString("target_format") } returns null
                every { encryptedDoc.getString("chat_id") } returns null
                every { encryptedDoc.getLong("created_at") } returns 1000L
                every { encryptedDoc.getLong("updated_at") } returns 2000L
                every { encryptedDoc.getBoolean("deleted") } returns false
                every { encryptedDoc.data } returns
                    mapOf(
                        "json" to """PCENC:v1:aes-gcm:{"version":"1.0","metadata":{"title":"Encrypted"},"blocks":[]}""",
                        "title" to "PCENC:v1:aes-gcm:Encrypted",
                        "message_id" to "assistant-2",
                    )
                coEvery { documentDao.getDocumentById("artifact-encrypted") } returns null

                val snapshot = mockSnapshot(listOf(plaintextDoc, encryptedDoc), DocumentChange.Type.ADDED)
                listenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify {
                    documentDao.insertDocument(
                        match<DocumentEntity> {
                            it.id == "artifact-plain" &&
                                it.json.startsWith("PCENC:v1:aes-gcm:") &&
                                it.title.startsWith("PCENC:v1:aes-gcm:")
                        },
                    )
                }
                verify { plaintextDoc.reference.set(any<Map<String, Any?>>(), any<SetOptions>()) }
                coVerify {
                    documentDao.insertDocument(
                        match<DocumentEntity> {
                            it.id == "artifact-encrypted" &&
                                it.json.startsWith("PCENC:v1:aes-gcm:") &&
                                it.title.startsWith("PCENC:v1:aes-gcm:")
                        },
                    )
                }
            }

        @Test
        @DisplayName("chat snapshot: remaining documents are processed when one document throws")
        fun chatSnapshot_continuesAfterDocumentError() =
            testScope.runTest {
                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val chatsCollection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
                every { chatsCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)
                dataSource.startListening()

                val badDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { badDoc.id } returns "chat-bad"
                every { badDoc.getBlob("name") } throws RuntimeException("Field 'name' is not a Blob")

                val goodDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { goodDoc.id } returns "chat-good"
                every { goodDoc.getBlob("name") } returns Blob.fromBytes("Valid Chat".toByteArray())
                every { goodDoc.getLong("created_at") } returns 1000L
                every { goodDoc.getLong("last_modified") } returns 2000L
                every { goodDoc.getBoolean("pinned") } returns false
                every { goodDoc.getLong("updated_at") } returns 3000L
                every { goodDoc.getBoolean("deleted") } returns false
                coEvery { chatDao.getChatById(ChatId("chat-good")) } returns null

                val snapshot = mockSnapshot(listOf(badDoc, goodDoc), DocumentChange.Type.ADDED)
                listenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify { chatDao.upsert(match { it.id.value == "chat-good" }) }
            }

        @Test
        @DisplayName("studio_gallery snapshot: remaining documents are processed when one document throws")
        fun studioGallerySnapshot_continuesAfterDocumentError() =
            testScope.runTest {
                val studioMediaDao = mockk<com.browntowndev.pocketcrew.core.data.local.StudioMediaDao>(relaxed = true)
                val localDataSource =
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
                        apiCredentialsDao = apiCredentialsDao,
                        apiModelConfigurationsDao = mockk(relaxed = true),
                        localModelsDao = mockk(relaxed = true),
                        localModelConfigurationsDao = mockk(relaxed = true),
                        defaultModelsDao = mockk(relaxed = true),
                        studioMediaDao = studioMediaDao,
                        studioAlbumDao = mockk(relaxed = true),
                        memoriesDao = mockk(relaxed = true),
                        ttsProviderDao = mockk(relaxed = true),
                        mediaProviderDao = mockk(relaxed = true),
                        apiKeyManager = apiKeyManager,
                        messageVisionAnalysisDao = mockk(relaxed = true),
                        messageFileDao = mockk(relaxed = true),
                        webSearchSourceDao = mockk(relaxed = true),
                        chatSummaryDao = chatSummaryDao,
                        embeddingCoordinator = embeddingCoordinator,
                        embeddingDao = embeddingDao,
                        localEncryption = FakeLocalDataEncryptionService(),
                    )
                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val galleryCollection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/studio_gallery") } returns galleryCollection
                every { galleryCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)
                localDataSource.startListening()

                val badDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { badDoc.id } returns "media-bad"
                every { badDoc.getBlob("prompt") } throws RuntimeException("Field 'prompt' is not a Blob")

                val goodDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { goodDoc.id } returns "media-good"
                every { goodDoc.getBlob("prompt") } returns Blob.fromBytes("a dog in the park".toByteArray())
                every { goodDoc.getBlob("remote_url") } returns Blob.fromBytes("https://example.com/img.jpg".toByteArray())
                every { goodDoc.getBlob("remote_thumbnail_url") } returns null
                every { goodDoc.getString("media_type") } returns "IMAGE"
                every { goodDoc.getLong("created_at") } returns 1000L
                every { goodDoc.getString("album_id") } returns null
                every { goodDoc.getLong("updated_at") } returns 2000L
                every { goodDoc.getBoolean("deleted") } returns false
                coEvery { studioMediaDao.getMediaById("media-good") } returns null

                val snapshot = mockSnapshot(listOf(badDoc, goodDoc), DocumentChange.Type.ADDED)
                listenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify { studioMediaDao.insertMedia(match { it.id == "media-good" }) }
            }

        @Test
        @DisplayName("messages snapshot: remaining documents are processed when one document throws")
        fun messagesSnapshot_continuesAfterDocumentError() =
            testScope.runTest {
                val listenerSlot = slot<EventListener<QuerySnapshot>>()
                val messagesCollection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/messages") } returns messagesCollection
                every { messagesCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)
                dataSource.startListening()

                val badDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { badDoc.id } returns "msg-bad"
                every { badDoc.getString("chat_id") } returns "chat-1"
                every { badDoc.getBlob("content") } throws RuntimeException("Field 'content' is not a Blob")

                val goodDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
                every { goodDoc.id } returns "msg-good"
                every { goodDoc.getString("chat_id") } returns "chat-1"
                every { goodDoc.getBlob("content") } returns Blob.fromBytes("encrypted".toByteArray())
                every { goodDoc.getString("role") } returns "user"
                every { goodDoc.getString("message_state") } returns "COMPLETE"
                every { goodDoc.getLong("created_at") } returns 1000L
                every { goodDoc.getString("model_type") } returns null
                every { goodDoc.getLong("updated_at") } returns 2000L
                every { goodDoc.getBoolean("deleted") } returns false
                coEvery { messageDao.getMessageById(MessageId("msg-good")) } returns null

                val snapshot = mockSnapshot(listOf(badDoc, goodDoc), DocumentChange.Type.ADDED)
                listenerSlot.captured.onEvent(snapshot, null)
                advanceUntilIdle()

                coVerify { messageDao.upsert(match { it.id.value == "msg-good" }) }
            }
    }

    @Nested
    @DisplayName("cloud purge placeholder guard")
    inner class CloudPurgePlaceholderGuardTests {
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
        @DisplayName("ADDED skips placeholder chat insert while cloud purge is in progress")
        fun addedSkipsPlaceholderChatWhilePurgeInProgress() =
            testScope.runTest {
                val purgeField = FirestoreDataSource::class.java.getDeclaredField("purgeInProgress")
                purgeField.isAccessible = true
                (purgeField.get(dataSource) as AtomicBoolean).set(true)

                val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
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

                coVerify(exactly = 0) { chatDao.insertIfAbsent(any()) }
                coVerify {
                    messageDao.upsert(
                        match {
                            it.id.value == "msg-1" && it.chatId.value == "chat-1"
                        },
                    )
                }
            }
    }

    @Nested
    @DisplayName("orphaned placeholder cleanup")
    inner class OrphanedPlaceholderCleanupTests {
        @Test
        @DisplayName("startListening removes orphaned placeholder chats when purge is not active")
        fun startListeningRemovesOrphanedPlaceholders() =
            testScope.runTest {
                val chatsCollection = mockk<CollectionReference>(relaxed = true)
                val messagesCollection = mockk<CollectionReference>(relaxed = true)
                every { firestore.collection("users/test-user-id/chats") } returns chatsCollection
                every { firestore.collection("users/test-user-id/messages") } returns messagesCollection
                coEvery { chatDao.deleteOrphanedPlaceholders() } returns 2

                dataSource.startListening()
                advanceUntilIdle()

                coVerify { chatDao.deleteOrphanedPlaceholders() }
            }
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

    private class FakeLocalDataEncryptionService : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(stored: String): String = stored.removePrefix("PCENC:v1:aes-gcm:")

        override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:")

        override fun encryptString(
            plaintext: String,
            aad: ByteArray,
        ): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(
            stored: String,
            aad: ByteArray,
        ): String = stored.removePrefix("PCENC:v1:aes-gcm:")
    }
}
