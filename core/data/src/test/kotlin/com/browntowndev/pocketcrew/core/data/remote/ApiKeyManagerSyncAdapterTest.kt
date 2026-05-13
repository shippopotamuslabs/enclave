package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApiKeyManagerSyncAdapterTest {
    private val firestore = mockk<FirebaseFirestore>()
    private val apiKeyManager = mockk<ApiKeyManager>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val testScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataSource: FirestoreDataSource

    @Before
    fun setup() {
        every { cryptoManager.hasCachedKey() } returns true
        every { cryptoManager.encrypt(any()) } returns Result.success("encrypted".toByteArray())

        dataSource =
            FirestoreDataSource(
                documentDao = mockk(relaxed = true),
                firestore = firestore,
                chatDao = mockk(relaxed = true),
                messageDao = mockk(relaxed = true),
                messageTranscriptSegmentDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                webSearchSourceDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                apiCredentialsDao = mockk(relaxed = true),
                localModelConfigurationsDao = mockk(relaxed = true),
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = mockk(relaxed = true),
                studioAlbumDao = mockk(relaxed = true),
                messageFileDao = mockk(relaxed = true),
                messageVisionAnalysisDao = mockk(relaxed = true),
                chatSummaryDao = mockk(relaxed = true),
                embeddingDao = mockk(relaxed = true),
                apiKeyManager = apiKeyManager,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { "test-user-id" },
                embeddingCoordinator = mockk(relaxed = true),
                localEncryption = FakeLocalDataEncryptionService(),
            )
    }

    @Test
    fun `syncPendingChanges pushes dirty aliases to Firestore`() =
        runTest {
            coEvery { apiKeyManager.getClaimedSyncAliases() } returns setOf("tavily_web_search")
            coEvery { apiKeyManager.getClaimedDeleteAliases() } returns emptySet()
            every { apiKeyManager.get("tavily_web_search") } returns "sk-tavily-123"

            val batch = mockk<WriteBatch>()
            val batchFunctionSlot = slot<WriteBatch.Function>()
            every { firestore.runBatch(capture(batchFunctionSlot)) } returns Tasks.forResult(null)

            val docRef = mockk<DocumentReference>()
            every { firestore.document(any()) } returns docRef
            every { batch.set(docRef, any(), any<com.google.firebase.firestore.SetOptions>()) } returns batch

            dataSource.syncPendingChanges()

            // Execute the captured batch function
            batchFunctionSlot.captured.apply(batch)

            verify { batch.set(docRef, match<Map<String, Any>> { it["alias"] == "tavily_web_search" }, any()) }
            coVerify { apiKeyManager.clearClaimedSync(listOf("tavily_web_search")) }
        }

    @Test
    fun `syncPendingChanges handles deleted aliases`() =
        runTest {
            coEvery { apiKeyManager.getClaimedSyncAliases() } returns emptySet()
            coEvery { apiKeyManager.getClaimedDeleteAliases() } returns setOf("exa_web_search")

            val batch = mockk<WriteBatch>()
            val batchFunctionSlot = slot<WriteBatch.Function>()
            every { firestore.runBatch(capture(batchFunctionSlot)) } returns Tasks.forResult(null)

            val docRef = mockk<DocumentReference>()
            every { firestore.document(any()) } returns docRef
            every { batch.delete(docRef) } returns batch

            dataSource.syncPendingChanges()

            // Execute the captured batch function
            batchFunctionSlot.captured.apply(batch)

            verify { batch.delete(docRef) }
            coVerify { apiKeyManager.clearClaimedDelete(listOf("exa_web_search")) }
        }

    @Test
    fun `sync aborts when vault is locked`() =
        runTest {
            every { cryptoManager.hasCachedKey() } returns false

            val result = dataSource.syncPendingChanges()

            assertTrue(result.isFailure)
            verify(exactly = 0) { firestore.runBatch(any()) }
        }
}
