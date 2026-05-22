package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.CleanupDao
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CleanupRepositoryImplTest {
    private val database = mockk<EnclaveDatabase>()
    private val cleanupDao = mockk<CleanupDao>(relaxed = true)
    private val apiKeyManager = mockk<ApiKeyManager>(relaxed = true)
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private lateinit var cleanupRepository: CleanupRepositoryImpl

    @BeforeEach
    fun setup() {
        every { database.cleanupDao() } returns cleanupDao
        cleanupRepository = CleanupRepositoryImpl(database, apiKeyManager, syncTrigger)
    }

    @Test
    fun `clearAllData wipes everything including models`() =
        runTest {
            cleanupRepository.clearAllData()

            coVerify { cleanupDao.clearUserData() }
            verify {
                apiKeyManager.delete(ApiKeyManager.TAVILY_SEARCH_ALIAS)
                apiKeyManager.delete(ApiKeyManager.EXA_SEARCH_ALIAS)
                apiKeyManager.delete(ApiKeyManager.X_SEARCH_ALIAS)
            }
            coVerify { apiKeyManager.clearAllPending() }
        }

    @Test
    fun `clearUserContent wipes chats and search keys but preserves models`() =
        runTest {
            cleanupRepository.clearUserContent()

            coVerify { cleanupDao.clearUserContent() }
            coVerify(exactly = 0) { cleanupDao.clearUserData() }
            verify {
                apiKeyManager.delete(ApiKeyManager.TAVILY_SEARCH_ALIAS)
                apiKeyManager.delete(ApiKeyManager.EXA_SEARCH_ALIAS)
                apiKeyManager.delete(ApiKeyManager.X_SEARCH_ALIAS)
            }
            coVerify { apiKeyManager.clearAllPending() }
        }

    @Test
    fun `deleteAllArtifacts deletes documents and triggers cloud purge`() =
        runTest {
            cleanupRepository.deleteAllArtifacts()

            coVerify { cleanupDao.deleteDocuments() }
            verify { syncTrigger.triggerCloudArtifactsPurge() }
            coVerify(exactly = 0) { cleanupDao.clearUserData() }
            coVerify(exactly = 0) { cleanupDao.clearUserContent() }
        }
}
