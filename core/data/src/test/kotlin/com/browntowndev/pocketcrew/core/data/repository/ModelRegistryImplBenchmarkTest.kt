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
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class ModelRegistryImplBenchmarkTest {

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
    fun benchmarkGetModelsPreferringOld() = runTest {
        // Setup mock delays to simulate DB access (e.g. 5ms per query)
        val allEntities = mutableListOf<ModelEntity>()
        for (modelType in ModelType.entries) {
            // Randomly assign some to OLD and some to CURRENT
            val status = if (modelType.ordinal % 2 == 0) ModelStatus.OLD else ModelStatus.CURRENT
            allEntities.add(createEntity(modelType, status))
        }

        coEvery { modelsDao.getModelEntityByStatus(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(5) // Simulate 5ms db access
            val requestedType = it.invocation.args[0] as ModelType
            val requestedStatus = it.invocation.args[1] as ModelStatus
            allEntities.find { e -> e.modelType == requestedType && e.modelStatus == requestedStatus }
        }

        coEvery { modelsDao.getModelsByStatuses(any()) } coAnswers {
            kotlinx.coroutines.delay(10) // Simulate a slightly longer 10ms single query
            allEntities
        }

        // Warmup
        modelRegistry.getModelsPreferringOld()
        modelRegistry.getModelsPreferringOld()

        val runs = 10
        val totalTime = measureTimeMillis {
            for (i in 1..runs) {
                modelRegistry.getModelsPreferringOld()
            }
        }

        println("Baseline Benchmark (Average over $runs runs): ${totalTime / runs.toDouble()} ms")
    }
}
