package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** In-memory [MemoriesRepository] fake (ENC-17). Search returns insertion order. */
class FakeMemoriesRepository(
    initial: List<Memory> = emptyList(),
) : MemoriesRepository {
    private val state = MutableStateFlow(initial)

    fun seed(memories: List<Memory>) {
        state.value = memories
    }

    val insertCalls = mutableListOf<Pair<MemoryCategory, String>>()
    val updateCalls = mutableListOf<Triple<String, String, MemoryCategory?>>()
    val deleteCalls = mutableListOf<String>()

    override fun getAllMemoriesFlow(): Flow<List<Memory>> = state.asStateFlow()

    override suspend fun getCoreMemories(): List<Memory> =
        state.value.filter { it.category in setOf(MemoryCategory.CORE_IDENTITY, MemoryCategory.PREFERENCES, MemoryCategory.PSYCH_PROFILE) }

    override suspend fun insertMemory(category: MemoryCategory, content: String): Memory {
        insertCalls += category to content
        val memory = Memory(id = UUID.randomUUID().toString(), category = category, content = content)
        state.value = state.value + memory
        return memory
    }

    override suspend fun updateMemory(id: String, content: String, category: MemoryCategory?) {
        updateCalls += Triple(id, content, category)
        state.value =
            state.value.map { memory ->
                if (memory.id == id) memory.copy(content = content, category = category ?: memory.category) else memory
            }
    }

    override suspend fun getMemoryById(id: String): Memory? = state.value.firstOrNull { it.id == id }

    /** When set, [deleteMemory] throws this instead of deleting (ENC-28, data batch). */
    var deleteFailure: Throwable? = null

    override suspend fun deleteMemory(id: String) {
        deleteFailure?.let { throw it }
        deleteCalls += id
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllMemories() {
        state.value = emptyList()
    }

    override suspend fun searchMemories(
        queryVector: FloatArray,
        limit: Int,
        maxDistance: Float?,
        excludeCategories: Set<MemoryCategory>,
    ): List<Memory> =
        state.value.filterNot { it.category in excludeCategories }.take(limit)
}
