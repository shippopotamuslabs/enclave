package com.browntowndev.pocketcrew.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.mockk
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
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)

    private val fakeTransactionProvider =
        object : TransactionProvider {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }

    @Before
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PocketCrewDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        repository =
            LocalModelRepositoryImpl(
                syncTrigger = syncTrigger,
                modelsDao = database.localModelsDao(),
                configsDao = database.localModelConfigurationsDao(),
                defaultModelsDao = database.defaultModelsDao(),
                transactionProvider = fakeTransactionProvider,
                localEncryption = FakeLocalDataEncryptionService(),
            )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `upsertLocalAsset stores and retrieves asset with isMultimodal`() =
        runTest {
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId(""),
                    huggingFaceModelName = "vision-model",
                    remoteFileName = "model.gguf",
                    localFileName = "model.gguf",
                    sha256 = "test-sha256",
                    sizeInBytes = 1000,
                    modelFileFormat = ModelFileFormat.GGUF,
                    isMultimodal = true,
                )
            val config =
                LocalModelConfiguration(
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
                    defaultAssignments = listOf(ModelType.FAST),
                )
            val asset = LocalModelAsset(metadata = metadata, configurations = listOf(config))

            val assetId = repository.upsertLocalAsset(asset)
            val configId = repository.upsertLocalConfiguration(config.copy(localModelId = assetId))

            val retrieved = repository.getAssetById(assetId)
            assertEquals(true, retrieved?.metadata?.isMultimodal)
            assertEquals("Vision Config", retrieved?.configurations?.firstOrNull()?.displayName)
        }

    @Test
    fun `upsertLocalConfiguration stores sensitive fields encrypted and reads them decrypted`() =
        runTest {
            val encryptedRepository =
                LocalModelRepositoryImpl(
                    syncTrigger = syncTrigger,
                    modelsDao = database.localModelsDao(),
                    configsDao = database.localModelConfigurationsDao(),
                    defaultModelsDao = database.defaultModelsDao(),
                    transactionProvider = fakeTransactionProvider,
                    localEncryption = FakeLocalDataEncryptionService(),
                )
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId(""),
                    huggingFaceModelName = "model",
                    remoteFileName = "model.gguf",
                    localFileName = "model.gguf",
                    sha256 = "encrypted-config-sha",
                    sizeInBytes = 1000,
                    modelFileFormat = ModelFileFormat.GGUF,
                )
            val assetId = encryptedRepository.upsertLocalAsset(LocalModelAsset(metadata = metadata, configurations = emptyList()))
            val config =
                LocalModelConfiguration(
                    id = LocalModelConfigurationId("config-encrypted"),
                    localModelId = assetId,
                    displayName = "Private Config",
                    maxTokens = 100,
                    contextWindow = 100,
                    temperature = 0.7,
                    topP = 0.9,
                    topK = 40,
                    repetitionPenalty = 1.1,
                    systemPrompt = "private system prompt",
                )

            encryptedRepository.upsertLocalConfiguration(config)
            val stored = database.localModelConfigurationsDao().getById(LocalModelConfigurationId("config-encrypted"))
            val loaded = encryptedRepository.getConfigurationById(LocalModelConfigurationId("config-encrypted"))

            assertEquals("ENC:v1:Private Config", stored?.displayName)
            assertEquals("ENC:v1:private system prompt", stored?.systemPrompt)
            assertEquals("Private Config", loaded?.displayName)
            assertEquals("private system prompt", loaded?.systemPrompt)
        }

    @Test
    fun `upsertLocalAsset updates isMultimodal on re-upsert with same SHA`() =
        runTest {
            val sharedSha = "shared-sha256"

            // 1. Upsert a multimodal asset
            val metadata1 =
                LocalModelMetadata(
                    id = LocalModelId(""),
                    huggingFaceModelName = "vision-model",
                    remoteFileName = "model.gguf",
                    localFileName = "model.gguf",
                    sha256 = sharedSha,
                    sizeInBytes = 1000,
                    modelFileFormat = ModelFileFormat.GGUF,
                    isMultimodal = true,
                )
            val config1 =
                LocalModelConfiguration(
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
                )
            val assetId = repository.upsertLocalAsset(LocalModelAsset(metadata1, listOf(config1)))
            repository.upsertLocalConfiguration(config1.copy(localModelId = assetId))

            assertEquals(true, repository.getAssetById(assetId)?.metadata?.isMultimodal)

            // 2. Re-upsert with same SHA but isMultimodal = false — latest value wins
            val metadata2 =
                LocalModelMetadata(
                    id = LocalModelId(""),
                    huggingFaceModelName = "non-vision-model",
                    remoteFileName = "model.gguf",
                    localFileName = "model.gguf",
                    sha256 = sharedSha,
                    sizeInBytes = 1000,
                    modelFileFormat = ModelFileFormat.GGUF,
                    isMultimodal = false,
                )
            val config2 =
                LocalModelConfiguration(
                    id = LocalModelConfigurationId("config-2"),
                    localModelId = LocalModelId(""),
                    displayName = "Non-Vision Config",
                    maxTokens = 100,
                    contextWindow = 100,
                    temperature = 0.7,
                    topP = 0.9,
                    topK = 40,
                    repetitionPenalty = 1.1,
                    systemPrompt = "sys",
                )
            repository.upsertLocalAsset(LocalModelAsset(metadata2, listOf(config2)))

            // 3. Latest upsert wins — isMultimodal is now false
            assertEquals(false, repository.getAssetById(assetId)?.metadata?.isMultimodal)
        }
}
