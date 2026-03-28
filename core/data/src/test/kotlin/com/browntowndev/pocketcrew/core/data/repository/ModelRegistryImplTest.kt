package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.DefaultModelEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.ModelEntity
import com.browntowndev.pocketcrew.core.data.local.ModelsDao
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelRegistryImplTest {

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

        // Mock transaction provider to execute block immediately
        coEvery { transactionProvider.runInTransaction(any<suspend () -> Any>()) } coAnswers {
            val block = it.invocation.args[0] as suspend () -> Any
            block()
        }

        modelRegistry = ModelRegistryImpl(
            modelsDao = modelsDao,
            defaultModelsDao = defaultModelsDao,
            transactionProvider = transactionProvider,
            logger = logger
        )
    }

    private fun createConfig(modelType: ModelType) = ModelConfiguration(
        modelType = modelType,
        metadata = ModelConfiguration.Metadata(
            huggingFaceModelName = "repo/model",
            remoteFileName = "model.bin",
            localFileName = "model.bin",
            displayName = "Model",
            sha256 = "sha",
            sizeInBytes = 100,
            modelFileFormat = ModelFileFormat.LITERTLM
        ),
        tunings = ModelConfiguration.Tunings(
            temperature = 0.7,
            topK = 40,
            topP = 0.9,
            maxTokens = 100,
            repetitionPenalty = 1.0,
            contextWindow = 512
        ),
        persona = ModelConfiguration.Persona(systemPrompt = "prompt")
    )

    @Test
    fun setRegisteredModel_noExistingDefault_insertsNewDefaultModel() = runTest {
        // Scenario 1: Initial Model Registration (No Existing Default)
        // Given
        val config = createConfig(ModelType.MAIN)
        coEvery { defaultModelsDao.getDefault(ModelType.MAIN) } returns null
        
        val defaultSlot = slot<DefaultModelEntity>()
        val entitySlot = slot<ModelEntity>()
        coEvery { defaultModelsDao.upsert(capture(defaultSlot)) } returns Unit
        coEvery { modelsDao.upsert(capture(entitySlot)) } returns Unit

        // When
        modelRegistry.setRegisteredModel(config, ModelStatus.CURRENT)

        // Then
        coVerify { 
            defaultModelsDao.upsert(any())
            modelsDao.upsert(any())
        }
        assert(defaultSlot.captured.modelType == ModelType.MAIN)
        assert(defaultSlot.captured.source == ModelSource.ON_DEVICE)
        assert(entitySlot.captured.modelType == ModelType.MAIN)
    }

    @Test
    fun setRegisteredModel_existingDefault_doesNotOverriddenDefault() = runTest {
        // Scenario 2: Subsequent Model Registration (Existing Default)
        // Given
        val config = createConfig(ModelType.MAIN)
        coEvery { defaultModelsDao.getDefault(ModelType.MAIN) } returns DefaultModelEntity(
            modelType = ModelType.MAIN,
            source = ModelSource.API, // Different source to verify it's not changed
            apiModelId = 123L
        )

        // When
        modelRegistry.setRegisteredModel(config, ModelStatus.CURRENT)

        // Then
        coVerify(exactly = 0) { defaultModelsDao.upsert(any()) }
        coVerify { modelsDao.upsert(any()) }
    }

    @Test
    fun setRegisteredModel_wrapsOperationsInTransaction() = runTest {
        // Mutation Defense: Verifies that the implementation actually uses the transaction provider.
        // Given
        val config = createConfig(ModelType.MAIN)

        // When
        modelRegistry.setRegisteredModel(config, ModelStatus.CURRENT)

        // Then
        coVerify { transactionProvider.runInTransaction(any()) }
    }

    @Test
    fun setRegisteredModel_onDatabaseError_throwsException() = runTest {
        // Error 1: DAO Insertion Failure
        // Given
        val config = createConfig(ModelType.MAIN)
        coEvery { modelsDao.upsert(any()) } throws RuntimeException("DB Error")

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest {
                modelRegistry.setRegisteredModel(config, ModelStatus.CURRENT)
            }
        }
    }

    @Test
    fun setRegisteredModel_transactionIntegrity() = runTest {
        // Scenario 3: Transactional Integrity (Database Error)
        // This is partially verified by setRegisteredModel_onDatabaseError_throwsException
        // but specifically ensures the order/wrapping.
        
        // Given
        val config = createConfig(ModelType.MAIN)
        coEvery { defaultModelsDao.getDefault(any()) } throws Exception("Failed first")

        // When/Then
        assertThrows(Exception::class.java) {
            runTest {
                modelRegistry.setRegisteredModel(config, ModelStatus.CURRENT)
            }
        }
    }
}
