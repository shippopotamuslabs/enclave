package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.DefaultModelEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelRegistryImplOptimizeTest {

    private lateinit var modelsDao: LocalModelsDao
    private lateinit var configsDao: LocalModelConfigurationsDao
    private lateinit var defaultModelsDao: DefaultModelsDao
    private lateinit var transactionProvider: TransactionProvider
    private lateinit var logger: LoggingPort
    private lateinit var modelRegistry: ModelRegistryImpl

    @BeforeEach
    fun setup() {
        modelsDao = mockk(relaxed = true)
        configsDao = mockk(relaxed = true)
        defaultModelsDao = mockk(relaxed = true)
        transactionProvider = mockk()
        logger = mockk(relaxed = true)

        modelRegistry = ModelRegistryImpl(
            modelsDao = modelsDao,
            configsDao = configsDao,
            defaultModelsDao = defaultModelsDao,
            transactionProvider = transactionProvider,
            logger = logger
        )
    }

    private fun createModelEntity(id: Long, modelType: ModelType, status: ModelStatus) = LocalModelEntity(
        id = id,
        modelFileFormat = ModelFileFormat.LITERTLM,
        huggingFaceModelName = "repo/${modelType.name.lowercase()}",
        remoteFilename = "${modelType.name.lowercase()}.bin",
        localFilename = "${modelType.name.lowercase()}.bin",
        sha256 = "sha-$id",
        sizeInBytes = 100L,
        modelStatus = status,
        thinkingEnabled = false,
        isVision = modelType == ModelType.VISION
    )

    @Test
    fun `getAssetsPreferringOld returns assets for the default configurations`() = runTest {
        val mainModelId = 10L
        val visionModelId = 20L
        val mainConfigId = 100L
        val visionConfigId = 200L

        coEvery { defaultModelsDao.getAll() } returns listOf(
            DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = mainConfigId),
            DefaultModelEntity(modelType = ModelType.VISION, localConfigId = visionConfigId)
        )
        coEvery { configsDao.getById(mainConfigId) } returns LocalModelConfigurationEntity(
            id = mainConfigId,
            localModelId = mainModelId,
            displayName = "Main preset"
        )
        coEvery { configsDao.getById(visionConfigId) } returns LocalModelConfigurationEntity(
            id = visionConfigId,
            localModelId = visionModelId,
            displayName = "Vision preset"
        )
        coEvery { modelsDao.getById(mainModelId) } returns createModelEntity(mainModelId, ModelType.MAIN, ModelStatus.OLD)
        coEvery { modelsDao.getById(visionModelId) } returns createModelEntity(visionModelId, ModelType.VISION, ModelStatus.CURRENT)
        coEvery { configsDao.getAllForAsset(mainModelId) } returns listOf(
            LocalModelConfigurationEntity(id = mainConfigId, localModelId = mainModelId, displayName = "Main preset")
        )
        coEvery { configsDao.getAllForAsset(visionModelId) } returns listOf(
            LocalModelConfigurationEntity(id = visionConfigId, localModelId = visionModelId, displayName = "Vision preset")
        )

        val result = modelRegistry.getAssetsPreferringOld()

        assertEquals(2, result.size)

        val mainAsset = result[ModelType.MAIN]
        assertNotNull(mainAsset)
        assertEquals(mainModelId, mainAsset!!.metadata.id)
        assertEquals("Main preset", mainAsset.configurations.single().displayName)

        val visionAsset = result[ModelType.VISION]
        assertNotNull(visionAsset)
        assertEquals(visionModelId, visionAsset!!.metadata.id)
        assertEquals("Vision preset", visionAsset.configurations.single().displayName)
    }
}
