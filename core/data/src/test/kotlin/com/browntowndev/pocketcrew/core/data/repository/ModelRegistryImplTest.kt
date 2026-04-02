package com.browntowndev.pocketcrew.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelRegistryImplTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var repository: ModelRegistryImpl
    private val transactionProvider = mockk<TransactionProvider>()
    private val logger = mockk<LoggingPort>(relaxed = true)

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()

        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            (args[0] as suspend () -> Any).invoke()
        }
        every { logger.debug(any(), any()) } returns Unit

        repository = ModelRegistryImpl(
            modelsDao = database.localModelsDao(),
            configsDao = database.localModelConfigurationsDao(),
            defaultModelsDao = database.defaultModelsDao(),
            transactionProvider = transactionProvider,
            logger = logger
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `setRegisteredModel demotes previous slot model to OLD`() = runTest {
        val oldAsset = LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "old/model",
                remoteFileName = "old.bin",
                localFileName = "old.bin",
                sha256 = "old-sha",
                sizeInBytes = 1024L,
                modelFileFormat = ModelFileFormat.GGUF,
                visionCapable = false
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    displayName = "Old Config", 
                    localModelId = 0,
                    maxTokens = 4096,
                    contextWindow = 4096,
                    temperature = 0.7,
                    topP = 0.9,
                    topK = 40,
                    repetitionPenalty = 1.0,
                    systemPrompt = ""
                )
            )
        )
        
        repository.setRegisteredModel(ModelType.FAST, oldAsset, ModelStatus.CURRENT)
        
        val newAsset = LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "new/model",
                remoteFileName = "new.bin",
                localFileName = "new.bin",
                sha256 = "new-sha",
                sizeInBytes = 1024L,
                modelFileFormat = ModelFileFormat.GGUF,
                visionCapable = false
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    displayName = "New Config", 
                    localModelId = 0,
                    maxTokens = 4096,
                    contextWindow = 4096,
                    temperature = 0.7,
                    topP = 0.9,
                    topK = 40,
                    repetitionPenalty = 1.0,
                    systemPrompt = ""
                )
            )
        )
        
        repository.setRegisteredModel(ModelType.FAST, newAsset, ModelStatus.CURRENT, markExistingAsOld = true)
        
        // Verify old model is now OLD
        val oldModelEntity = database.localModelsDao().getBySha256("old-sha")
        assertNotNull(oldModelEntity)
        assertTrue(oldModelEntity!!.modelStatus == ModelStatus.OLD)
    }

    @Test
    fun `clearOld deletes OLD models from database`() = runTest {
        val oldAsset = LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "old/model",
                remoteFileName = "old.bin",
                localFileName = "old.bin",
                sha256 = "old-sha",
                sizeInBytes = 1024L,
                modelFileFormat = ModelFileFormat.GGUF,
                visionCapable = false
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    displayName = "Old", 
                    localModelId = 0,
                    maxTokens = 4096,
                    contextWindow = 4096,
                    temperature = 0.7,
                    topP = 0.9,
                    topK = 40,
                    repetitionPenalty = 1.0,
                    systemPrompt = ""
                )
            )
        )
        // 1. Register as CURRENT
        repository.setRegisteredModel(ModelType.FAST, oldAsset, ModelStatus.CURRENT)
        
        // 2. Replace with new asset, demoting old to OLD
        val newAsset = LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "new/model",
                remoteFileName = "new.bin",
                localFileName = "new.bin",
                sha256 = "new-sha",
                sizeInBytes = 1024L,
                modelFileFormat = ModelFileFormat.GGUF,
                visionCapable = false
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    displayName = "New", 
                    localModelId = 0,
                    maxTokens = 4096,
                    contextWindow = 4096,
                    temperature = 0.7,
                    topP = 0.9,
                    topK = 40,
                    repetitionPenalty = 1.0,
                    systemPrompt = ""
                )
            )
        )
        repository.setRegisteredModel(ModelType.FAST, newAsset, ModelStatus.CURRENT, markExistingAsOld = true)
        
        // Verify old exists as OLD
        assertNotNull(database.localModelsDao().getBySha256("old-sha"))
        assertTrue(database.localModelsDao().getBySha256("old-sha")!!.modelStatus == ModelStatus.OLD)
        
        // 3. Clear old
        repository.clearOld()
        
        // 4. Verify deleted
        val deletedModel = database.localModelsDao().getBySha256("old-sha")
        assertTrue(deletedModel == null)
    }
}
