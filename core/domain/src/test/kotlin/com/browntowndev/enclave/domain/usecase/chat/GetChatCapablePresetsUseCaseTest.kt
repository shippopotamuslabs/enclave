package com.browntowndev.enclave.domain.usecase.chat

import app.cash.turbine.test
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GetChatCapablePresetsUseCaseTest {
    private lateinit var localRepo: FakeLocalModelRepository
    private lateinit var apiRepo: FakeApiModelRepository
    private lateinit var useCase: GetChatCapablePresetsUseCase

    @BeforeEach
    fun setup() {
        localRepo = FakeLocalModelRepository()
        apiRepo = FakeApiModelRepository()
        useCase = GetChatCapablePresetsUseCase(localRepo, apiRepo)
    }

    @Test
    @DisplayName("S03 — Returns chat-capable local and API assets; excludes image-gen local")
    fun s03_returnsChatCapableAssets() =
        runTest {
            localRepo.assets =
                listOf(
                    localAsset(
                        displayName = "Qwen 3 8B",
                        isImageGenerationCapable = false,
                        configId = "preset-A",
                    ),
                    localAsset(
                        displayName = "SD 1.5",
                        isImageGenerationCapable = true,
                        configId = "preset-B",
                    ),
                )
            apiRepo.credentials =
                listOf(
                    ApiCredentials(
                        id = ApiCredentialsId("cred-1"),
                        displayName = "OpenRouter",
                        provider = ApiProvider.OPENROUTER,
                        credentialAlias = "openrouter",
                    ),
                )
            apiRepo.configurations =
                listOf(
                    ApiModelConfiguration(
                        apiCredentialsId = ApiCredentialsId("cred-1"),
                        modelId = "anthropic/claude-3.5-sonnet",
                        displayName = "Claude 3.5 Sonnet",
                    ),
                )

            val presets = useCase().first()

            assertEquals(
                listOf("Qwen 3 8B"),
                presets.localAssets.map { it.metadata.displayName },
            )
            assertTrue(presets.localAssets.none { it.metadata.displayName == "SD 1.5" })
            assertEquals("OpenRouter", presets.apiAssets.single().credentials.displayName)
        }

    @Test
    @DisplayName("S15 — Empty catalog returns empty lists")
    fun s15_emptyCatalogReturnsEmptyLists() =
        runTest {
            localRepo.assets = emptyList()
            apiRepo.credentials = emptyList()
            apiRepo.configurations = emptyList()

            val presets = useCase().first()

            assertEquals(emptyList<LocalModelAsset>(), presets.localAssets)
            assertEquals(emptyList<ApiModelAsset>(), presets.apiAssets)
        }

    private fun localAsset(
        displayName: String,
        isImageGenerationCapable: Boolean,
        configId: String,
    ): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId(displayName),
                    displayName = displayName,
                    huggingFaceModelName = displayName,
                    remoteFileName = "$displayName.bin",
                    localFileName = "$displayName.bin",
                    sha256 = "sha-$displayName",
                    sizeInBytes = 1L,
                    modelFileFormat = ModelFileFormat.GGUF,
                    source = DownloadSource.HUGGING_FACE,
                    utilityType = if (isImageGenerationCapable) UtilityType.SD15_QNN_PACK else null,
                ),
            configurations =
                listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId(configId),
                        localModelId = LocalModelId(displayName),
                        displayName = displayName,
                        maxTokens = 2048,
                        contextWindow = 4096,
                        temperature = 0.7,
                        topP = 0.9,
                        topK = 40,
                        repetitionPenalty = 1.1,
                        systemPrompt = "",
                    ),
                ),
        )
}

private class FakeLocalModelRepository : LocalModelRepositoryPort {
    var assets: List<LocalModelAsset> = emptyList()

    override fun observeAllLocalAssets() = flowOf(assets)

    override suspend fun getAllLocalAssets(): List<LocalModelAsset> = assets

    override suspend fun clearAll() = Unit

    override suspend fun upsertLocalAsset(asset: LocalModelAsset): LocalModelId =
        asset.metadata.id

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

    override suspend fun getCredentialsById(id: ApiCredentialsId): ApiCredentials? =
        credentials.find { it.id == id }

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
