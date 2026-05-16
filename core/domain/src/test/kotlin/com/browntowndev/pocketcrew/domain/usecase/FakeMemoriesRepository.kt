package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.memory.Memory
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Lightweight fake for settings cleanup tests — listing/deletion only. */
class FakeMemoriesRepository : MemoriesRepository {

    private val _memories = MutableStateFlow<List<Memory>>(emptyList())
    private val deletedIds = mutableSetOf<String>()

    val deletedMemoryIds: Set<String>
        get() = deletedIds.toSet()

    var deleteMemoryFailure: RuntimeException? = null

    fun setMemories(memories: List<Memory>) {
        _memories.value = memories
    }

    override fun getAllMemoriesFlow(): Flow<List<Memory>> = _memories

    override suspend fun deleteMemory(id: String) {
        deleteMemoryFailure?.let { throw it }
        deletedIds.add(id)
        _memories.value = _memories.value.filter { it.id != id }
    }

    override suspend fun getCoreMemories(): List<Memory> = unsupported()

    override suspend fun insertMemory(category: MemoryCategory, content: String): Memory =
        unsupported()

    override suspend fun updateMemory(id: String, content: String, category: MemoryCategory?) =
        unsupported()

    override suspend fun getMemoryById(id: String): Memory? = unsupported()

    override suspend fun searchMemories(queryVector: FloatArray, limit: Int): List<Memory> =
        unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("FakeMemoriesRepository: not implemented")
}
