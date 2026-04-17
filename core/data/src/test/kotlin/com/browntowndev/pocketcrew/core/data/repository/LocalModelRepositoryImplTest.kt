package com.browntowndev.pocketcrew.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalModelRepositoryImplTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var repository: LocalModelRepositoryImpl

    private val fakeTransactionProvider = object : TransactionProvider {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    private val fakeLogger = object : LoggingPort {
        override fun info(tag: String, message: String) {}
        override fun debug(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
        override fun recordException(tag: String, message: String, throwable: Throwable) {}
    }

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = LocalModelRepositoryImpl(
            database.localModelsDao(),
            database.localModelConfigurationsDao(),
            database.defaultModelsDao(),
            fakeTransactionProvider,
            fakeLogger
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `upsertLocalAsset stores and retrieves asset with isMultimodal`() = runTest {
        val metadata = LocalModelMetadata(
            id = LocalModelId(""),
            huggingFaceModelName = "vision-model",
            remoteFileName = "model.gguf",
            localFileName = "model.gguf",
            sha256 = "test-sha256",
            sizeInBytes = 1000,
            modelFileFormat = ModelFileFormat.GGUF,
            isMultimodal = true
        )
        val config = LocalModelConfiguration(
            id = LocalModelConfigurationId("config-1"),
            localModelId = LocalModelId(""),
            displayName = "Vision Config",
            maxTokens = 100,
            contextWindow = 100,
            temperature = 0.7,
            topP = 0.9,
            topK = 40,
            repetitionPenalty = 1.1,
            systemPrompt = "sys",
            defaultAssignments = listOf(ModelType.FAST)
        )
        val asset = LocalModelAsset(metadata = metadata, configurations = listOf(config))

        val assetId = repository.upsertLocalAsset(asset)
        val configId = repository.upsertLocalConfiguration(config.copy(localModelId = assetId))

        val retrieved = repository.getAssetById(assetId)
        assertEquals(true, retrieved?.metadata?.isMultimodal)
        assertEquals("Vision Config", retrieved?.configurations?.firstOrNull()?.displayName)
    }

    @Test
    fun `upsertLocalAsset updates isMultimodal on re-upsert with same SHA`() = runTest {
        val sharedSha = "shared-sha256"

        // 1. Upsert a multimodal asset
        val metadata1 = LocalModelMetadata(
            id = LocalModelId(""),
            huggingFaceModelName = "vision-model",
            remoteFileName = "model.gguf",
            localFileName = "model.gguf",
            sha256 = sharedSha,
            sizeInBytes = 1000,
            modelFileFormat = ModelFileFormat.GGUF,
            isMultimodal = true
        )
        val config1 = LocalModelConfiguration(
            id = LocalModelConfigurationId("config-1"),
            localModelId = LocalModelId(""),
            displayName = "Vision Config",
            maxTokens = 100,
            contextWindow = 100,
            temperature = 0.7,
            topP = 0.9,
            topK = 40,
            repetitionPenalty = 1.1,
            systemPrompt = "sys"
        )
        val assetId = repository.upsertLocalAsset(LocalModelAsset(metadata1, listOf(config1)))
        repository.upsertLocalConfiguration(config1.copy(localModelId = assetId))

        assertEquals(true, repository.getAssetById(assetId)?.metadata?.isMultimodal)

        // 2. Re-upsert with same SHA but isMultimodal = false — latest value wins
        val metadata2 = LocalModelMetadata(
            id = LocalModelId(""),
            huggingFaceModelName = "non-vision-model",
            remoteFileName = "model.gguf",
            localFileName = "model.gguf",
            sha256 = sharedSha,
            sizeInBytes = 1000,
            modelFileFormat = ModelFileFormat.GGUF,
            isMultimodal = false
        )
        val config2 = LocalModelConfiguration(
            id = LocalModelConfigurationId("config-2"),
            localModelId = LocalModelId(""),
            displayName = "Non-Vision Config",
            maxTokens = 100,
            contextWindow = 100,
            temperature = 0.7,
            topP = 0.9,
            topK = 40,
            repetitionPenalty = 1.1,
            systemPrompt = "sys"
        )
        repository.upsertLocalAsset(LocalModelAsset(metadata2, listOf(config2)))

        // 3. Latest upsert wins — isMultimodal is now false
        assertEquals(false, repository.getAssetById(assetId)?.metadata?.isMultimodal)
    }
}
