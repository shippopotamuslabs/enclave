package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.domain.port.repository.CleanupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CleanupRepositoryImpl @Inject constructor(
    private val database: PocketCrewDatabase
) : CleanupRepository {
    override suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            database.cleanupDao().clearUserData()
        }
    }
}
