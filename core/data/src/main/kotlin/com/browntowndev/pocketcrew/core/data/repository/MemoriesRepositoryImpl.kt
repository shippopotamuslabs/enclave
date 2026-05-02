package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.MemoriesDao
import com.browntowndev.pocketcrew.core.data.local.MemoriesEntity
import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.domain.model.memory.Memory
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoriesRepositoryImpl @Inject constructor(
    private val memoriesDao: MemoriesDao,
    private val embeddingDao: EmbeddingDao,
    private val embeddingEngine: EmbeddingEnginePort
) : MemoriesRepository {
    override fun getAllMemoriesFlow(): Flow<List<Memory>> = 
        memoriesDao.getAllMemoriesFlow().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getCoreMemories(): List<Memory> {
        return memoriesDao.getMemoriesByCategories(
            listOf(MemoryCategory.CORE_IDENTITY, MemoryCategory.PREFERENCES)
        ).map { it.toDomain() }
    }

    override suspend fun insertMemory(category: MemoryCategory, content: String): Memory {
        val entity = MemoriesEntity(category = category, content = content)
        memoriesDao.insertMemory(entity)
        updateEmbedding(entity.id, content)
        return entity.toDomain()
    }

    override suspend fun updateMemory(id: String, content: String, category: MemoryCategory?) {
        val existing = memoriesDao.getMemoryById(id) ?: return
        val updated = existing.copy(
            content = content, 
            category = category ?: existing.category, 
            updatedAt = System.currentTimeMillis()
        )
        memoriesDao.updateMemory(updated)
        updateEmbedding(updated.id, content)
    }

    override suspend fun deleteMemory(id: String) {
        val existing = memoriesDao.getMemoryById(id) ?: return
        memoriesDao.deleteMemory(existing)
        deleteEmbedding(id)
    }

    private suspend fun updateEmbedding(id: String, content: String) {
        val vector = embeddingEngine.getEmbedding(content)
        val vectorString = vector.joinToString(",", prefix = "[", postfix = "]")
        val query = SimpleSQLiteQuery("INSERT OR REPLACE INTO memory_embeddings(id, vector) VALUES ('$id', '$vectorString')")
        embeddingDao.insertEmbedding(query)
    }

    private suspend fun deleteEmbedding(id: String) {
        val query = SimpleSQLiteQuery("DELETE FROM memory_embeddings WHERE id = '$id'")
        embeddingDao.insertEmbedding(query) // Re-using insert method for general execution
    }

    override suspend fun searchMemories(queryVector: FloatArray, limit: Int): List<Memory> {
        val vectorString = queryVector.joinToString(",", prefix = "[", postfix = "]")
        val sql = """
            SELECT m.* FROM memories m
            JOIN memory_embeddings me ON m.id = me.id
            WHERE me.vector MATCH '$vectorString' AND k = $limit
            ORDER BY distance
        """.trimIndent()
        
        return memoriesDao.searchMemoriesRaw(SimpleSQLiteQuery(sql)).map { it.toDomain() }
    }
}
