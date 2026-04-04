package com.browntowndev.pocketcrew.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun createAsset(sha256: String, isVision: Boolean = false): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "test.bin",
                localFileName = "test.bin",
                sha256 = sha256,
                sizeInBytes = 1024L,
                modelFileFormat = ModelFileFormat.GGUF,
                visionCapable = isVision
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    displayName = "Test Config",
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
    }

    @Test
    fun `Granular Model Registration creates entities and resolves properly`() = runTest {
        val asset = createAsset("sha-123")
        
        val assetId = repository.upsertLocalAsset(asset)
        assertTrue(assetId > 0)
        
        val config = asset.configurations.first().copy(localModelId = assetId)
        val configId = repository.upsertLocalConfiguration(config)
        assertTrue(configId > 0)
        
        repository.setDefaultLocalConfig(ModelType.FAST, configId)
        
        val selection = repository.getRegisteredSelection(ModelType.FAST)
        assertNotNull(selection)
        assertEquals(ModelType.FAST, selection!!.modelType)
        assertEquals("sha-123", selection.asset.metadata.sha256)
        assertEquals("Test Config", selection.selectedConfig.displayName)
    }

    @Test
    fun `activateLocalModel wires config to persisted asset id`() = runTest {
        val asset = createAsset("sha-activate")

        val selection = repository.activateLocalModel(ModelType.FAST, asset)

        assertTrue(selection.asset.metadata.id > 0)
        assertEquals(selection.asset.metadata.id, selection.selectedConfig.localModelId)
        assertEquals("sha-activate", selection.asset.metadata.sha256)
    }

    @Test
    fun `Same SHA Update (Tuning-only) updates existing rows and pointer`() = runTest {
        val asset = createAsset("sha-update")
        val assetId = repository.upsertLocalAsset(asset)
        val configId1 = repository.upsertLocalConfiguration(asset.configurations.first().copy(localModelId = assetId))
        repository.setDefaultLocalConfig(ModelType.FAST, configId1)
        
        val updatedConfig = asset.configurations.first().copy(
            localModelId = assetId,
            temperature = 0.1, // Updated tuning
            displayName = "Updated Config"
        )
        
        val configId2 = repository.upsertLocalConfiguration(updatedConfig)
        repository.setDefaultLocalConfig(ModelType.FAST, configId2)
        
        val selection = repository.getRegisteredSelection(ModelType.FAST)
        assertNotNull(selection)
        assertEquals(0.1, selection!!.selectedConfig.temperature, 0.001)
        assertEquals("Updated Config", selection.selectedConfig.displayName)
    }

    @Test
    fun `Safe Replace upon Download Success replaces model without clearOld`() = runTest {
        val asset1 = createAsset("sha-old")
        val assetId1 = repository.upsertLocalAsset(asset1)
        val configId1 = repository.upsertLocalConfiguration(asset1.configurations.first().copy(localModelId = assetId1))
        repository.setDefaultLocalConfig(ModelType.FAST, configId1)
        
        val asset2 = createAsset("sha-new")
        val assetId2 = repository.upsertLocalAsset(asset2)
        val configId2 = repository.upsertLocalConfiguration(asset2.configurations.first().copy(localModelId = assetId2))
        repository.setDefaultLocalConfig(ModelType.FAST, configId2)
        
        val selection = repository.getRegisteredSelection(ModelType.FAST)
        assertNotNull(selection)
        assertEquals("sha-new", selection!!.asset.metadata.sha256)
    }

    @Test
    fun `Re-download of a Soft-deleted Asset reuses entity and assigns new config`() = runTest {
        val asset = createAsset("sha-soft")
        val assetId = repository.upsertLocalAsset(asset)
        
        // Simulating soft-delete (model entity exists, but zero configs, zero defaults)
        
        val newConfig = asset.configurations.first().copy(localModelId = assetId, displayName = "Re-downloaded Config")
        val configId = repository.upsertLocalConfiguration(newConfig)
        repository.setDefaultLocalConfig(ModelType.FAST, configId)
        
        val selection = repository.getRegisteredSelection(ModelType.FAST)
        assertNotNull(selection)
        assertEquals("sha-soft", selection!!.asset.metadata.sha256)
        assertEquals("Re-downloaded Config", selection.selectedConfig.displayName)
    }

    @Test
    fun `Mutation Defense - getRegisteredSelection returns strictly resolved config not first in list`() = runTest {
        val sharedSha = "shared-sha"
        val sharedFile = "gemma-4-E4B-it.litertlm"

        val fastAsset = LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm",
                remoteFileName = sharedFile,
                localFileName = sharedFile,
                sha256 = sharedSha,
                sizeInBytes = 1024L,
                modelFileFormat = ModelFileFormat.LITERTLM,
                visionCapable = false
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    displayName = "Gemma 3 2B (Fast)",
                    localModelId = 0,
                    maxTokens = 4096,
                    contextWindow = 16384,
                    temperature = 0.8,
                    topP = 0.9,
                    topK = 50,
                    minP = 0.07,
                    repetitionPenalty = 1.05,
                    thinkingEnabled = false,
                    systemPrompt = "fast"
                )
            )
        )

        val thinkingConfig = LocalModelConfiguration(
            displayName = "Gemma 3 2B (Thinking)",
            localModelId = 0,
            maxTokens = 6144,
            contextWindow = 16384,
            temperature = 0.05,
            topP = 0.9,
            topK = 50,
            minP = 0.1,
            repetitionPenalty = 1.05,
            thinkingEnabled = true,
            systemPrompt = "thinking"
        )

        val assetId = repository.upsertLocalAsset(fastAsset)
        
        val fastConfigId = repository.upsertLocalConfiguration(fastAsset.configurations.first().copy(localModelId = assetId))
        val thinkingConfigId = repository.upsertLocalConfiguration(thinkingConfig.copy(localModelId = assetId))
        
        repository.setDefaultLocalConfig(ModelType.FAST, fastConfigId)
        repository.setDefaultLocalConfig(ModelType.THINKING, thinkingConfigId)
        
        val fastSelection = repository.getRegisteredSelection(ModelType.FAST)
        val thinkingSelection = repository.getRegisteredSelection(ModelType.THINKING)
        
        assertNotNull(fastSelection)
        assertNotNull(thinkingSelection)
        
        assertEquals(ModelType.FAST, fastSelection!!.modelType)
        assertEquals("Gemma 3 2B (Fast)", fastSelection.selectedConfig.displayName)
        assertEquals(false, fastSelection.selectedConfig.thinkingEnabled)
        
        assertEquals(ModelType.THINKING, thinkingSelection!!.modelType)
        assertEquals("Gemma 3 2B (Thinking)", thinkingSelection.selectedConfig.displayName)
        assertEquals(true, thinkingSelection.selectedConfig.thinkingEnabled)
    }

    @Test
    fun `deleteModel reassigns defaults and clears configs`() = runTest {
        val asset = createAsset("sha-delete")
        val assetId = repository.upsertLocalAsset(asset)
        val configId = repository.upsertLocalConfiguration(asset.configurations.first().copy(localModelId = assetId))
        repository.setDefaultLocalConfig(ModelType.FAST, configId)

        val replacementAsset = createAsset("sha-replacement")
        val replacementAssetId = repository.upsertLocalAsset(replacementAsset)
        val replacementConfigId = repository.upsertLocalConfiguration(
            replacementAsset.configurations.first().copy(localModelId = replacementAssetId)
        )

        val result = repository.deleteModel(assetId, replacementConfigId, null)

        assertTrue(result.isSuccess)
        assertEquals(replacementConfigId, database.defaultModelsDao().getDefault(ModelType.FAST)?.localConfigId)
        assertTrue(database.localModelConfigurationsDao().getAllForAsset(assetId).isEmpty())
        assertNotNull(database.localModelsDao().getById(assetId))
    }

    @Test
    fun `activateLocalModel keeps FAST and THINKING separate on fresh install`() = runTest {
        val sharedSha = "shared-fresh-sha"
        val sharedFile = "gemma-4-E4B-it.litertlm"
        val baseMetadata = LocalModelMetadata(
            huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm",
            remoteFileName = sharedFile,
            localFileName = sharedFile,
            sha256 = sharedSha,
            sizeInBytes = 1024L,
            modelFileFormat = ModelFileFormat.LITERTLM,
            visionCapable = false
        )
        val fastAsset = LocalModelAsset(
            metadata = baseMetadata,
            configurations = listOf(
                LocalModelConfiguration(
                    displayName = "Gemma 3 2B (Fast)",
                    localModelId = 0,
                    maxTokens = 4096,
                    contextWindow = 16384,
                    temperature = 0.8,
                    topP = 0.9,
                    topK = 50,
                    minP = 0.07,
                    repetitionPenalty = 1.05,
                    thinkingEnabled = false,
                    systemPrompt = "fast"
                )
            )
        )
        val thinkingAsset = LocalModelAsset(
            metadata = baseMetadata,
            configurations = listOf(
                LocalModelConfiguration(
                    displayName = "Gemma 3 2B (Thinking)",
                    localModelId = 0,
                    maxTokens = 6144,
                    contextWindow = 16384,
                    temperature = 0.05,
                    topP = 0.9,
                    topK = 50,
                    minP = 0.1,
                    repetitionPenalty = 1.05,
                    thinkingEnabled = true,
                    systemPrompt = "thinking"
                )
            )
        )

        repository.activateLocalModel(ModelType.FAST, fastAsset)
        repository.activateLocalModel(ModelType.THINKING, thinkingAsset)

        val fastSelection = repository.getRegisteredSelection(ModelType.FAST)
        val thinkingSelection = repository.getRegisteredSelection(ModelType.THINKING)

        assertNotNull(fastSelection)
        assertNotNull(thinkingSelection)
        assertEquals("Gemma 3 2B (Fast)", fastSelection!!.selectedConfig.displayName)
        assertEquals(false, fastSelection.selectedConfig.thinkingEnabled)
        assertEquals("fast", fastSelection.selectedConfig.systemPrompt)
        assertEquals("Gemma 3 2B (Thinking)", thinkingSelection!!.selectedConfig.displayName)
        assertEquals(true, thinkingSelection.selectedConfig.thinkingEnabled)
        assertEquals("thinking", thinkingSelection.selectedConfig.systemPrompt)
        assertTrue(fastSelection.selectedConfig.id != thinkingSelection.selectedConfig.id)
    }
}
