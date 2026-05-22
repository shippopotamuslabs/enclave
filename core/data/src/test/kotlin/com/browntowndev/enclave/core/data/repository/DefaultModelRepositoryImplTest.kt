package com.browntowndev.enclave.core.data.repository

import app.cash.turbine.test
import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiCredentialsEntity
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.DefaultModelAssignmentView
import com.browntowndev.enclave.core.data.local.DefaultModelEntity
import com.browntowndev.enclave.core.data.local.DefaultModelsDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelEntity
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MediaProviderEntity
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.TtsProviderEntity
import com.browntowndev.enclave.core.data.security.CryptoAtRestEncryptionService
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import javax.crypto.AEADBadTagException
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultModelRepositoryImplTest {
    private lateinit var repo: DefaultModelRepositoryImpl
    private val defaultModelsDao = mockk<DefaultModelsDao>(relaxed = true)
    private val localConfigsDao = mockk<LocalModelConfigurationsDao>()
    private val localModelsDao = mockk<LocalModelsDao>()
    private val apiConfigsDao = mockk<ApiModelConfigurationsDao>()
    private val apiCredentialsDao = mockk<ApiCredentialsDao>()
    private val ttsProviderDao = mockk<TtsProviderDao>()
    private val mediaProviderDao = mockk<MediaProviderDao>()
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val localEncryption = FakeLocalDataEncryptionService()

    @BeforeEach
    fun setup() {
        repo =
            DefaultModelRepositoryImpl(
                syncTrigger = syncTrigger,
                defaultModelsDao = defaultModelsDao,
                localModelConfigurationsDao = localConfigsDao,
                localModelsDao = localModelsDao,
                apiModelConfigurationsDao = apiConfigsDao,
                apiCredentialsDao = apiCredentialsDao,
                ttsProviderDao = ttsProviderDao,
                mediaProviderDao = mediaProviderDao,
                localEncryption = localEncryption,
            )
    }

    @Test
    fun `get default with resolved display data for local model`() =
        runTest {
            val configId = LocalModelConfigurationId("test-local-config-1")
            val modelId = LocalModelId("10")
            coEvery { defaultModelsDao.getDefault(ModelType.MAIN) } returns
                DefaultModelEntity(
                    modelType = ModelType.MAIN,
                    localConfigId = configId,
                    apiConfigId = null,
                    ttsProviderId = null,
                    mediaProviderId = null,
                    updatedAt = 0L,
                    pendingSync = 0,
                )
            coEvery { localConfigsDao.getById(configId) } returns
                LocalModelConfigurationEntity(
                    id = configId,
                    localModelId = modelId,
                    displayName = localEncryption.encryptString("Precise"),
                )
            coEvery { localModelsDao.getById(modelId) } returns
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.GGUF,
                    huggingFaceModelName = "qwen",
                    remoteFilename = "qwen.gguf",
                    localFilename = "qwen.gguf",
                    sha256 = "abc",
                    sizeInBytes = 100,
                )

            val result = repo.getDefault(ModelType.MAIN)

            assertEquals(configId, result?.localConfigId)
            assertNull(result?.apiConfigId)
            assertEquals("qwen", result?.displayName)
            assertEquals("Local", result?.providerName)
            assertEquals("Precise", result?.presetName)
        }

    @Test
    fun `get default with resolved display data for API model`() =
        runTest {
            val configId = ApiModelConfigurationId("test-api-config-1")
            val credId = ApiCredentialsId("12")
            coEvery { defaultModelsDao.getDefault(ModelType.VISION) } returns
                DefaultModelEntity(
                    modelType = ModelType.VISION,
                    localConfigId = null,
                    apiConfigId = configId,
                    ttsProviderId = null,
                    mediaProviderId = null,
                    updatedAt = 0L,
                    pendingSync = 0,
                )
            coEvery { apiConfigsDao.getById(configId) } returns
                ApiModelConfigurationEntity(
                    id = configId,
                    apiCredentialsId = credId,
                    displayName = localEncryption.encryptString("Default"),
                )
            coEvery { apiCredentialsDao.getById(credId) } returns
                ApiCredentialsEntity(
                    id = credId,
                    displayName = localEncryption.encryptString("OpenAI Primary"),
                    provider = ApiProvider.OPENAI,
                    modelId = "gpt-4o",
                    credentialAlias = "key1",
                    isMultimodal = false,
                )

            val result = repo.getDefault(ModelType.VISION)

            assertEquals(configId, result?.apiConfigId)
            assertNull(result?.localConfigId)
            assertEquals("OpenAI Primary", result?.displayName)
            assertEquals("OpenAI", result?.providerName)
            assertEquals("Default", result?.presetName)
        }

    @Test
    fun `get default with resolved display data for TTS model`() =
        runTest {
            val ttsId = TtsProviderId("tts-1")
            coEvery { defaultModelsDao.getDefault(ModelType.TTS) } returns
                DefaultModelEntity(
                    modelType = ModelType.TTS,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = ttsId,
                    mediaProviderId = null,
                    updatedAt = 0L,
                    pendingSync = 0,
                )
            coEvery { ttsProviderDao.getTtsProvider(ttsId.value) } returns
                TtsProviderEntity(
                    id = ttsId.value,
                    displayName = localEncryption.encryptString("My Grok TTS"),
                    provider = ApiProvider.XAI,
                    voiceName = localEncryption.encryptString("eve"),
                    credentialAlias = "xai-key",
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
            coEvery { defaultModelsDao.getDefault(ModelType.IMAGE_GENERATION) } returns
                DefaultModelEntity(
                    modelType = ModelType.IMAGE_GENERATION,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = null,
                    mediaProviderId = mediaId,
                    updatedAt = 0L,
                    pendingSync = 0,
                )
            coEvery { mediaProviderDao.getMediaProvider(mediaId.value) } returns
                MediaProviderEntity(
                    id = mediaId.value,
                    displayName = localEncryption.encryptString("Imagen"),
                    provider = ApiProvider.GOOGLE,
                    capability = MediaCapability.IMAGE,
                    modelName = localEncryption.encryptString("imagen-3"),
                    baseUrl = localEncryption.encryptString("https://api.google.com"),
                    credentialAlias = "google-key",
                )

            val result = repo.getDefault(ModelType.IMAGE_GENERATION)

            assertEquals(mediaId, result?.mediaProviderId)
            assertEquals("Imagen", result?.displayName)
            assertEquals("Google", result?.providerName)
            assertEquals("Image", result?.presetName)
        }

    @Test
    fun `observeDefaults decrypts assignment view labels from Room`() =
        runTest {
            every { defaultModelsDao.observeAllWithDetails() } returns
                flowOf(
                    listOf(
                        DefaultModelAssignmentView(
                            modelType = ModelType.MAIN,
                            localConfigId = LocalModelConfigurationId("local-cfg"),
                            apiConfigId = null,
                            localPresetName = localEncryption.encryptString("Precise"),
                            apiPresetName = null,
                            localAssetName = "qwen",
                            apiAssetName = null,
                            apiCredentialsId = null,
                            apiProviderName = null,
                            ttsProviderId = null,
                            ttsAssetName = null,
                            ttsProviderName = null,
                            ttsVoiceName = null,
                            mediaProviderId = null,
                            mediaAssetName = null,
                            mediaProviderName = null,
                            mediaCapability = null,
                        ),
                        DefaultModelAssignmentView(
                            modelType = ModelType.VISION,
                            localConfigId = null,
                            apiConfigId = ApiModelConfigurationId("api-cfg"),
                            localPresetName = null,
                            apiPresetName = localEncryption.encryptString("Default"),
                            localAssetName = null,
                            apiAssetName = localEncryption.encryptString("OpenAI Primary"),
                            apiCredentialsId = ApiCredentialsId("12"),
                            apiProviderName = ApiProvider.OPENAI,
                            ttsProviderId = null,
                            ttsAssetName = null,
                            ttsProviderName = null,
                            ttsVoiceName = null,
                            mediaProviderId = null,
                            mediaAssetName = null,
                            mediaProviderName = null,
                            mediaCapability = null,
                        ),
                        DefaultModelAssignmentView(
                            modelType = ModelType.TTS,
                            localConfigId = null,
                            apiConfigId = null,
                            localPresetName = null,
                            apiPresetName = null,
                            localAssetName = null,
                            apiAssetName = null,
                            apiCredentialsId = null,
                            apiProviderName = null,
                            ttsProviderId = TtsProviderId("tts-1"),
                            ttsAssetName = localEncryption.encryptString("My Grok TTS"),
                            ttsProviderName = ApiProvider.XAI,
                            ttsVoiceName = localEncryption.encryptString("eve"),
                            mediaProviderId = null,
                            mediaAssetName = null,
                            mediaProviderName = null,
                            mediaCapability = null,
                        ),
                        DefaultModelAssignmentView(
                            modelType = ModelType.IMAGE_GENERATION,
                            localConfigId = null,
                            apiConfigId = null,
                            localPresetName = null,
                            apiPresetName = null,
                            localAssetName = null,
                            apiAssetName = null,
                            apiCredentialsId = null,
                            apiProviderName = null,
                            ttsProviderId = null,
                            ttsAssetName = null,
                            ttsProviderName = null,
                            ttsVoiceName = null,
                            mediaProviderId = MediaProviderId("media-1"),
                            mediaAssetName = localEncryption.encryptString("Imagen"),
                            mediaProviderName = ApiProvider.GOOGLE,
                            mediaCapability = MediaCapability.IMAGE,
                        ),
                    ),
                )

            repo.observeDefaults().test {
                val assignments = awaitItem().associateBy { it.modelType }

                val main = requireNotNull(assignments[ModelType.MAIN])
                assertEquals("qwen", main.displayName)
                assertEquals("Precise", main.presetName)
                assertEquals("Local", main.providerName)

                val vision = requireNotNull(assignments[ModelType.VISION])
                assertEquals("OpenAI Primary", vision.displayName)
                assertEquals("Default", vision.presetName)
                assertEquals("OpenAI", vision.providerName)

                val tts = requireNotNull(assignments[ModelType.TTS])
                assertEquals("My Grok TTS", tts.displayName)
                assertEquals("eve", tts.presetName)
                assertEquals("xAI", tts.providerName)

                val media = requireNotNull(assignments[ModelType.IMAGE_GENERATION])
                assertEquals("Imagen", media.displayName)
                assertEquals("Image", media.presetName)
                assertEquals("Google", media.providerName)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeDefaults survives decrypt failure without emitting crash`() =
        runTest {
            val failingEncryption = mockk<LocalDataEncryptionService>()
            every { failingEncryption.isEncrypted(any()) } answers {
                firstArg<String>().startsWith(CryptoAtRestEncryptionService.ENVELOPE_PREFIX)
            }
            every { failingEncryption.decryptString(any(), any()) } throws AEADBadTagException()
            val failingRepo =
                DefaultModelRepositoryImpl(
                    syncTrigger = syncTrigger,
                    defaultModelsDao = defaultModelsDao,
                    localModelConfigurationsDao = localConfigsDao,
                    localModelsDao = localModelsDao,
                    apiModelConfigurationsDao = apiConfigsDao,
                    apiCredentialsDao = apiCredentialsDao,
                    ttsProviderDao = ttsProviderDao,
                    mediaProviderDao = mediaProviderDao,
                    localEncryption = failingEncryption,
                )
            every { defaultModelsDao.observeAllWithDetails() } returns
                flowOf(
                    listOf(
                        DefaultModelAssignmentView(
                            modelType = ModelType.VISION,
                            localConfigId = null,
                            apiConfigId = ApiModelConfigurationId("api-cfg"),
                            localPresetName = null,
                            apiPresetName = "${CryptoAtRestEncryptionService.ENVELOPE_PREFIX}bad",
                            localAssetName = null,
                            apiAssetName = null,
                            apiCredentialsId = ApiCredentialsId("12"),
                            apiProviderName = ApiProvider.OPENAI,
                            ttsProviderId = null,
                            ttsAssetName = null,
                            ttsProviderName = null,
                            ttsVoiceName = null,
                            mediaProviderId = null,
                            mediaAssetName = null,
                            mediaProviderName = null,
                            mediaCapability = null,
                        ),
                    ),
                )

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
            val failingEncryption = mockk<LocalDataEncryptionService>()
            every { failingEncryption.isEncrypted(any()) } answers {
                firstArg<String>().startsWith(CryptoAtRestEncryptionService.ENVELOPE_PREFIX)
            }
            every { failingEncryption.decryptString(any(), any()) } throws AEADBadTagException()
            val failingRepo =
                DefaultModelRepositoryImpl(
                    syncTrigger = syncTrigger,
                    defaultModelsDao = defaultModelsDao,
                    localModelConfigurationsDao = localConfigsDao,
                    localModelsDao = localModelsDao,
                    apiModelConfigurationsDao = apiConfigsDao,
                    apiCredentialsDao = apiCredentialsDao,
                    ttsProviderDao = ttsProviderDao,
                    mediaProviderDao = mediaProviderDao,
                    localEncryption = failingEncryption,
                )
            val mediaId = MediaProviderId("media-1")
            coEvery { defaultModelsDao.getDefault(ModelType.IMAGE_GENERATION) } returns
                DefaultModelEntity(
                    modelType = ModelType.IMAGE_GENERATION,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = null,
                    mediaProviderId = mediaId,
                    pendingSync = 0,
                )
            coEvery { mediaProviderDao.getMediaProvider(mediaId.value) } returns
                MediaProviderEntity(
                    id = mediaId.value,
                    displayName = "${CryptoAtRestEncryptionService.ENVELOPE_PREFIX}bad",
                    provider = ApiProvider.GOOGLE,
                    capability = MediaCapability.IMAGE,
                    modelName = "model",
                    baseUrl = "url",
                    credentialAlias = "alias",
                )

            val result = failingRepo.getDefault(ModelType.IMAGE_GENERATION)

            assertEquals(ModelType.IMAGE_GENERATION, result?.modelType)
            assertNull(result?.displayName)
            assertEquals("Image", result?.presetName)
        }

    @Test
    fun `setDefault calls triggerSync once`() =
        runTest {
            val configId = LocalModelConfigurationId("cfg-1")

            repo.setDefault(ModelType.MAIN, localConfigId = configId)

            verify(exactly = 1) { syncTrigger.trigger() }
        }

    @Test
    fun `clearDefault calls triggerSync once`() =
        runTest {
            repo.clearDefault(ModelType.FAST)

            verify(exactly = 1) { syncTrigger.trigger() }
        }

    @Test
    fun `setDefault upserts entity with pendingSync true`() =
        runTest {
            val configId = ApiModelConfigurationId("api-cfg-1")
            coEvery { defaultModelsDao.upsert(any()) } returns Unit

            repo.setDefault(ModelType.VISION, apiConfigId = configId)

            io.mockk.coVerify {
                defaultModelsDao.upsert(match { it.pendingSync == 1 })
            }
        }
}
