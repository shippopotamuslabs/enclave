package com.browntowndev.enclave.domain.usecase.media

import app.cash.turbine.test
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GetStudioCapablePresetsUseCaseTest {
    private lateinit var localRepo: FakeLocalModelRepository
    private lateinit var mediaProviderRepo: FakeMediaProviderRepository
    private lateinit var useCase: GetStudioCapablePresetsUseCase

    @BeforeEach
    fun setup() {
        localRepo = FakeLocalModelRepository()
        mediaProviderRepo = FakeMediaProviderRepository()
        useCase = GetStudioCapablePresetsUseCase(localRepo, mediaProviderRepo)
    }

    @Nested
    @DisplayName("S07 — Catalog filters presets per ModelType + MediaTask")
    inner class S07CatalogFiltering {
        @BeforeEach
        fun seedCatalogs() {
            localRepo.assets =
                listOf(
                    diffusionAsset(),
                    utilityAsset(),
                )
            mediaProviderRepo.providers =
                listOf(
                    provider("fal-t2i", MediaCapability.IMAGE, setOf(MediaTask.TEXT_TO_IMAGE)),
                    provider("fal-edit", MediaCapability.IMAGE, setOf(MediaTask.EDIT_IMAGE)),
                    provider(
                        "fal-both",
                        MediaCapability.IMAGE,
                        setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE),
                    ),
                    provider("fal-t2v", MediaCapability.VIDEO, setOf(MediaTask.TEXT_TO_VIDEO)),
                    provider("fal-i2v", MediaCapability.VIDEO, setOf(MediaTask.IMAGE_TO_VIDEO)),
                    provider("fal-music", MediaCapability.MUSIC, emptySet()),
                )
        }

        @Test
        @DisplayName("S07-1 IMAGE_GENERATION returns diffusion local + TEXT_TO_IMAGE providers")
        fun s07_imageGeneration() =
            runTest {
                val presets = useCase(ModelType.IMAGE_GENERATION).first()

                assertEquals(1, presets.localAssets.size)
                assertEquals("SD Pack", presets.localAssets.first().metadata.displayName)
                val providerIds = presets.mediaAssets.map { it.id.value }
                assertTrue(providerIds.contains("fal-t2i"))
                assertTrue(providerIds.contains("fal-both"))
                assertTrue(!providerIds.contains("fal-edit"))
            }

        @Test
        @DisplayName("S07-2 IMAGE_EDIT returns diffusion local + EDIT_IMAGE providers")
        fun s07_imageEdit() =
            runTest {
                val presets = useCase(ModelType.IMAGE_EDIT).first()

                assertEquals(1, presets.localAssets.size)
                val providerIds = presets.mediaAssets.map { it.id.value }
                assertTrue(providerIds.contains("fal-edit"))
                assertTrue(providerIds.contains("fal-both"))
                assertTrue(!providerIds.contains("fal-t2i"))
            }

        @Test
        @DisplayName("S07-3 VIDEO_GENERATION returns empty local + TEXT_TO_VIDEO providers")
        fun s07_videoGeneration() =
            runTest {
                val presets = useCase(ModelType.VIDEO_GENERATION).first()

                assertTrue(presets.localAssets.isEmpty())
                val providerIds = presets.mediaAssets.map { it.id.value }
                assertTrue(providerIds.contains("fal-t2v"))
                assertTrue(!providerIds.contains("fal-i2v"))
            }

        @Test
        @DisplayName("S07-4 IMAGE_TO_VIDEO returns IMAGE_TO_VIDEO providers only")
        fun s07_imageToVideo() =
            runTest {
                val presets = useCase(ModelType.IMAGE_TO_VIDEO).first()

                assertTrue(presets.localAssets.isEmpty())
                val providerIds = presets.mediaAssets.map { it.id.value }
                assertTrue(providerIds.contains("fal-i2v"))
                assertTrue(!providerIds.contains("fal-t2v"))
            }

        @Test
        @DisplayName("S07-5 MUSIC_GENERATION returns all MUSIC providers, empty local")
        fun s07_musicGeneration() =
            runTest {
                val presets = useCase(ModelType.MUSIC_GENERATION).first()

                assertTrue(presets.localAssets.isEmpty())
                assertEquals(
                    listOf("fal-music"),
                    presets.mediaAssets.map { it.id.value },
                )
            }
    }

    @Nested
    @DisplayName("E05 — Empty catalog for ModelType")
    inner class E05EmptyCatalog {
        @Test
        @DisplayName("E05 Empty catalogs produce empty presets for any ModelType")
        fun e05_emptyCatalog() =
            runTest {
                localRepo.assets = emptyList()
                mediaProviderRepo.providers = emptyList()

                val presets = useCase(ModelType.IMAGE_GENERATION).first()

                assertTrue(presets.localAssets.isEmpty())
                assertTrue(presets.mediaAssets.isEmpty())
            }
    }

    @Nested
    @DisplayName("API configs excluded from Studio picker")
    inner class ApiExcluded {
        @Test
        @DisplayName("API credentials present but never surfaced as selectable Studio presets")
        fun apiConfigsExcluded() =
            runTest {
                localRepo.assets = emptyList()
                mediaProviderRepo.providers = emptyList()

                val presets = useCase(ModelType.IMAGE_GENERATION).first()

                assertTrue(presets.mediaAssets.isEmpty())
                assertTrue(presets.localAssets.isEmpty())
            }
    }

    private fun diffusionAsset(): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd-pack"),
                    displayName = "SD Pack",
                    huggingFaceModelName = "sd-pack",
                    remoteFileName = "sd.bin",
                    localFileName = "sd.bin",
                    sha256 = LocalImageGenerationModelRegistry.SD15_CPU_SHA256,
                    sizeInBytes = 1L,
                    modelFileFormat = ModelFileFormat.GGUF,
                    source = DownloadSource.HUGGING_FACE,
                    utilityType = null,
                ),
            configurations =
                listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("sd-config"),
                        localModelId = LocalModelId("sd-pack"),
                        displayName = "SD Pack",
                        maxTokens = 0,
                        contextWindow = 0,
                        temperature = 0.0,
                        topP = 1.0,
                        topK = 1,
                        repetitionPenalty = 1.0,
                        systemPrompt = "",
                    ),
                ),
        )

    @Suppress("unused")
    private fun utilityAsset(): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("whisper-1"),
                    displayName = "Whisper",
                    huggingFaceModelName = "whisper",
                    remoteFileName = "whisper.bin",
                    localFileName = "whisper.bin",
                    sha256 = "sha-whisper",
                    sizeInBytes = 1L,
                    modelFileFormat = ModelFileFormat.GGUF,
                    source = DownloadSource.HUGGING_FACE,
                    utilityType = UtilityType.WHISPER,
                ),
            configurations =
                listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("whisper-config"),
                        localModelId = LocalModelId("whisper-1"),
                        displayName = "Whisper",
                        maxTokens = 0,
                        contextWindow = 0,
                        temperature = 0.0,
                        topP = 1.0,
                        topK = 1,
                        repetitionPenalty = 1.0,
                        systemPrompt = "",
                    ),
                ),
        )

    private fun provider(
        id: String,
        capability: MediaCapability,
        tasks: Set<MediaTask>,
    ): MediaProviderAsset =
        MediaProviderAsset(
            id = MediaProviderId(id),
            displayName = id,
            provider = ApiProvider.OPENAI,
            capability = capability,
            credentialAlias = "$id-alias",
            tasks = tasks,
        )
}

