package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiCredentialsEntity
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.DefaultModelEntity
import com.browntowndev.enclave.core.data.local.DefaultModelsDao
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelEntity
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MediaProviderEntity
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.TtsProviderEntity
import com.browntowndev.enclave.core.data.security.CryptoAtRestEncryptionService
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class DefaultModelRepositoryImplTest {
    private lateinit var repo: DefaultModelRepositoryImpl
    private lateinit var database: EnclaveDatabase
    private lateinit var defaultModelsDao: DefaultModelsDao
    private lateinit var localConfigsDao: LocalModelConfigurationsDao
    private lateinit var localModelsDao: LocalModelsDao
    private lateinit var apiConfigsDao: ApiModelConfigurationsDao
    private lateinit var apiCredentialsDao: ApiCredentialsDao
    private lateinit var ttsProviderDao: TtsProviderDao
    private lateinit var mediaProviderDao: MediaProviderDao
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private val localEncryption = FakeLocalDataEncryptionService()

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        defaultModelsDao = database.defaultModelsDao()
        localConfigsDao = database.localModelConfigurationsDao()
        localModelsDao = database.localModelsDao()
        apiConfigsDao = database.apiModelConfigurationsDao()
        apiCredentialsDao = database.apiCredentialsDao()
        ttsProviderDao = database.ttsProviderDao()
        mediaProviderDao = database.mediaProviderDao()
        syncTrigger = RecordingCloudSyncTrigger()
        repo = buildRepository(localEncryption)
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    private fun buildRepository(encryption: FakeLocalDataEncryptionService) =
        DefaultModelRepositoryImpl(
            syncTrigger = syncTrigger,
            defaultModelsDao = defaultModelsDao,
            localModelConfigurationsDao = localConfigsDao,
            localModelsDao = localModelsDao,
            apiModelConfigurationsDao = apiConfigsDao,
            apiCredentialsDao = apiCredentialsDao,
            ttsProviderDao = ttsProviderDao,
            mediaProviderDao = mediaProviderDao,
            localEncryption = encryption,
        )

    private suspend fun seedLocalModel(
        modelId: LocalModelId,
        displayName: String,
        huggingFaceModelName: String = "bartowski/ai21labs_AI21-Jamba-Reasoning-3B-GGUF",
    ) {
        localModelsDao.upsert(
            LocalModelEntity(
                id = modelId,
                modelFileFormat = ModelFileFormat.GGUF,
                displayName = displayName,
                huggingFaceModelName = huggingFaceModelName,
                remoteFilename = "qwen.gguf",
                localFilename = "qwen.gguf",
                sha256 = "abc",
                sizeInBytes = 100,
            ),
        )
    }

    private suspend fun seedLocalConfig(
        configId: LocalModelConfigurationId,
        modelId: LocalModelId,
        storedDisplayName: String,
    ) {
        localConfigsDao.upsert(
            LocalModelConfigurationEntity(
                id = configId,
                localModelId = modelId,
                displayName = storedDisplayName,
            ),
        )
    }

    private suspend fun seedApiCredentials(
        credId: ApiCredentialsId,
        storedDisplayName: String,
        provider: ApiProvider = ApiProvider.OPENAI,
        alias: String = "key1",
    ) {
        apiCredentialsDao.insert(
            ApiCredentialsEntity(
                id = credId,
                displayName = storedDisplayName,
                provider = provider,
                credentialAlias = alias,
            ),
        )
    }

    private suspend fun seedApiConfig(
        configId: ApiModelConfigurationId,
        credId: ApiCredentialsId,
        modelId: String,
        storedDisplayName: String,
    ) {
        apiConfigsDao.upsert(
            ApiModelConfigurationEntity(
                id = configId,
                apiCredentialsId = credId,
                modelId = modelId,
                displayName = storedDisplayName,
            ),
        )
    }

    private suspend fun seedTtsProvider(entity: TtsProviderEntity) {
        ttsProviderDao.upsertTtsProvider(entity)
    }

    private suspend fun seedMediaProvider(entity: MediaProviderEntity) {
        mediaProviderDao.upsert(entity)
    }

    private suspend fun seedDefault(entity: DefaultModelEntity) {
        defaultModelsDao.upsert(entity)
    }

    @Test
    fun `SCN-REPO-DEFAULT-001 getDefault uses asset displayName for local assignment`() =
        runTest {
            val configId = LocalModelConfigurationId("test-local-config-1")
            val modelId = LocalModelId("10")
            seedLocalModel(modelId, displayName = "Jamba Reasoning 3B")
            seedLocalConfig(configId, modelId, storedDisplayName = localEncryption.encryptString("Synthesis"))
            seedDefault(
                DefaultModelEntity(
                    modelType = ModelType.MAIN,
                    localConfigId = configId,
                    apiConfigId = null,
                    ttsProviderId = null,
                    mediaProviderId = null,
                    updatedAt = 0L,
                    pendingSync = 0,
                ),
            )

            val result = repo.getDefault(ModelType.MAIN)

            assertEquals(configId, result?.localConfigId)
            assertNull(result?.apiConfigId)
            assertEquals("Jamba Reasoning 3B", result?.displayName)
            assertEquals("Local", result?.providerName)
            assertEquals("Synthesis", result?.presetName)
        }

    @Test
    fun `getDefault falls back to hugging face name when asset display name is blank`() =
        runTest {
            val configId = LocalModelConfigurationId("test-local-config-1")
            val modelId = LocalModelId("10")
            val huggingFaceName = "Jackrong/Qwen3.5-2B-Claude-4.6-Opus-Reasoning-Distilled-GGUF"
            seedLocalModel(modelId, displayName = "", huggingFaceModelName = huggingFaceName)
            seedLocalConfig(configId, modelId, storedDisplayName = localEncryption.encryptString("Qwen 3.5 2B"))
            seedDefault(
                DefaultModelEntity(
                    modelType = ModelType.DRAFT_TWO,
                    localConfigId = configId,
                    apiConfigId = null,
                ),
            )

            val result = repo.getDefault(ModelType.DRAFT_TWO)

            assertEquals(huggingFaceName, result?.displayName)
        }

    @Test
    fun `get default with resolved display data for API model`() =
        runTest {
            val configId = ApiModelConfigurationId("test-api-config-1")
            val credId = ApiCredentialsId("12")
            seedApiCredentials(credId, storedDisplayName = localEncryption.encryptString("OpenAI Primary"))
            seedApiConfig(configId, credId, modelId = "gpt-4o", storedDisplayName = localEncryption.encryptString("Default"))
            seedDefault(
                DefaultModelEntity(
                    modelType = ModelType.VISION,
                    localConfigId = null,
                    apiConfigId = configId,
                    ttsProviderId = null,
                    mediaProviderId = null,
                    updatedAt = 0L,
                    pendingSync = 0,
                ),
            )

            val result = repo.getDefault(ModelType.VISION)

            assertEquals(configId, result?.apiConfigId)
            assertNull(result?.localConfigId)
            assertEquals("OpenAI Primary", result?.displayName)
            assertEquals("OpenAI", result?.providerName)
            assertEquals("Default", result?.presetName)
            assertEquals("gpt-4o", result?.modelId)
        }

    @Test
    fun `get default with resolved display data for TTS model`() =
        runTest {
            val ttsId = TtsProviderId("tts-1")
            seedTtsProvider(
                TtsProviderEntity(
                    id = ttsId.value,
                    displayName = localEncryption.encryptString("My Grok TTS"),
                    provider = ApiProvider.XAI,
                    voiceName = localEncryption.encryptString("eve"),
                    credentialAlias = "xai-key",
                ),
            )
            seedDefault(
                DefaultModelEntity(
                    modelType = ModelType.TTS,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = ttsId,
                    mediaProviderId = null,
                    updatedAt = 0L,
                    pendingSync = 0,
                ),
            )

            val result = repo.getDefault(ModelType.TTS)

            assertEquals(ttsId, result?.ttsProviderId)
            assertEquals("My Grok TTS", result?.displayName)
            assertEquals("xAI", result?.providerName)
            assertEquals("eve", result?.presetName)
        }

    @Test
    fun `get default with resolved display data for media model`() =
        runTest {
            val mediaId = MediaProviderId("media-1")
            seedMediaProvider(
                MediaProviderEntity(
                    id = mediaId.value,
                    displayName = localEncryption.encryptString("Imagen"),
                    provider = ApiProvider.GOOGLE,
                    capability = MediaCapability.IMAGE,
                    modelName = localEncryption.encryptString("imagen-3"),
                    baseUrl = localEncryption.encryptString("https://api.google.com"),
                    credentialAlias = "google-key",
                ),
            )
            seedDefault(
                DefaultModelEntity(
                    modelType = ModelType.IMAGE_GENERATION,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = null,
                    mediaProviderId = mediaId,
                    updatedAt = 0L,
                    pendingSync = 0,
                ),
            )

            val result = repo.getDefault(ModelType.IMAGE_GENERATION)

            assertEquals(mediaId, result?.mediaProviderId)
            assertEquals("Imagen", result?.displayName)
            assertEquals("Google", result?.providerName)
            assertEquals("imagen-3", result?.presetName)
        }

    @Test
    fun `SCN-REPO-DEFAULT-002 observeDefaults uses asset displayName from SQL view`() =
        runTest {
            val configId = LocalModelConfigurationId("cfg-thinking")
            val modelId = LocalModelId("model-thinking")
            seedLocalModel(modelId, displayName = "Gemma 4 E4B")
            seedLocalConfig(configId, modelId, storedDisplayName = localEncryption.encryptString("Thinking"))
            seedDefault(
                DefaultModelEntity(
                    modelType = ModelType.THINKING,
                    localConfigId = configId,
                    apiConfigId = null,
                ),
            )

            repo.observeDefaults().test {
                val thinking = awaitItem().single { it.modelType == ModelType.THINKING }
                assertEquals("Gemma 4 E4B", thinking.displayName)
                assertEquals("Thinking", thinking.presetName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeDefaults decrypts assignment view labels from Room`() =
        runTest {
            // MAIN (local)
            val mainModelId = LocalModelId("model-main")
            val mainConfigId = LocalModelConfigurationId("local-cfg")
            seedLocalModel(mainModelId, displayName = "Gemma 4 E4B")
            seedLocalConfig(mainConfigId, mainModelId, storedDisplayName = localEncryption.encryptString("Precise"))
            seedDefault(DefaultModelEntity(modelType = ModelType.MAIN, localConfigId = mainConfigId, apiConfigId = null))

            // VISION (api)
            val visionCredId = ApiCredentialsId("12")
            val visionConfigId = ApiModelConfigurationId("api-cfg")
            seedApiCredentials(visionCredId, storedDisplayName = localEncryption.encryptString("OpenAI Primary"))
            seedApiConfig(
                visionConfigId,
                visionCredId,
                modelId = "grok-4-3",
                storedDisplayName = localEncryption.encryptString("Default"),
            )
            seedDefault(DefaultModelEntity(modelType = ModelType.VISION, localConfigId = null, apiConfigId = visionConfigId))

            // TTS
            val ttsId = TtsProviderId("tts-1")
            seedTtsProvider(
                TtsProviderEntity(
                    id = ttsId.value,
                    displayName = localEncryption.encryptString("My Grok TTS"),
                    provider = ApiProvider.XAI,
                    voiceName = localEncryption.encryptString("eve"),
                    credentialAlias = "xai-key",
                ),
            )
            seedDefault(
                DefaultModelEntity(
                    modelType = ModelType.TTS,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = ttsId,
                ),
            )

            // IMAGE_GENERATION (media)
            val mediaId = MediaProviderId("media-1")
            seedMediaProvider(
                MediaProviderEntity(
                    id = mediaId.value,
                    displayName = localEncryption.encryptString("Imagen"),
                    provider = ApiProvider.GOOGLE,
                    capability = MediaCapability.IMAGE,
                    modelName = localEncryption.encryptString("imagen-3"),
                    baseUrl = localEncryption.encryptString("https://api.google.com"),
                    credentialAlias = "google-key",
                ),
            )
            seedDefault(
                DefaultModelEntity(
                    modelType = ModelType.IMAGE_GENERATION,
                    localConfigId = null,
                    apiConfigId = null,
                    mediaProviderId = mediaId,
                ),
            )

            repo.observeDefaults().test {
                val assignments = awaitItem().associateBy { it.modelType }

                val main = requireNotNull(assignments[ModelType.MAIN])
                assertEquals("Gemma 4 E4B", main.displayName)
                assertEquals("Precise", main.presetName)
                assertEquals("Local", main.providerName)

                val vision = requireNotNull(assignments[ModelType.VISION])
                assertEquals("OpenAI Primary", vision.displayName)
                assertEquals("Default", vision.presetName)
                assertEquals("grok-4-3", vision.modelId)
                assertEquals("OpenAI", vision.providerName)

                val tts = requireNotNull(assignments[ModelType.TTS])
                assertEquals("My Grok TTS", tts.displayName)
                assertEquals("eve", tts.presetName)
                assertEquals("xAI", tts.providerName)

                val media = requireNotNull(assignments[ModelType.IMAGE_GENERATION])
                assertEquals("Imagen", media.displayName)
                assertEquals("imagen-3", media.presetName)
                assertEquals("Google", media.providerName)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeDefaults survives decrypt failure without emitting crash`() =
        runTest {
            val credId = ApiCredentialsId("12")
            val configId = ApiModelConfigurationId("api-cfg")
            seedApiCredentials(credId, storedDisplayName = "OpenAI Primary")
            seedApiConfig(
                configId,
                credId,
                modelId = "grok-vision",
                storedDisplayName = "${CryptoAtRestEncryptionService.ENVELOPE_PREFIX}bad",
            )
            seedDefault(DefaultModelEntity(modelType = ModelType.VISION, localConfigId = null, apiConfigId = configId))

            val failingEncryption = FakeLocalDataEncryptionService()
            failingEncryption.forceDecryptFailure = true
            val failingRepo = buildRepository(failingEncryption)

            failingRepo.observeDefaults().test {
                val assignments = awaitItem()
                assertEquals(1, assignments.size)
                assertNull(assignments.first().presetName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getDefault survives decrypt failure for media provider assignment`() =
        runTest {
            val mediaId = MediaProviderId("media-1")
            seedMediaProvider(
                MediaProviderEntity(
                    id = mediaId.value,
                    displayName = "${CryptoAtRestEncryptionService.ENVELOPE_PREFIX}bad",
                    provider = ApiProvider.GOOGLE,
                    capability = MediaCapability.IMAGE,
                    modelName = "model",
                    baseUrl = "url",
                    credentialAlias = "alias",
                ),
            )
            seedDefault(
                DefaultModelEntity(
                    modelType = ModelType.IMAGE_GENERATION,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = null,
                    mediaProviderId = mediaId,
                    pendingSync = 0,
                ),
            )

            val failingEncryption = FakeLocalDataEncryptionService()
            failingEncryption.forceDecryptFailure = true
            val failingRepo = buildRepository(failingEncryption)

            val result = failingRepo.getDefault(ModelType.IMAGE_GENERATION)

            assertEquals(ModelType.IMAGE_GENERATION, result?.modelType)
            assertNull(result?.displayName)
            assertNull(result?.presetName)
        }

    @Test
    fun `setDefault calls triggerSync once`() =
        runTest {
            val configId = LocalModelConfigurationId("cfg-1")
            val modelId = LocalModelId("model-1")
            seedLocalModel(modelId, displayName = "Model")
            seedLocalConfig(configId, modelId, storedDisplayName = "Config")

            repo.setDefault(ModelType.MAIN, localConfigId = configId)

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `clearDefault calls triggerSync once`() =
        runTest {
            repo.clearDefault(ModelType.FAST)

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `setDefault upserts entity with pendingSync true`() =
        runTest {
            val credId = ApiCredentialsId("9")
            val configId = ApiModelConfigurationId("api-cfg-1")
            seedApiCredentials(credId, storedDisplayName = "Creds")
            seedApiConfig(configId, credId, modelId = "gpt-4o", storedDisplayName = "Cfg")

            repo.setDefault(ModelType.VISION, apiConfigId = configId)

            assertEquals(1, defaultModelsDao.getDefault(ModelType.VISION)?.pendingSync)
        }
}
