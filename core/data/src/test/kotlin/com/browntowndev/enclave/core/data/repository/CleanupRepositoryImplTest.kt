package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ApiCredentialsEntity
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.LocalModelEntity
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class CleanupRepositoryImplTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private val userIdProvider: () -> String? = { "user-1" }
    private lateinit var cleanupRepository: CleanupRepositoryImpl

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        // Unconfined sync scope so ApiKeyManager's pending-sync bookkeeping lands immediately.
        apiKeyManager = testApiKeyManager(context, CoroutineScope(Dispatchers.Unconfined), "cleanup_repository_test_prefs")
        syncTrigger = RecordingCloudSyncTrigger()
        cleanupRepository =
            CleanupRepositoryImpl(
                database = database,
                apiKeyManager = apiKeyManager,
                syncTrigger = syncTrigger,
                preferencesDataStore = InMemoryPreferencesDataStore(),
                userIdProvider = userIdProvider,
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    private suspend fun seedChat() {
        database.chatDao().upsert(
            ChatEntity(
                id = ChatId("chat-1"),
                name = "Chat",
                created = Date(),
                lastModified = Date(),
                pinned = false,
            ),
        )
    }

    private suspend fun seedLocalModel() {
        database.localModelsDao().upsert(
            LocalModelEntity(
                id = LocalModelId("model-1"),
                modelFileFormat = ModelFileFormat.GGUF,
                huggingFaceModelName = "hf/model",
                remoteFilename = "model.gguf",
                localFilename = "model.gguf",
                sha256 = "abc",
                sizeInBytes = 1L,
            ),
        )
    }

    private suspend fun seedApiCredentials() {
        database.apiCredentialsDao().insert(
            ApiCredentialsEntity(
                id = com.browntowndev.enclave.domain.model.config.ApiCredentialsId("cred-1"),
                displayName = "Creds",
                provider = ApiProvider.OPENAI,
                credentialAlias = "alias-1",
            ),
        )
    }

    /** Saves the three search keys and claims their pending-sync state. */
    private suspend fun seedSearchKeys() {
        apiKeyManager.save(ApiKeyManager.TAVILY_SEARCH_ALIAS, "tavily-key")
        apiKeyManager.save(ApiKeyManager.EXA_SEARCH_ALIAS, "exa-key")
        apiKeyManager.save(ApiKeyManager.X_SEARCH_ALIAS, "x-key")
        apiKeyManager.claimPending()
    }

    private fun assertSearchKeysDeleted() {
        assertNull(apiKeyManager.get(ApiKeyManager.TAVILY_SEARCH_ALIAS))
        assertNull(apiKeyManager.get(ApiKeyManager.EXA_SEARCH_ALIAS))
        assertNull(apiKeyManager.get(ApiKeyManager.X_SEARCH_ALIAS))
    }

    @Test
    fun `clearAllData wipes everything including models`() =
        runTest {
            seedChat()
            seedLocalModel()
            seedApiCredentials()
            seedSearchKeys()
            assertEquals(3, apiKeyManager.getClaimedSyncAliases().size)

            cleanupRepository.clearAllData()

            assertTrue(database.chatDao().getAllChats().first().isEmpty())
            assertTrue(database.localModelsDao().getAll().isEmpty())
            assertTrue(database.apiCredentialsDao().getAll().isEmpty())
            assertSearchKeysDeleted()
            // clearAllPending ran: no claimed sync/delete aliases remain.
            assertTrue(apiKeyManager.getClaimedSyncAliases().isEmpty())
            assertTrue(apiKeyManager.getClaimedDeleteAliases().isEmpty())
        }

    @Test
    fun `clearUserContent wipes chats and search keys but preserves models`() =
        runTest {
            seedChat()
            seedLocalModel()
            seedSearchKeys()
            assertEquals(3, apiKeyManager.getClaimedSyncAliases().size)

            cleanupRepository.clearUserContent()

            assertTrue(database.chatDao().getAllChats().first().isEmpty())
            // clearUserData was NOT run: the local models table survives.
            assertEquals(1, database.localModelsDao().getAll().size)
            assertSearchKeysDeleted()
            assertTrue(apiKeyManager.getClaimedSyncAliases().isEmpty())
            assertTrue(apiKeyManager.getClaimedDeleteAliases().isEmpty())
        }

    @Test
    fun `deleteAllArtifacts deletes documents and triggers cloud purge`() =
        runTest {
            seedChat()
            database.messageDao().upsert(
                MessageEntity(
                    id = MessageId("msg-1"),
                    chatId = ChatId("chat-1"),
                    role = Role.USER,
                    messageState = MessageState.COMPLETE,
                    content = "hi",
                    createdAt = 1000L,
                ),
            )
            database.documentDao().insertDocument(
                DocumentEntity(
                    id = "doc-1",
                    messageId = MessageId("msg-1"),
                    json = "{}",
                    title = "Doc",
                ),
            )

            cleanupRepository.deleteAllArtifacts()

            assertNull(database.documentDao().getDocumentById("doc-1"))
            assertEquals(1, syncTrigger.triggerCloudArtifactsPurgeCalls)
            // Neither clearUserData nor clearUserContent ran: the chat survives.
            assertEquals(1, database.chatDao().getAllChats().first().size)
        }
}
