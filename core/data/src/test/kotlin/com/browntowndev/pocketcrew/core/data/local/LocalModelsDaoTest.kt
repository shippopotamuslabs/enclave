package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
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

    @Test
    fun `test register and retrieve model`() = runTest {
        val entity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "unsloth/Qwen3-4B-GGUF",
            remoteFilename = "qwen3.gguf",
            localFilename = "qwen3.gguf",
            sha256 = "abc123",
            sizeInBytes = 1000L
        )
        val id = dao.upsert(entity)
        val retrieved = dao.getById(id)
        assertNotNull(retrieved)
        assertEquals("abc123", retrieved?.sha256)
        assertEquals("unsloth/Qwen3-4B-GGUF", retrieved?.huggingFaceModelName)
    }

    @Test
    fun `retrieve local model by SHA256`() = runTest {
        val entity = LocalModelEntity(
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
        val currentEntity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "sha1",
            sizeInBytes = 100L
        )
        val oldEntity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "llama",
            remoteFilename = "llama.gguf",
            localFilename = "llama.gguf",
            sha256 = "sha2",
            sizeInBytes = 200L
        )
        val currentId = dao.upsert(currentEntity)
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
        val entity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "abc123",
            sizeInBytes = 1000L
        )
        val id = dao.upsert(entity)
        dao.deleteById(id)
        val retrieved = dao.getById(id)
        assertNull(retrieved)
    }

    /**
     * Risk #7 Defense: Soft-deleted models leak into active model list via observeAllActive
     *
     * Scenario: LocalModelEntity(id=42) has no configs
     * When: LocalModelsDao.observeAllActive() is queried
     * Then: LocalModelEntity(42) is NOT in the result
     * And: SettingsUiState.localModels does NOT include model 42
     *
     * TDD Red: This test FAILS against the old implementation because
     * the active query did not exclude models with zero configs.
     *
     * The fix requires an EXISTS subquery to exclude models with no configs.
     */
    @Test
    fun `observeAllActive excludes models with zero configs`() = runTest {
        // Given - insert a model with NO configs
        val softDeletedEntity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "softdeleted123",
            sizeInBytes = 1000L
        )
        val softDeletedId = dao.upsert(softDeletedEntity)

        // Given - insert a normal active model WITH a config
        val activeEntity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "llama",
            remoteFilename = "llama.gguf",
            localFilename = "llama.gguf",
            sha256 = "active456",
            sizeInBytes = 2000L
        )
        val activeId = dao.upsert(activeEntity)
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
        val softDeletedModels = activeModels.filter { it.id == softDeletedId }
        assert(softDeletedModels.isEmpty()) {
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
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "softdeleted789",
            sizeInBytes = 1000L
        )
        dao.upsert(softDeletedEntity)

        // Given - active model with config
        val activeEntity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "llama",
            remoteFilename = "llama.gguf",
            localFilename = "llama.gguf",
            sha256 = "active789",
            sizeInBytes = 2000L
        )
        val activeId = dao.upsert(activeEntity)
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
