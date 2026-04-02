package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalModelConfigurationsDaoTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var modelsDao: LocalModelsDao
    private lateinit var configDao: LocalModelConfigurationsDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
        modelsDao = database.localModelsDao()
        configDao = database.localModelConfigurationsDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and retrieve a tuning preset for a local model`() = runTest {
        val modelId = modelsDao.upsert(LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "abc",
            sizeInBytes = 100,
            modelStatus = ModelStatus.CURRENT
        ))
        
        val config = LocalModelConfigurationEntity(
            localModelId = modelId,
            displayName = "Creative",
            temperature = 0.9,
            systemPrompt = "You are a creative writer"
        )
        val configId = configDao.upsert(config)
        
        val retrieved = configDao.getById(configId)
        assertNotNull(retrieved)
        assertEquals("Creative", retrieved?.displayName)
        assertEquals(0.9, retrieved?.temperature ?: 0.0, 0.001)
        assertEquals("You are a creative writer", retrieved?.systemPrompt)
        
        val allConfigs = configDao.getAllForAsset(modelId)
        assertEquals(1, allConfigs.size)
    }

    @Test
    fun `multiple presets per local model`() = runTest {
        val modelId = modelsDao.upsert(LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "abc",
            sizeInBytes = 100,
            modelStatus = ModelStatus.CURRENT
        ))
        
        configDao.upsert(LocalModelConfigurationEntity(localModelId = modelId, displayName = "Precise"))
        configDao.upsert(LocalModelConfigurationEntity(localModelId = modelId, displayName = "Creative"))
        
        val allConfigs = configDao.getAllForAsset(modelId)
        assertEquals(2, allConfigs.size)
    }

    @Test
    fun `duplicate display name per local model asset is rejected via upsert replace`() = runTest {
        val modelId = modelsDao.upsert(LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF,
            huggingFaceModelName = "qwen",
            remoteFilename = "qwen.gguf",
            localFilename = "qwen.gguf",
            sha256 = "abc",
            sizeInBytes = 100,
            modelStatus = ModelStatus.CURRENT
        ))
        
        configDao.upsert(LocalModelConfigurationEntity(localModelId = modelId, displayName = "Creative"))
        configDao.upsert(LocalModelConfigurationEntity(localModelId = modelId, displayName = "Creative"))
        
        val allConfigs = configDao.getAllForAsset(modelId)
        assertEquals(1, allConfigs.size)
    }

    @Test
    fun `same display name on different assets is allowed`() = runTest {
        val model1Id = modelsDao.upsert(LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF, huggingFaceModelName = "qwen1", remoteFilename = "qwen1.gguf", localFilename = "qwen1.gguf", sha256 = "abc1", sizeInBytes = 100, modelStatus = ModelStatus.CURRENT
        ))
        val model2Id = modelsDao.upsert(LocalModelEntity(
            modelFileFormat = ModelFileFormat.GGUF, huggingFaceModelName = "qwen2", remoteFilename = "qwen2.gguf", localFilename = "qwen2.gguf", sha256 = "abc2", sizeInBytes = 100, modelStatus = ModelStatus.CURRENT
        ))
        
        configDao.upsert(LocalModelConfigurationEntity(localModelId = model1Id, displayName = "Creative"))
        configDao.upsert(LocalModelConfigurationEntity(localModelId = model2Id, displayName = "Creative"))
        
        val allConfigs1 = configDao.getAllForAsset(model1Id)
        val allConfigs2 = configDao.getAllForAsset(model2Id)
        
        assertEquals(1, allConfigs1.size)
        assertEquals(1, allConfigs2.size)
        assertEquals("Creative", allConfigs1[0].displayName)
        assertEquals("Creative", allConfigs2[0].displayName)
    }
}
