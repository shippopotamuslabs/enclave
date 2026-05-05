package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.domain.port.repository.CleanupRepository
import javax.inject.Inject

class CleanupRepositoryImpl @Inject constructor(
    private val database: PocketCrewDatabase
) : CleanupRepository {
    override suspend fun clearAllData() {
        database.clearAllTables()
    }
}
