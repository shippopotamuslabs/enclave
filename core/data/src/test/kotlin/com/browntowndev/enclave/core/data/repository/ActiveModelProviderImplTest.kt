package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiCredentialsEntity
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelEntity
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MediaProviderEntity
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.TtsProviderEntity
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ActiveModelProviderImplTest {
    private lateinit var database: EnclaveDatabase
    private val defaultModelRepository = FakeDefaultModelRepository()
    private lateinit var localModelsDao: LocalModelsDao
    private lateinit var localConfigsDao: LocalModelConfigurationsDao
    private lateinit var apiCredentialsDao: ApiCredentialsDao
    private lateinit var apiConfigsDao: ApiModelConfigurationsDao
    private lateinit var mediaProviderDao: MediaProviderDao
    private lateinit var ttsProviderDao: TtsProviderDao
    private val localEncryption = FakeLocalDataEncryptionService()

    private lateinit var repository: ActiveModelProviderImpl

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        localModelsDao = database.localModelsDao()
        localConfigsDao = database.localModelConfigurationsDao()
        apiCredentialsDao = database.apiCredentialsDao()
        apiConfigsDao = database.apiModelConfigurationsDao()
        mediaProviderDao = database.mediaProviderDao()
        ttsProviderDao = database.ttsProviderDao()
        repository =
            ActiveModelProviderImpl(
                defaultModelRepository = defaultModelRepository,
                localModelsDao = localModelsDao,
                localConfigsDao = localConfigsDao,
                apiCredentialsDao = apiCredentialsDao,
                apiConfigsDao = apiConfigsDao,
                mediaProviderDao = mediaProviderDao,
                ttsProviderDao = ttsProviderDao,
                localEncryption = localEncryption,
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `SCN-ACTIVE-001 active model provider uses asset displayName for local config`() =
        runTest {
            val configId = LocalModelConfigurationId("cfg-fast")
            val modelId = LocalModelId("model-1")
            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.FAST,
                    localConfigId = configId,
                ),
            )
            localModelsDao.upsert(
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.LITERTLM,
                    displayName = "Gemma 4 E4B",
                    huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm",
                    remoteFilename = "gemma-4-E4B-it.litertlm",
                    localFilename = "gemma-4-E4B-it.litertlm",
                    sha256 = "gemma-sha",
                    sizeInBytes = 42L,
                ),
            )
            localConfigsDao.upsert(
                LocalModelConfigurationEntity(
                    id = configId,
                    localModelId = modelId,
                    displayName = localEncryption.encryptString("Fast"),
                    reasoningBudget = 1536,
                ),
            )

            val result = repository.getActiveConfiguration(ModelType.FAST)

            assertEquals("Gemma 4 E4B", result?.name)
            assertEquals("gemma-4-E4B-it.litertlm", result?.modelId)
            assertEquals(1536, result?.reasoningBudget)
        }

    @Test
    fun `SCN-ACTIVE-001 Active Provider Model From Preset`() =
        runTest {
            val result = activeApiConfigurationFromPreset()

            assertEquals("gpt-4o", result.modelId)
            assertEquals(ApiProvider.OPENAI, result.apiProvider)
        }

    @Test
    fun `SCN-ACTIVE-002 Active Provider Multimodal From Preset`() =
        runTest {
            val result = activeApiConfigurationFromPreset()

            assertEquals(true, result.isMultimodal)
        }

    @Test
    fun `SCN-MUT-002 Active Provider Must Not Read Credential Model`() =
        runTest {
            val result = activeApiConfigurationFromPreset(modelType = ModelType.MAIN)

            assertEquals("gpt-4o", result.modelId)
            assertFalse(ApiCredentialsEntity::class.members.any { it.name == "modelId" })
        }

    @Test
    fun `SCN-MUT-002 source ActiveModelProviderImpl does not read credentials modelId`() {
        val sourcePath =
            Paths.get(
                "src/main/kotlin/com/browntowndev/enclave/core/data/repository/ActiveModelProviderImpl.kt",
            )
        val source = Files.readString(sourcePath)
        assertFalse(source.contains("credentials.modelId"))
    }

    private suspend fun activeApiConfigurationFromPreset(modelType: ModelType = ModelType.VISION): ActiveModelConfiguration {
        val configId = ApiModelConfigurationId("cfg-9")
        val credentialsId = ApiCredentialsId("creds-1")
        defaultModelRepository.seed(
            DefaultModelAssignment(
                modelType = modelType,
                apiConfigId = configId,
            ),
        )
        apiCredentialsDao.insert(
            ApiCredentialsEntity(
                id = credentialsId,
                displayName = localEncryption.encryptString("OpenAI Primary"),
                provider = ApiProvider.OPENAI,
                baseUrl = localEncryption.encryptString("https://api.openai.com/v1"),
                credentialAlias = "openai-key",
            ),
        )
        apiConfigsDao.upsert(
            ApiModelConfigurationEntity(
                id = configId,
                apiCredentialsId = credentialsId,
                displayName = localEncryption.encryptString("Default"),
                modelId = "gpt-4o",
                isMultimodal = true,
                systemPrompt = localEncryption.encryptString("Be helpful"),
            ),
        )

        return requireNotNull(repository.getActiveConfiguration(modelType))
    }

    @Test
    fun `getActiveConfiguration returns null when no default assignment`() =
        runTest {
            val result = repository.getActiveConfiguration(ModelType.MAIN)

            assertNull(result)
        }

    @Test
    fun `getActiveConfiguration returns null system prompt when decrypt fails`() =
        runTest {
            localEncryption.forceDecryptFailure = true
            val configId = LocalModelConfigurationId("local-config-1")
            val modelId = LocalModelId("model-1")
            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.MAIN,
                    localConfigId = configId,
                ),
            )
            localModelsDao.upsert(
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.GGUF,
                    displayName = "Gemma 4 E4B",
                    huggingFaceModelName = "qwen",
                    remoteFilename = "qwen.gguf",
                    localFilename = "qwen.gguf",
                    sha256 = "abc123",
                    sizeInBytes = 42L,
                ),
            )
            localConfigsDao.upsert(
                LocalModelConfigurationEntity(
                    id = configId,
                    localModelId = modelId,
                    displayName = localEncryption.encryptString("Balanced"),
                    systemPrompt = localEncryption.encryptString("Use brief answers"),
                ),
            )

            val result = repository.getActiveConfiguration(ModelType.MAIN)

            assertEquals("Gemma 4 E4B", result?.name)
            assertNull(result?.systemPrompt)
        }

    @Test
    @DisplayName("S04 — getActiveConfigurationByOverride resolves local config by explicit id")
    fun s04_getActiveConfigurationByOverrideLocal() =
        runTest {
            val override =
                ChatModelOverride.Local(
                    configId = LocalModelConfigurationId("local-qwen-preset"),
                    displayName = "Qwen 3 8B Fast",
                )

            val result = repository.getActiveConfigurationByOverride(override)

            // TDD Red: should return config with name "Qwen 3 8B Fast"
            assertEquals("Qwen 3 8B Fast", result?.name)
        }

    @Test
    @DisplayName("S05 — getActiveConfigurationByOverride resolves api config by explicit id")
    fun s05_getActiveConfigurationByOverrideApi() =
        runTest {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-grok-beta"),
                    displayName = "Grok Beta",
                )

            val result = repository.getActiveConfigurationByOverride(override)

            // TDD Red: should return config with name "Grok Beta"
            assertEquals("Grok Beta", result?.name)
        }

    @Test
    @DisplayName("S06 — getActiveConfigurationByOverride does not read slot assignment")
    fun s06_getActiveConfigurationByOverrideDoesNotReadSlot() =
        runTest {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-grok-beta"),
                    displayName = "Grok Beta",
                )

            // Should NOT call defaultModelRepository.getDefault (slot lookup)
            val result = repository.getActiveConfigurationByOverride(override)

            // TDD Red: should resolve from explicit config id, not FAST slot
            assertEquals("Grok Beta", result?.name)
            assertTrue(defaultModelRepository.getDefaultCalls.isEmpty())
        }

    @Test
    @DisplayName("getActiveConfiguration returns non-local ActiveModelConfiguration for media default assignment")
    fun getActiveConfigurationMediaProvider() =
        runTest {
            val mediaProviderId = MediaProviderId("media-1")
            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.IMAGE_GENERATION,
                    mediaProviderId = mediaProviderId,
                ),
            )
            mediaProviderDao.upsert(
                MediaProviderEntity(
                    id = "media-1",
                    displayName = localEncryption.encryptString("DALL-E 3"),
                    provider = ApiProvider.OPENAI,
                    capability = MediaCapability.IMAGE,
                    modelName = localEncryption.encryptString("dall-e-3"),
                    credentialAlias = "openai-creds",
                ),
            )

            val result = repository.getActiveConfiguration(ModelType.IMAGE_GENERATION)

            assertEquals("DALL-E 3", result?.name)
            assertEquals("dall-e-3", result?.modelId)
            assertEquals(false, result?.isLocal)
            assertEquals(ApiProvider.OPENAI, result?.apiProvider)
        }

    @Test
    @DisplayName("getActiveConfiguration returns non-local ActiveModelConfiguration for TTS default assignment")
    fun getActiveConfigurationTtsProvider() =
        runTest {
            val ttsProviderId = TtsProviderId("tts-1")
            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.TTS,
                    ttsProviderId = ttsProviderId,
                ),
            )
            ttsProviderDao.upsertTtsProvider(
                TtsProviderEntity(
                    id = "tts-1",
                    displayName = localEncryption.encryptString("TTS Voice"),
                    provider = ApiProvider.OPENAI,
                    voiceName = localEncryption.encryptString("alloy"),
                    credentialAlias = "openai-creds",
                ),
            )

            val result = repository.getActiveConfiguration(ModelType.TTS)

            assertEquals("TTS Voice", result?.name)
            assertEquals("alloy", result?.modelId)
            assertEquals(false, result?.isLocal)
            assertEquals(ApiProvider.OPENAI, result?.apiProvider)
        }
}
