package com.browntowndev.enclave.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LocalModelRepositoryImplTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var repository: LocalModelRepositoryImpl
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private lateinit var readRepairApplier: SensitiveRoomReadRepairApplier

    private val fakeTransactionProvider =
        object : TransactionProvider {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }

    @BeforeEach
    fun setup() {
        syncTrigger = RecordingCloudSyncTrigger()
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        readRepairApplier = database.realReadRepairApplier()

        repository =
            LocalModelRepositoryImpl(
                syncTrigger = syncTrigger,
                modelsDao = database.localModelsDao(),
                configsDao = database.localModelConfigurationsDao(),
                defaultModelsDao = database.defaultModelsDao(),
                transactionProvider = fakeTransactionProvider,
                healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                readRepairApplier = readRepairApplier,
            )
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `SCN-REPO-001 local model repository preserves displayName`() =
        runTest {
            val metadata =
                LocalModelMetadata(
                    id = LocalModelId(""),
                    displayName = "MiniCPM-V-4.6 1.3B",
                    huggingFaceModelName = "openbmb/MiniCPM-V-4.6-Thinking-gguf",
                    remoteFileName = "minicpm.gguf",
                    localFileName = "minicpm.gguf",
                    sha256 = "minicpm-sha",
                    sizeInBytes = 1000,
                    modelFileFormat = ModelFileFormat.GGUF,
                )
            val asset = LocalModelAsset(metadata = metadata, configurations = emptyList())

            val assetId = repository.upsertLocalAsset(asset)
            val loaded = repository.getAssetById(assetId)

            assertEquals("MiniCPM-V-4.6 1.3B", loaded?.metadata?.displayName)
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
                    healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                    readRepairApplier = readRepairApplier,
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

            assertEquals("PCENC:v1:aes-gcm:Private Config", stored?.displayName)
            assertEquals("PCENC:v1:aes-gcm:private system prompt", stored?.systemPrompt)
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
