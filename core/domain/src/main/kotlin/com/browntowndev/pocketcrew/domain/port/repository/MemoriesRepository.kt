package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.memory.Memory
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import kotlinx.coroutines.flow.Flow

interface MemoriesRepository {
    fun getAllMemoriesFlow(): Flow<List<Memory>>
    suspend fun getCoreMemories(): List<Memory>
    suspend fun insertMemory(category: MemoryCategory, content: String): Memory
    suspend fun updateMemory(id: String, content: String, category: MemoryCategory? = null)
    suspend fun deleteMemory(id: String)
    suspend fun searchMemories(queryVector: FloatArray, limit: Int = 5): List<Memory>
}
