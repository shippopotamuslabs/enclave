package com.browntowndev.enclave.core.data.inference

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageCommittedInferencePromptEntity
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class RoomCommittedInferencePromptStoreTest {
    private lateinit var database: EnclaveDatabase
    private val logger = FakeLoggingPort()

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    private suspend fun insertMessage(messageId: MessageId) {
        val chatId = ChatId("test-chat")
        database.chatDao().upsert(
            ChatEntity(
                id = chatId,
                name = "Test Chat",
                created = Date(),
                lastModified = Date(),
                pinned = false,
            ),
        )
        database.messageCommandDao().upsert(
            MessageEntity(
                id = messageId,
                chatId = chatId,
                role = Role.USER,
                content = "Raw message content",
            ),
        )
    }

    /**
     * RoomCommittedInferencePromptStore persists cache mutations via fire-and-forget
     * `scope.launch(Dispatchers.IO)` coroutines that are children of this TestScope.
     * Joining them deterministically flushes the pending DB writes without relying on
     * wall-clock polling, which races against the real IO dispatcher under contention.
     *
     * The running test body is itself a child of the scope and is excluded so that the
     * join never blocks on itself.
     */
    private suspend fun TestScope.awaitBackgroundWrites() {
        val currentJob = kotlin.coroutines.coroutineContext[Job]
        this.coroutineContext[Job]!!.children
            .filter { it != currentJob }
            .toList()
            .joinAll()
    }

    @Test
    fun scnPersist001_commitUpdatesCacheAndPersistsToDb() =
        runTest {
            // Given an empty RoomCommittedInferencePromptStore backed by an in-memory EnclaveDatabase
            // And a user message with ID "msg-123" exists in the message table
            val messageId = MessageId("msg-123")
            insertMessage(messageId)

            val store =
                RoomCommittedInferencePromptStore(
                    dao = database.messageCommittedInferencePromptDao(),
                    scope = this,
                    logger = logger,
                )

            // When
            store.commit(messageId, "Expanded user query with RAG contexts")
            // Deterministically flush the fire-and-forget DB insert before asserting on it.
            awaitBackgroundWrites()

            // Then the in-memory cache must return immediately when calling store.get()
            assertEquals("Expanded user query with RAG contexts", store.get(messageId))

            // And the database table must contain the record (the insert coroutine was joined above,
            // so no wall-clock polling is required).
            val entity = database.messageCommittedInferencePromptDao().getByMessageId(messageId)
            assertEquals("Expanded user query with RAG contexts", entity?.inferenceContent)
        }

    @Test
    fun scnPersist002_getResolvesCacheMissFromDatabaseAndCachesIt() =
        runTest {
            // Given an in-memory EnclaveDatabase with a record in message_committed_inference_prompt
            val messageId = MessageId("msg-abc")
            insertMessage(messageId)
            database.messageCommittedInferencePromptDao().insertOrUpdate(
                MessageCommittedInferencePromptEntity(
                    messageId = messageId,
                    inferenceContent = "Prior RAG query turn",
                    updatedAt = System.currentTimeMillis(),
                ),
            )

            // And a new RoomCommittedInferencePromptStore instance is instantiated (re-hydrating with empty cache to simulate app restart)
            val store =
                RoomCommittedInferencePromptStore(
                    dao = database.messageCommittedInferencePromptDao(),
                    scope = this,
                    logger = logger,
                )

            // When
            val result = store.get(messageId)

            // Then
            assertEquals("Prior RAG query turn", result)

            // And subsequent call resolves from cache
            assertEquals("Prior RAG query turn", store.get(messageId))
        }

    @Test
    fun scnPersist003_clearRemovesFromCacheAndDeletesDbRecord() =
        runTest {
            // Given a RoomCommittedInferencePromptStore with a cached and persisted prompt for ID "msg-456"
            val messageId = MessageId("msg-456")
            insertMessage(messageId)

            val store =
                RoomCommittedInferencePromptStore(
                    dao = database.messageCommittedInferencePromptDao(),
                    scope = this,
                    logger = logger,
                )
            store.commit(messageId, "Stale prompt context")
            // Deterministically flush the fire-and-forget DB insert so the prompt is actually
            // persisted before exercising clear() (establishes the scenario's "persisted" given).
            awaitBackgroundWrites()

            // When
            store.clear(messageId)
            // Deterministically flush the fire-and-forget DB delete before asserting on it.
            awaitBackgroundWrites()

            // Then the in-memory cache must no longer hold the prompt
            assertNull(store.get(messageId))
            // And the database record must be deleted (the delete coroutine was joined above,
            // so no wall-clock polling is required).
            assertNull(database.messageCommittedInferencePromptDao().getByMessageId(messageId))
        }

    @Test
    fun scnPersist004_cascadeDeletionVerification() =
        runTest {
            // Given an in-memory EnclaveDatabase containing a message entity with ID "msg-to-delete"
            // And an associated committed prompt in message_committed_inference_prompt
            val messageId = MessageId("msg-to-delete")
            insertMessage(messageId)
            database.messageCommittedInferencePromptDao().insertOrUpdate(
                MessageCommittedInferencePromptEntity(
                    messageId = messageId,
                    inferenceContent = "Prompt context to delete",
                    updatedAt = System.currentTimeMillis(),
                ),
            )

            val store =
                RoomCommittedInferencePromptStore(
                    dao = database.messageCommittedInferencePromptDao(),
                    scope = this,
                    logger = logger,
                )

            // When the message entity is deleted from the message table
            database.openHelper.writableDatabase.execSQL("DELETE FROM message WHERE id = 'msg-to-delete'")

            // Then the record in message_committed_inference_prompt must be automatically deleted by SQLite cascade trigger
            assertNull(database.messageCommittedInferencePromptDao().getByMessageId(messageId))
            // And calling store.get() (with empty cache) must return null
            assertNull(store.get(messageId))
        }

    @Test
    fun scnPersist006_nonExistentIdQueries() =
        runTest {
            // Given a RoomCommittedInferencePromptStore backing an empty message_committed_inference_prompt table
            val store =
                RoomCommittedInferencePromptStore(
                    dao = database.messageCommittedInferencePromptDao(),
                    scope = this,
                    logger = logger,
                )

            // When
            val result = store.get(MessageId("non-existent-msg-id"))

            // Then
            assertNull(result)
        }

    @Test
    fun scnPersist007_distinctKeysSeparation() =
        runTest {
            // Given a RoomCommittedInferencePromptStore
            // And user messages with IDs "msg-x" and "msg-y" exist in the database
            val msgX = MessageId("msg-x")
            val msgY = MessageId("msg-y")
            insertMessage(msgX)
            insertMessage(msgY)

            val store =
                RoomCommittedInferencePromptStore(
                    dao = database.messageCommittedInferencePromptDao(),
                    scope = this,
                    logger = logger,
                )

            // When
            store.commit(msgX, "Prompt Content X")
            store.commit(msgY, "Prompt Content Y")

            // Then
            assertEquals("Prompt Content X", store.get(msgX))
            assertEquals("Prompt Content Y", store.get(msgY))
        }
}
