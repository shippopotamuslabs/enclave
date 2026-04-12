package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
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

    private fun nextId() = LocalModelId(UUID.randomUUID().toString())

    @Test
    fun `test register and retrieve model`() = runTest {
        val entityId = nextId()
        val entity = LocalModelEntity(
            id = entityId,
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "unsloth/Qwen3-4B-GGUF",
            remoteFilename = "qwen3.gguf",
            localFilename = "qwen3.gguf",
            sha256 = "abc123",
            sizeInBytes = 1000L
        )
        dao.upsert(entity)
        val retrieved = dao.getById(entityId)
        assertNotNull(retrieved)
        assertEquals("abc123", retrieved?.sha256)
        assertEquals("unsloth/Qwen3-4B-GGUF", retrieved?.huggingFaceModelName)
    }

    @Test
    fun `retrieve local model by SHA256`() = runTest {
        val entity = LocalModelEntity(
            id = nextId(),
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "deadbeef",
            sizeInBytes = 100L
        )
        dao.upsert(entity)
        val retrieved = dao.getBySha256("deadbeef")
        assertNotNull(retrieved)
        assertEquals("deadbeef", retrieved?.sha256)
    }

    @Test
    fun `observe only active models`() = runTest {
        val configDao = database.localModelConfigurationsDao()
        val currentId = nextId()
        val currentEntity = LocalModelEntity(
            id = currentId,
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "sha1",
            sizeInBytes = 100L
        )
        val oldEntity = LocalModelEntity(
            id = nextId(),
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "llama",
            remoteFilename = "llama.gguf",
            localFilename = "llama.gguf",
            sha256 = "sha2",
            sizeInBytes = 200L
        )
        dao.upsert(currentEntity)
        dao.upsert(oldEntity)
        configDao.upsert(
            LocalModelConfigurationEntity(
                id = LocalModelConfigurationId("test-config-1"),
                localModelId = currentId,
                displayName = "Default"
            )
        )
        val activeList = dao.observeAllActive().first()
        assertEquals(1, activeList.size)
        assertEquals("sha1", activeList[0].sha256)
    }

    @Test
    fun `delete a local model by ID`() = runTest {
        val entityId = nextId()
        val entity = LocalModelEntity(
            id = entityId,
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "abc123",
            sizeInBytes = 1000L
        )
        dao.upsert(entity)
        dao.deleteById(entityId)
        val retrieved = dao.getById(entityId)
        assertNull(retrieved)
    }

    /**
     * Risk #7 Defense: Soft-deleted models leak into active model list via observeAllActive
     */
    @Test
    fun `observeAllActive excludes models with zero configs`() = runTest {
        // Given - insert a model with NO configs
        val softDeletedId = nextId()
        val softDeletedEntity = LocalModelEntity(
            id = softDeletedId,
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "softdeleted123",
            sizeInBytes = 1000L
        )
        dao.upsert(softDeletedEntity)

        // Given - insert a normal active model WITH a config
        val activeId = nextId()
        val activeEntity = LocalModelEntity(
            id = activeId,
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "llama",
            remoteFilename = "llama.gguf",
            localFilename = "llama.gguf",
            sha256 = "active456",
            sizeInBytes = 2000L
        )
        dao.upsert(activeEntity)
        val configDao = database.localModelConfigurationsDao()
        configDao.upsert(LocalModelConfigurationEntity(
            id = LocalModelConfigurationId("test-config-1"),
            localModelId = activeId,
            displayName = "Llama Default",
            temperature = 0.7
        ))

        // When - observe all active models
        val activeModels = dao.observeAllActive().first()

        // Then - model with no configs should NOT appear
        val softDeletedResults = activeModels.filter { it.id == softDeletedId }
        assert(softDeletedResults.isEmpty()) {
            "Model with no configs should NOT appear in observeAllActive(), but found: ${activeModels.map { it.sha256 }}"
        }

        // And - active model should appear
        val activeResults = activeModels.filter { it.id == activeId }
        assert(activeResults.size == 1) {
            "Active model (has configs) should appear in observeAllActive()"
        }
    }

    /**
     * Risk #7 Defense for getAllActive() (suspend version)
     */
    @Test
    fun `getAllActive excludes models with zero configs`() = runTest {
        // Given - model with no configs
        val softDeletedEntity = LocalModelEntity(
            id = nextId(),
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "softdeleted789",
            sizeInBytes = 1000L
        )
        dao.upsert(softDeletedEntity)

        // Given - active model with config
        val activeId = nextId()
        val activeEntity = LocalModelEntity(
            id = activeId,
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "llama",
            remoteFilename = "llama.gguf",
            localFilename = "llama.gguf",
            sha256 = "active789",
            sizeInBytes = 2000L
        )
        dao.upsert(activeEntity)
        val configDao = database.localModelConfigurationsDao()
        configDao.upsert(LocalModelConfigurationEntity(
            id = LocalModelConfigurationId("test-config-1"),
            localModelId = activeId,
            displayName = "Llama Default",
            temperature = 0.7
        ))

        // When
        val activeModels = dao.getAllActive()

        // Then - model with no configs should NOT appear
        val softDeletedModels = activeModels.filter { it.sha256 == "softdeleted789" }
        assert(softDeletedModels.isEmpty()) {
            "Model with no configs should NOT appear in getAllActive(), but found: ${activeModels.map { it.sha256 }}"
        }

        // And - active model should appear
        val activeResults = activeModels.filter { it.sha256 == "active789" }
        assert(activeResults.size == 1)
    }
}
