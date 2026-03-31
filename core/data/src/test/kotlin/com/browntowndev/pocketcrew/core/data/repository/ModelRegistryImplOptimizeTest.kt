package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.ModelEntity
import com.browntowndev.pocketcrew.core.data.local.ModelsDao
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelRegistryImplOptimizeTest {

    private lateinit var modelsDao: ModelsDao
    private lateinit var defaultModelsDao: DefaultModelsDao
    private lateinit var transactionProvider: TransactionProvider
    private lateinit var logger: LoggingPort
    private lateinit var modelRegistry: ModelRegistryImpl

    @BeforeEach
    fun setup() {
        modelsDao = mockk(relaxed = true)
        defaultModelsDao = mockk(relaxed = true)
        transactionProvider = mockk()
        logger = mockk(relaxed = true)

        modelRegistry = ModelRegistryImpl(
            modelsDao = modelsDao,
            defaultModelsDao = defaultModelsDao,
            transactionProvider = transactionProvider,
            logger = logger
        )
    }

    private fun createEntity(modelType: ModelType, status: ModelStatus) = ModelEntity(
        modelType = modelType,
        modelStatus = status,
        remoteFilename = "model.bin",
        huggingFaceModelName = "repo/model",
        displayName = "Model ${modelType.name}",
        modelFileFormat = ModelFileFormat.LITERTLM,
        sha256 = "sha",
        sizeInBytes = 100,
        temperature = 0.7,
        topK = 40,
        topP = 0.9,
        minP = 0.05,
        maxTokens = 100,
        contextWindow = 512,
        thinkingEnabled = false,
        systemPrompt = "prompt",
        repetitionPenalty = 1.0
    )

    @Test
    fun getModelsPreferringOld_usesSingleQueryAndReturnsCorrectModels() = runTest {
        val allEntities = mutableListOf<ModelEntity>()
        // Give MAIN model an OLD and CURRENT entity. We should prefer OLD.
        allEntities.add(createEntity(ModelType.MAIN, ModelStatus.OLD))
        allEntities.add(createEntity(ModelType.MAIN, ModelStatus.CURRENT))

        // Give VISION model just a CURRENT entity. We should get CURRENT.
        allEntities.add(createEntity(ModelType.VISION, ModelStatus.CURRENT))

        coEvery { modelsDao.getModelsByStatuses(any()) } returns allEntities

        val result = modelRegistry.getModelsPreferringOld()

        // Assert we only queried once
        coVerify(exactly = 1) { modelsDao.getModelsByStatuses(any()) }
        coVerify(exactly = 0) { modelsDao.getModelEntityByStatus(any(), any()) }

        // Assert size
        assertEquals(2, result.size)

        // Assert MAIN is OLD
        val mainConfig = result.find { it.modelType == ModelType.MAIN }
        assertEquals(ModelType.MAIN, mainConfig?.modelType)

        // Assert VISION is CURRENT
        val visionConfig = result.find { it.modelType == ModelType.VISION }
        assertEquals(ModelType.VISION, visionConfig?.modelType)
    }
}
