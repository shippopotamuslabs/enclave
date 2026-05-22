package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.MemoriesEntity
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class MemoriesSyncHandlerTest {
    private val memoriesDao = mockk<MemoriesDao>(relaxed = true)
    private lateinit var handler: MemoriesSyncHandler

    @BeforeEach
    fun setup() {
        handler = MemoriesSyncHandler(createContext(memoriesDao))
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
            coEvery { memoriesDao.claimPending() } returns Unit

            handler.claimPending()

            coVerify { memoriesDao.claimPending() }
        }

    private fun createContext(memoriesDao: MemoriesDao): FirestoreSyncContext {
        val localEncryption = FakeLocalDataEncryptionService()
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        return FirestoreSyncContext(
            firestore = mockk<FirebaseFirestore>(relaxed = true),
            daos = testFirestoreSyncDaos(memoriesDao = memoriesDao),
            localEncryption = localEncryption,
            sensitiveSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort),
            firestoreSensitiveFieldRepairer = mockk(relaxed = true),
            cryptoManager = mockk<CryptoManagerPort>(relaxed = true),
            loggingPort = loggingPort,
            apiKeyManager = mockk(relaxed = true),
            embeddingCoordinator = mockk(relaxed = true),
            userIdProvider = { "user-1" },
            purgeInProgress = AtomicBoolean(false),
        )
    }
}
