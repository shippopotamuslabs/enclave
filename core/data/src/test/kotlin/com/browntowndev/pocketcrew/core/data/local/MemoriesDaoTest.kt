package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoriesDaoTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var dao: MemoriesDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.memoriesDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and retrieve memory`() = runTest {
        val id = UUID.randomUUID().toString()
        val memory = MemoriesEntity(
            id = id,
            category = MemoryCategory.FACTS,
            content = "The sky is blue."
        )
        dao.insertMemory(memory)
        val retrieved = dao.getMemoryById(id)
        assertNotNull(retrieved)
        assertEquals("The sky is blue.", retrieved?.content)
        assertEquals(MemoryCategory.FACTS, retrieved?.category)
    }

    @Test
    fun `update memory`() = runTest {
        val id = UUID.randomUUID().toString()
        val memory = MemoriesEntity(
            id = id,
            category = MemoryCategory.FACTS,
            content = "The sky is blue."
        )
        dao.insertMemory(memory)
        
        val updatedMemory = memory.copy(content = "The sky is clear blue.")
        dao.updateMemory(updatedMemory)
        
        val retrieved = dao.getMemoryById(id)
        assertEquals("The sky is clear blue.", retrieved?.content)
    }

    @Test
    fun `delete memory`() = runTest {
        val id = UUID.randomUUID().toString()
        val memory = MemoriesEntity(
            id = id,
            category = MemoryCategory.FACTS,
            content = "The sky is blue."
        )
        dao.insertMemory(memory)
        dao.deleteMemory(memory)
        
        val retrieved = dao.getMemoryById(id)
        assertNull(retrieved)
    }

    @Test
    fun `get memories by categories`() = runTest {
        val memory1 = MemoriesEntity(
            category = MemoryCategory.FACTS,
            content = "Fact 1"
        )
        val memory2 = MemoriesEntity(
            category = MemoryCategory.PREFERENCES,
            content = "Preference 1"
        )
        val memory3 = MemoriesEntity(
            category = MemoryCategory.CORE_IDENTITY,
            content = "Identity 1"
        )
        dao.insertMemory(memory1)
        dao.insertMemory(memory2)
        dao.insertMemory(memory3)
        
        val retrieved = dao.getMemoriesByCategories(listOf(MemoryCategory.FACTS, MemoryCategory.PREFERENCES))
        assertEquals(2, retrieved.size)
        val categories = retrieved.map { it.category }
        assert(categories.contains(MemoryCategory.FACTS))
        assert(categories.contains(MemoryCategory.PREFERENCES))
    }

    @Test
    fun `observe all memories flow`() = runTest {
        val memory1 = MemoriesEntity(
            category = MemoryCategory.FACTS,
            content = "Fact 1",
            updatedAt = 1000
        )
        val memory2 = MemoriesEntity(
            category = MemoryCategory.PREFERENCES,
            content = "Preference 1",
            updatedAt = 2000
        )
        dao.insertMemory(memory1)
        dao.insertMemory(memory2)
        
        val list = dao.getAllMemoriesFlow().first()
        assertEquals(2, list.size)
        assertEquals("Preference 1", list[0].content) // Ordered by updated_at DESC
        assertEquals("Fact 1", list[1].content)
    }
}
