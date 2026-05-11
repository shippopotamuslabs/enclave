package com.browntowndev.pocketcrew.domain.port.repository

interface CleanupRepository {
    suspend fun clearAllData()
}