private class FakeLocalModelRepository : LocalModelRepositoryPort {
    var assets: List<LocalModelAsset> = emptyList()

    override fun observeAllLocalAssets() = flowOf(assets)

    override suspend fun getAllLocalAssets(): List<LocalModelAsset> = assets

    override suspend fun clearAll() = Unit

    override suspend fun upsertLocalAsset(asset: LocalModelAsset): LocalModelId = asset.metadata.id

    override suspend fun upsertLocalConfiguration(config: LocalModelConfiguration): LocalModelConfigurationId = config.id

    override suspend fun saveLocalModelMetadata(metadata: LocalModelMetadata): LocalModelId = metadata.id

    override suspend fun deleteLocalModelMetadata(id: LocalModelId) = Unit

    override suspend fun saveConfiguration(config: LocalModelConfiguration): LocalModelConfigurationId = config.id

    override suspend fun deleteConfiguration(id: LocalModelConfigurationId) = Unit

    override suspend fun getConfigurationById(id: LocalModelConfigurationId): LocalModelConfiguration? = null

    override suspend fun getAllConfigurationsForAsset(localModelId: LocalModelId): List<LocalModelConfiguration> = emptyList()

    override suspend fun deleteAllConfigurationsForAsset(localModelId: LocalModelId) = Unit

