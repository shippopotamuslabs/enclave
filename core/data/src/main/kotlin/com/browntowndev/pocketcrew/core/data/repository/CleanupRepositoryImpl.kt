package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.domain.port.repository.CleanupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CleanupRepositoryImpl @Inject constructor(
    private val database: PocketCrewDatabase,
    private val apiKeyManager: com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
) : CleanupRepository {
    override suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            database.cleanupDao().clearUserData()
            
            // Clear all aliases from EncryptedSharedPreferences
            com.browntowndev.pocketcrew.core.data.security.ApiKeyManager.let { 
                apiKeyManager.delete(it.TAVILY_SEARCH_ALIAS)
                apiKeyManager.delete(it.EXA_SEARCH_ALIAS)
                apiKeyManager.delete(it.X_SEARCH_ALIAS)
            }
            // Clear all pending sync state from DataStore
            apiKeyManager.clearAllPending()
        }
    }
}
