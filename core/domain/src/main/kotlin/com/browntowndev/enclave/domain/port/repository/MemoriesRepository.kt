package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import kotlinx.coroutines.flow.Flow

interface MemoriesRepository {
    fun getAllMemoriesFlow(): Flow<List<Memory>>

    suspend fun getCoreMemories(): List<Memory>

    suspend fun insertMemory(
        category: MemoryCategory,
        content: String,
    ): Memory

    suspend fun updateMemory(
        id: String,
        content: String,
        category: MemoryCategory? = null,
    )

    suspend fun getMemoryById(id: String): Memory?

    suspend fun deleteMemory(id: String)

    suspend fun deleteAllMemories()

    suspend fun searchMemories(
        queryVector: FloatArray,
        limit: Int = 5,
        maxDistance: Float? = null,
        excludeCategories: Set<MemoryCategory> = emptySet(),
    ): List<Memory>
}
