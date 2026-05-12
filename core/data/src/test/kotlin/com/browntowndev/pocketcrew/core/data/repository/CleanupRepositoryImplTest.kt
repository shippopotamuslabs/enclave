package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.CleanupDao
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CleanupRepositoryImplTest {
    private val database = mockk<PocketCrewDatabase>()
    private val cleanupDao = mockk<CleanupDao>(relaxed = true)
    private val apiKeyManager = mockk<ApiKeyManager>(relaxed = true)
    private lateinit var cleanupRepository: CleanupRepositoryImpl

    @Before
    fun setup() {
        every { database.cleanupDao() } returns cleanupDao
        cleanupRepository = CleanupRepositoryImpl(database, apiKeyManager)
    }

    @Test
    fun `clearAllData clears database and ApiKeyManager`() =
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
}
