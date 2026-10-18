package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.MemoriesEntity
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.recordingDao
import com.browntowndev.enclave.core.data.remote.firestore.testSyncContext
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class MemoriesSyncHandlerTest {
    private val memoriesDao = recordingDao<MemoriesDao>()
    private lateinit var handler: MemoriesSyncHandler

    @BeforeEach
    fun setup() {
        handler = MemoriesSyncHandler(createContext(memoriesDao.dao))
    }

    @Test
    @DisplayName("collectionPath is memories")
    fun collectionPath_isMemories() {
        assertEquals("memories", handler.collectionPath)
    }

    @Test
    @DisplayName("getEntityId returns memory id")
    fun getEntityId_returnsMemoryId() {
        val memory =
            MemoriesEntity(
                id = "memory-42",
                category = MemoryCategory.FACTS,
                content = "enc:hello",
                createdAt = 1L,
                updatedAt = 2L,
                deleted = false,
            )

        assertEquals("memory-42", handler.getEntityId(memory))
    }

    @Test
    @DisplayName("claimPending delegates to memories dao")
    fun claimPending_delegatesToDao() =
        runTest {
            handler.claimPending()

            assertEquals(1, memoriesDao.countCalls("claimPending"))
        }

    private fun createContext(memoriesDao: MemoriesDao): FirestoreSyncContext =
        testSyncContext(daos = testFirestoreSyncDaos(memoriesDao = memoriesDao))
}
