package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.port.repository.CleanupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CleanupRepositoryImpl
    @Inject
    constructor(
        private val database: PocketCrewDatabase,
        private val apiKeyManager: com.browntowndev.pocketcrew.core.data.security.ApiKeyManager,
        private val syncTrigger: CloudSyncTrigger,
    ) : CleanupRepository {
        override suspend fun clearAllData() {
            withContext(Dispatchers.IO) {
                database.cleanupDao().clearUserData()
                clearSearchKeys()
            }
        }

        override suspend fun clearUserContent() {
            withContext(Dispatchers.IO) {
                database.cleanupDao().clearUserContent()
                clearSearchKeys()
            }
        }

        private suspend fun clearSearchKeys() {
            com.browntowndev.pocketcrew.core.data.security.ApiKeyManager.let {
                apiKeyManager.delete(it.TAVILY_SEARCH_ALIAS)
                apiKeyManager.delete(it.EXA_SEARCH_ALIAS)
                apiKeyManager.delete(it.X_SEARCH_ALIAS)
            }
            apiKeyManager.clearAllPending()
        }

        override suspend fun deleteAllArtifacts() {
            withContext(Dispatchers.IO) {
                database.cleanupDao().deleteDocuments()
                syncTrigger.triggerCloudArtifactsPurge()
            }
        }
    }
