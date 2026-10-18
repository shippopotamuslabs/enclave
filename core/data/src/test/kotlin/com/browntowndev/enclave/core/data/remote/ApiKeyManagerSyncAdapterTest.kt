@file:Suppress("ForbiddenMockKInTests")
// MockK is used only for Firebase SDK classes (FirebaseFirestore, WriteBatch, DocumentReference), third-party SDK types with no hand-writable fakes (ENC-28).

package com.browntowndev.enclave.core.data.remote

import com.browntowndev.enclave.core.data.FakeApiKeyManager
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ApiKeyManagerSyncAdapterTest {
    private val firestore = mockk<FirebaseFirestore>()
    private val apiKeyManager = FakeApiKeyManager()
    private val cryptoManager = FakeCryptoManagerPort()
    private val loggingPort = FakeLoggingPort()
    private val testScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataSource: FirestoreDataSource

    @BeforeEach
    fun setup() {
        cryptoManager.hasCachedKeyValue = true
        cryptoManager.encryptResult = Result.success("encrypted".toByteArray())

        dataSource =
            createTestFirestoreDataSource(
                firestore = firestore,
                apiKeyManager = apiKeyManager,
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { "test-user-id" },
                localEncryption = FakeLocalDataEncryptionService(),
            )
    }

    @Test
    fun `syncPendingChanges pushes dirty aliases to Firestore`() =
        runTest {
            apiKeyManager.claimedSyncAliases = setOf("tavily_web_search")
            apiKeyManager.claimedDeleteAliases = emptySet()
            apiKeyManager.storedValues["tavily_web_search"] = "sk-tavily-123"

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
            assertEquals(listOf(listOf("tavily_web_search")), apiKeyManager.clearClaimedSyncCalls)
        }

    @Test
    fun `syncPendingChanges handles deleted aliases`() =
        runTest {
            apiKeyManager.claimedSyncAliases = emptySet()
            apiKeyManager.claimedDeleteAliases = setOf("exa_web_search")

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
            assertEquals(listOf(listOf("exa_web_search")), apiKeyManager.clearClaimedDeleteCalls)
        }

    @Test
    fun `syncPendingChanges clears blank claimed aliases without Firestore write`() =
        runTest {
            apiKeyManager.claimedSyncAliases = setOf("")
            apiKeyManager.claimedDeleteAliases = setOf(" ")

            val result = dataSource.syncPendingChanges()

            assertTrue(result.isSuccess)
            verify(exactly = 0) { firestore.runBatch(any()) }
            assertEquals(listOf(listOf("")), apiKeyManager.clearClaimedSyncCalls)
            assertEquals(listOf(listOf(" ")), apiKeyManager.clearClaimedDeleteCalls)
        }

    @Test
    fun `sync aborts when vault is locked`() =
        runTest {
            cryptoManager.hasCachedKeyValue = false

            val result = dataSource.syncPendingChanges()

            assertTrue(result.isFailure)
            verify(exactly = 0) { firestore.runBatch(any()) }
        }
}
