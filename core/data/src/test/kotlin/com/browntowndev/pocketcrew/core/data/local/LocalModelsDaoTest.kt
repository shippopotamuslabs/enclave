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
    fun `test register and retrieve model`() = runTest {
        val entity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "unsloth/Qwen3-4B-GGUF",
            remoteFilename = "qwen3.gguf",
            localFilename = "qwen3.gguf",
            sha256 = "abc123",
            sizeInBytes = 1000L,
            modelStatus = ModelStatus.CURRENT
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
            sizeInBytes = 100L,
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
            modelStatus = ModelStatus.CURRENT
        )
        val oldEntity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "llama",
            remoteFilename = "llama.gguf",
            localFilename = "llama.gguf",
            sha256 = "sha2",
            sizeInBytes = 200L,
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
            modelStatus = ModelStatus.CURRENT
        )
        val id = dao.upsert(entity)
        dao.deleteById(id)
        val retrieved = dao.getById(id)
        assertNull(retrieved)
    }

    /**
     * Risk #7 Defense: Soft-deleted models leak into active model list via observeAllCurrent
     *
     * Scenario: LocalModelEntity(id=42) is soft-deleted (0 configs)
     * When: LocalModelsDao.observeAllCurrent() is queried
     * Then: LocalModelEntity(42) is NOT in the result
     * And: SettingsUiState.localModels does NOT include model 42
     *
     * TDD Red: This test FAILS against current implementation because
     * observeAllCurrent() uses `WHERE model_status = 'CURRENT'` which includes
     * soft-deleted models (they retain CURRENT status but have 0 configs).
     *
     * The fix requires an EXISTS subquery to exclude models with no configs.
     */
    @Test
    fun `observeAllCurrent excludes soft-deleted models with zero configs`() = runTest {
        // Given - insert a model with CURRENT status but NO configs (soft-deleted)
        val softDeletedEntity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "softdeleted123",
            sizeInBytes = 1000L,
            modelStatus = ModelStatus.CURRENT
            // Note: NO configs inserted - this is the soft-deleted state
        )
        val softDeletedId = dao.upsert(softDeletedEntity)

        // Given - insert a normal active model WITH a config
        val activeEntity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "llama",
            remoteFilename = "llama.gguf",
            localFilename = "llama.gguf",
            sha256 = "active456",
            sizeInBytes = 2000L,
            modelStatus = ModelStatus.CURRENT
        )
        val activeId = dao.upsert(activeEntity)
        val configDao = database.localModelConfigurationsDao()
        configDao.upsert(LocalModelConfigurationEntity(
            localModelId = activeId,
            displayName = "Llama Default",
            temperature = 0.7
        ))

        // When - observe all current models
        val currentModels = dao.observeAllCurrent().first()

        // Then - soft-deleted model should NOT appear (0 configs)
        val softDeletedModels = currentModels.filter { it.id == softDeletedId }
        assert(softDeletedModels.isEmpty()) {
            "Soft-deleted model (0 configs) should NOT appear in observeAllCurrent(), but found: ${currentModels.map { it.sha256 }}"
        }

        // And - active model should appear
        val activeModels = currentModels.filter { it.id == activeId }
        assert(activeModels.size == 1) {
            "Active model (has configs) should appear in observeAllCurrent()"
        }
    }

    /**
     * Risk #7 Defense for getAllCurrent() (suspend version)
     */
    @Test
    fun `getAllCurrent excludes soft-deleted models with zero configs`() = runTest {
        // Given - soft-deleted model (CURRENT status, 0 configs)
        val softDeletedEntity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "softdeleted789",
            sizeInBytes = 1000L,
            modelStatus = ModelStatus.CURRENT
        )
        dao.upsert(softDeletedEntity)

        // Given - active model with config
        val activeEntity = LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "llama",
            remoteFilename = "llama.gguf",
            localFilename = "llama.gguf",
            sha256 = "active789",
            sizeInBytes = 2000L,
            modelStatus = ModelStatus.CURRENT
        )
        val activeId = dao.upsert(activeEntity)
        val configDao = database.localModelConfigurationsDao()
        configDao.upsert(LocalModelConfigurationEntity(
            localModelId = activeId,
            displayName = "Llama Default",
            temperature = 0.7
        ))

        // When
        val currentModels = dao.getAllCurrent()

        // Then - soft-deleted model should NOT appear
        val softDeletedModels = currentModels.filter { it.sha256 == "softdeleted789" }
        assert(softDeletedModels.isEmpty()) {
            "Soft-deleted model should NOT appear in getAllCurrent(), but found: ${currentModels.map { it.sha256 }}"
        }

        // And - active model should appear
        val activeModels = currentModels.filter { it.sha256 == "active789" }
        assert(activeModels.size == 1)
    }
}