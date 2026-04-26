package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.core.data.local.MemoriesDao
import com.browntowndev.pocketcrew.core.data.local.MemoriesEntity
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import androidx.sqlite.db.SupportSQLiteQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MemoriesRepositoryTest {

    private lateinit var memoriesDao: MemoriesDao
    private lateinit var embeddingDao: EmbeddingDao
    private lateinit var embeddingEngine: EmbeddingEnginePort
    private lateinit var repository: MemoriesRepository

    @BeforeEach
    fun setup() {
        memoriesDao = mockk(relaxed = true)
        embeddingDao = mockk(relaxed = true)
        embeddingEngine = mockk(relaxed = true)
        repository = MemoriesRepositoryImpl(memoriesDao, embeddingDao, embeddingEngine)
    }

    @Test
    fun `getAllMemoriesFlow returns flow from dao`() = runTest {
        val entities = listOf(
            MemoriesEntity(category = MemoryCategory.FACTS, content = "Fact 1")
        )
        every { memoriesDao.getAllMemoriesFlow() } returns flowOf(entities)

        val result = repository.getAllMemoriesFlow().first()

        assertEquals(entities.size, result.size)
        assertEquals(entities[0].content, result[0].content)
        coVerify { memoriesDao.getAllMemoriesFlow() }
    }

    @Test
    fun `getCoreMemories returns core identity and preferences`() = runTest {
        val coreEntities = listOf(
            MemoriesEntity(category = MemoryCategory.CORE_IDENTITY, content = "I am an AI"),
            MemoriesEntity(category = MemoryCategory.PREFERENCES, content = "I like blue")
        )
        coEvery { memoriesDao.getMemoriesByCategories(any()) } returns coreEntities

        val result = repository.getCoreMemories()

        assertEquals(coreEntities.size, result.size)
        assertEquals(coreEntities[0].content, result[0].content)
        coVerify {
            memoriesDao.getMemoriesByCategories(
                listOf(MemoryCategory.CORE_IDENTITY, MemoryCategory.PREFERENCES)
            )
        }
    }

    @Test
    fun `insertMemory inserts into dao and updates embedding`() = runTest {
        val category = MemoryCategory.FACTS
        val content = "New fact"
        val vector = floatArrayOf(0.1f, 0.2f)
        coEvery { embeddingEngine.getEmbedding(content) } returns vector

        val result = repository.insertMemory(category, content)

        assertEquals(category, result.category)
        assertEquals(content, result.content)
        coVerify { memoriesDao.insertMemory(any()) }
        coVerify { embeddingEngine.getEmbedding(content) }
        
        val querySlot = slot<SupportSQLiteQuery>()
        coVerify { embeddingDao.insertEmbedding(capture(querySlot)) }
        val sql = querySlot.captured.sql
        assert(sql.contains("INSERT OR REPLACE INTO memory_embeddings"))
        assert(sql.contains(result.id))
        assert(sql.contains("[0.1,0.2]"))
    }

    @Test
    fun `updateMemory updates dao and embedding`() = runTest {
        val id = "memory-1"
        val existing = MemoriesEntity(id = id, category = MemoryCategory.FACTS, content = "Old content")
        val newContent = "Updated content"
        val vector = floatArrayOf(0.3f, 0.4f)
        
        coEvery { memoriesDao.getMemoryById(id) } returns existing
        coEvery { embeddingEngine.getEmbedding(newContent) } returns vector

        repository.updateMemory(id, newContent)

        coVerify { memoriesDao.updateMemory(match { it.id == id && it.content == newContent }) }
        coVerify { embeddingEngine.getEmbedding(newContent) }
        
        val querySlot = slot<SupportSQLiteQuery>()
        coVerify { embeddingDao.insertEmbedding(capture(querySlot)) }
        val sql = querySlot.captured.sql
        assert(sql.contains("INSERT OR REPLACE INTO memory_embeddings"))
        assert(sql.contains(id))
        assert(sql.contains("[0.3,0.4]"))
    }

    @Test
    fun `deleteMemory deletes from dao and removes embedding`() = runTest {
        val id = "memory-1"
        val existing = MemoriesEntity(id = id, category = MemoryCategory.FACTS, content = "Content")
        
        coEvery { memoriesDao.getMemoryById(id) } returns existing

        repository.deleteMemory(id)

        coVerify { memoriesDao.deleteMemory(existing) }
        
        val querySlot = slot<SupportSQLiteQuery>()
        coVerify { embeddingDao.insertEmbedding(capture(querySlot)) }
        val sql = querySlot.captured.sql
        assert(sql.contains("DELETE FROM memory_embeddings WHERE id = 'memory-1'"))
    }

    @Test
    fun `searchMemories performs raw query on dao`() = runTest {
        val queryVector = floatArrayOf(0.5f, 0.6f)
        val limit = 3
        val expectedEntities = listOf(
            MemoriesEntity(category = MemoryCategory.FACTS, content = "Result")
        )
        
        coEvery { memoriesDao.searchMemoriesRaw(any()) } returns expectedEntities

        val result = repository.searchMemories(queryVector, limit)

        assertEquals(expectedEntities.size, result.size)
        assertEquals(expectedEntities[0].content, result[0].content)
        
        val querySlot = slot<SupportSQLiteQuery>()
        coVerify { memoriesDao.searchMemoriesRaw(capture(querySlot)) }
        val sql = querySlot.captured.sql
        assert(sql.contains("SELECT m.* FROM memories m"))
        assert(sql.contains("JOIN memory_embeddings me ON m.id = me.id"))
        assert(sql.contains("WHERE me.vector MATCH '[0.5,0.6]'"))
        assert(sql.contains("LIMIT 3"))
    }
}