    override suspend fun getSoftDeletedModels(): List<LocalModelAsset> = emptyList()

    override suspend fun getAssetById(id: LocalModelId): LocalModelAsset? = assets.find { it.metadata.id == id }

    override suspend fun getAssetByConfigId(configId: LocalModelConfigurationId): LocalModelAsset? =
        assets.find { asset -> asset.configurations.any { it.id == configId } }

    override suspend fun restoreSoftDeletedModel(
        id: LocalModelId,
        configurations: List<LocalModelConfiguration>,
    ): LocalModelAsset = assets.first { it.metadata.id == id }
}

private class FakeApiModelRepository : ApiModelRepositoryPort {
    var credentials: List<ApiCredentials> = emptyList()
    var configurations: List<ApiModelConfiguration> = emptyList()

    override fun observeAllCredentials() = flowOf(credentials)

    override fun observeAllConfigurations() = flowOf(configurations)

    override suspend fun getAllCredentials(): List<ApiCredentials> = credentials

    override suspend fun getAllCredentialAliases(): Set<String> = emptySet()

    override suspend fun getCredentialsById(id: ApiCredentialsId): ApiCredentials? = credentials.find { it.id == id }

    override suspend fun saveCredentials(
        credentials: ApiCredentials,
        apiKey: String,
        sourceCredentialAlias: String?,
    ): ApiCredentialsId = credentials.id

    override suspend fun findMatchingCredentials(
        provider: ApiProvider,
        baseUrl: String?,
        apiKey: String,
        sourceCredentialAlias: String?,
    ): ApiCredentials? = null

    override suspend fun deleteCredentials(id: ApiCredentialsId) = Unit

    override suspend fun getConfigurationsForCredentials(credentialsId: ApiCredentialsId): List<ApiModelConfiguration> =
        configurations.filter { it.apiCredentialsId == credentialsId }

    override suspend fun getConfigurationById(id: ApiModelConfigurationId): ApiModelConfiguration? =
        configurations.find { it.id == id }

    override suspend fun saveConfiguration(config: ApiModelConfiguration): ApiModelConfigurationId = config.id

    override suspend fun deleteConfiguration(id: ApiModelConfigurationId) = Unit

    override suspend fun deleteConfigurationsForCredentials(credentialsId: ApiCredentialsId) = Unit
}

private class FakeMediaProviderRepository : MediaProviderRepositoryPort {
    var providers: List<MediaProviderAsset> = emptyList()

    override fun getMediaProviders(): Flow<List<MediaProviderAsset>> = flowOf(providers)

    override suspend fun getMediaProvidersSync(): List<MediaProviderAsset> = providers

    override suspend fun getMediaProvider(id: MediaProviderId): MediaProviderAsset? =
        providers.find { it.id == id }

    override suspend fun saveMediaProvider(
        asset: MediaProviderAsset,
        apiKey: String?,
    ): MediaProviderId = asset.id

    override suspend fun deleteMediaProvider(id: MediaProviderId) = Unit
}
