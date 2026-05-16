package com.browntowndev.pocketcrew.domain.port.repository

interface CleanupRepository {
    suspend fun clearAllData()

    /** Deletes persisted artifact/document rows only (no full user wipe). */
    suspend fun deleteAllArtifacts()
}
