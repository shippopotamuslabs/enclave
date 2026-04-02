package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
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

@RunWith(RobolectricTestRunner::class)
class LocalModelsDaoTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var dao: LocalModelsDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.localModelsDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and retrieve a local model asset`() = runTest {
        val entity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen/qwen3-4b",
            remoteFilename = "qwen3.gguf",
            localFilename = "qwen3.gguf",
            sha256 = "abc123",
            sizeInBytes = 1000L,
            displayName = "Qwen3-4B",
            modelStatus = ModelStatus.CURRENT
        )
        val id = dao.upsert(entity)
        val retrieved = dao.getById(id)
        assertNotNull(retrieved)
        assertEquals("abc123", retrieved?.sha256)
        assertEquals("Qwen3-4B", retrieved?.displayName)
    }

    @Test
    fun `retrieve local model by SHA256`() = runTest {
        val entity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "deadbeef",
            sizeInBytes = 100L,
            displayName = "Qwen",
            modelStatus = ModelStatus.CURRENT
        )
        dao.upsert(entity)
        val retrieved = dao.getBySha256("deadbeef")
        assertNotNull(retrieved)
        assertEquals("deadbeef", retrieved?.sha256)
    }

    @Test
    fun `observe only CURRENT models`() = runTest {
        val currentEntity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "sha1",
            sizeInBytes = 100L,
            displayName = "Qwen",
            modelStatus = ModelStatus.CURRENT
        )
        val oldEntity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "llama",
            remoteFilename = "llama.gguf",
            localFilename = "llama.gguf",
            sha256 = "sha2",
            sizeInBytes = 200L,
            displayName = "Llama",
            modelStatus = ModelStatus.OLD
        )
        dao.upsert(currentEntity)
        dao.upsert(oldEntity)
        val currentList = dao.observeAllCurrent().first()
        assertEquals(1, currentList.size)
        assertEquals("sha1", currentList[0].sha256)
    }

    @Test
    fun `delete a local model by ID`() = runTest {
        val entity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "abc123",
            sizeInBytes = 1000L,
            displayName = "Qwen",
            modelStatus = ModelStatus.CURRENT
        )
        val id = dao.upsert(entity)
        dao.deleteById(id)
        val retrieved = dao.getById(id)
        assertNull(retrieved)
    }
}