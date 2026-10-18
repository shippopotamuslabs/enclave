package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MemoriesEntity
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import org.robolectric.annotation.Config as RoboConfig

/**
 * Translated from MockK to hand-written fakes (ENC-28): recording DAO fakes
 * capture the entities the repository writes, and the recording sync
 * trigger counts its calls. Incidental cleanup DAO / read-repair applier
 * are the real implementations over an in-memory Room database.
 */
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class MemoriesRepositorySyncTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private val memoriesDao = FakeMemoriesDao()
    private val embeddingDao = FakeEmbeddingDao()
    private val embeddingEngine = FakeEmbeddingEngine()

    private lateinit var repository: MemoriesRepositoryImpl

    @BeforeEach
    fun setup() {
        syncTrigger = RecordingCloudSyncTrigger()
        database = createInMemoryEnclaveDatabase()
        embeddingEngine.uniformEmbedding = floatArrayOf(0.1f)

        repository =
            MemoriesRepositoryImpl(
                syncTrigger = syncTrigger,
                memoriesDao = memoriesDao,
                embeddingDao = embeddingDao,
                embeddingEngine = embeddingEngine,
                healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService(), FakeLoggingPort()),
                readRepairApplier = database.realReadRepairApplier(),
                cleanupDao = database.cleanupDao(),
                transactionProvider = FakeTransactionProvider(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insertMemory sets pendingSync to 1 and triggers sync`() =
        runTest {
            repository.insertMemory(MemoryCategory.CORE_IDENTITY, "I am a test")

            assertEquals(1, memoriesDao.insertCalls.single().pendingSync)
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `updateMemory sets pendingSync to 1 and triggers sync`() =
        runTest {
            val entity = MemoriesEntity(id = "id", category = MemoryCategory.CORE_IDENTITY, content = "old")
            memoriesDao.entities[entity.id] = entity

            repository.updateMemory("id", "new content", null)

            assertEquals(1, memoriesDao.updateCalls.single().pendingSync)
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `deleteMemory triggers sync`() =
        runTest {
            repository.deleteMemory("id")

            assertEquals(listOf("id"), memoriesDao.softDeleteCalls.map { it.first })
            assertEquals(1, syncTrigger.triggerCalls)
        }
}
