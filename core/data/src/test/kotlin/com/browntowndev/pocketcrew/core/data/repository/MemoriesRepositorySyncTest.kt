package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.core.data.local.MemoriesDao
import com.browntowndev.pocketcrew.core.data.local.MemoriesEntity
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MemoriesRepositorySyncTest {
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val memoriesDao = mockk<MemoriesDao>(relaxed = true)
    private val embeddingDao = mockk<EmbeddingDao>(relaxed = true)
    private val embeddingEngine = mockk<EmbeddingEnginePort>(relaxed = true)
    private val readRepairApplier = mockk<SensitiveRoomReadRepairApplier>(relaxed = true)

    private lateinit var repository: MemoriesRepositoryImpl

    @BeforeEach
    fun setup() {
        coEvery { embeddingEngine.getEmbedding(any()) } returns floatArrayOf(0.1f)

        repository =
            MemoriesRepositoryImpl(
                syncTrigger = syncTrigger,
                memoriesDao = memoriesDao,
                embeddingDao = embeddingDao,
                embeddingEngine = embeddingEngine,
                healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                readRepairApplier = readRepairApplier,
                cleanupDao = mockk(relaxed = true),
                transactionProvider = mockk(relaxed = true),
            )
    }

    @Test
    fun `insertMemory sets pendingSync to 1 and triggers sync`() =
        runTest {
            repository.insertMemory(MemoryCategory.CORE_IDENTITY, "I am a test")

            coVerify {
                memoriesDao.insertMemory(match { it.pendingSync == 1 })
                syncTrigger.trigger()
            }
        }

    @Test
    fun `updateMemory sets pendingSync to 1 and triggers sync`() =
        runTest {
            val entity = MemoriesEntity(id = "id", category = MemoryCategory.CORE_IDENTITY, content = "old")
            coEvery { memoriesDao.getMemoryById(any()) } returns entity

            repository.updateMemory("id", "new content", null)

            coVerify {
                memoriesDao.updateMemory(match { it.pendingSync == 1 })
                syncTrigger.trigger()
            }
        }

    @Test
    fun `deleteMemory triggers sync`() =
        runTest {
            repository.deleteMemory("id")

            coVerify {
                memoriesDao.softDeleteById("id", any())
                syncTrigger.trigger()
            }
        }
}
