package com.browntowndev.enclave.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncStateStore
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.port.repository.CleanupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CleanupRepositoryImpl
    @Inject
    constructor(
        private val database: EnclaveDatabase,
        private val apiKeyManager: com.browntowndev.enclave.core.data.security.ApiKeyManager,
        private val syncTrigger: CloudSyncTrigger,
        private val preferencesDataStore: DataStore<Preferences>,
        private val userIdProvider: () -> String?,
    ) : CleanupRepository {
        override suspend fun clearAllData() {
            withContext(Dispatchers.IO) {
                database.cleanupDao().clearUserData()
                clearSearchKeys()
                resetCloudSyncCursor()
            }
        }

        private suspend fun resetCloudSyncCursor() {
            val userId = userIdProvider() ?: return
            FirestoreSyncStateStore(preferencesDataStore).setLastAppliedSequence(userId, 0L)
        }

        override suspend fun clearUserContent() {
            withContext(Dispatchers.IO) {
                database.cleanupDao().clearUserContent()
                clearSearchKeys()
            }
        }

        private suspend fun clearSearchKeys() {
            com.browntowndev.enclave.core.data.security.ApiKeyManager.let {
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
