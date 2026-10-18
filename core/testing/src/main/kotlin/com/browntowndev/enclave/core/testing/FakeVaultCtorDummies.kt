package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.download.ParsedModelCatalog
import com.browntowndev.enclave.domain.model.config.RemoteModelAsset
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.download.ModelFileScannerPort
import com.browntowndev.enclave.domain.port.repository.CleanupRepository
import com.browntowndev.enclave.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities
import com.browntowndev.enclave.domain.port.device.SnapdragonTierPort
import com.browntowndev.enclave.domain.port.download.QnnPackInstallProgressListener
import com.browntowndev.enclave.domain.port.download.QnnPackInstallerPort
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.MusicGenerationPort
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogPort
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogResult
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.port.repository.TrustedDomainRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.domain.port.media.AudioCapturePort
import com.browntowndev.enclave.domain.port.inference.WhisperInferencePort
import com.browntowndev.enclave.domain.port.config.DistributionConfigurationPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Minimal port fakes (ENC-28) used to construct real use-case objects in tests
 * whose behavior is exercised through other collaborators. Each returns inert
 * defaults; recording variants live in their own fakes where tests need them.
 */
class FakeModelConfigFetcher : ModelConfigFetcherPort {
    /** Result returned by [getPersistedParsedCatalog]; defaults to the historical failure. */
    var persistedCatalogResult: Result<ParsedModelCatalog> =
        Result.failure(UnsupportedOperationException("not used in tests"))

    /** Result returned by [fetchParsedCatalog]; defaults to the historical failure. */
    var parsedCatalogResult: Result<ParsedModelCatalog> =
        Result.failure(UnsupportedOperationException("not used in tests"))

    /** Result returned by [fetchRemoteConfig]; defaults to the historical empty success. */
    var fetchRemoteConfigResult: Result<List<LocalModelAsset>> = Result.success(emptyList())

    /** Value returned by [toLocalModelAssets]; defaults to the historical empty list. */
    var localModelAssetsResult: List<LocalModelAsset> = emptyList()

    override suspend fun fetchRemoteConfig(): Result<List<LocalModelAsset>> = fetchRemoteConfigResult

    override suspend fun fetchParsedCatalog(): Result<ParsedModelCatalog> = parsedCatalogResult

    override suspend fun getPersistedParsedCatalog(): Result<ParsedModelCatalog> = persistedCatalogResult

    override fun toLocalModelAssets(assets: List<RemoteModelAsset>): List<LocalModelAsset> = localModelAssetsResult
}

class FakeTransactionProvider : TransactionProvider {
    /** Number of [runInTransaction] invocations (ENC-28). */
    var runInTransactionCallCount = 0
        private set

    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        runInTransactionCallCount++
        return block()
    }
}

class FakeModelFileScanner : ModelFileScannerPort {
    /** Recorded delete calls for assertions (ENC-28). */
    val deletedModelIds = mutableListOf<LocalModelId>()
    val deletedFileNames = mutableListOf<String>()

    /** Result returned by [scanAndCreateDirIfNotExist]; defaults to the historical all-valid scan. */
    var scanResult: ModelScanResult =
        ModelScanResult(missingModels = emptyList(), partialDownloads = emptyMap(), allValid = true)

    /** Every (expectedModels, utilityAssets) pair passed to [scanAndCreateDirIfNotExist], in call order. */
    val scanCalls = mutableListOf<Pair<Map<ModelType, LocalModelAsset>, List<LocalModelAsset>>>()

    override suspend fun scanAndCreateDirIfNotExist(
        expectedModels: Map<ModelType, LocalModelAsset>,
        utilityAssets: List<LocalModelAsset>,
    ): ModelScanResult {
        scanCalls += expectedModels to utilityAssets
        return scanResult
    }

    override suspend fun deleteModelFile(localModelId: LocalModelId) {
        deletedModelIds += localModelId
    }

    override suspend fun deleteModelFileByLocalFileName(localFileName: String) {
        deletedFileNames += localFileName
    }
}

class FakeApiKeyProvider : ApiKeyProviderPort {
    private val keys = mutableMapOf<String, String>()

    /** Aliases passed to [getApiKey], in call order (ENC-28). */
    val getApiKeyCalls = mutableListOf<String>()

    override suspend fun getApiKey(credentialAlias: String): String? {
        getApiKeyCalls += credentialAlias
        return keys[credentialAlias]
    }

    override fun saveApiKey(credentialAlias: String, apiKey: String) {
        keys[credentialAlias] = apiKey
    }
}

class FakeCleanupRepository : CleanupRepository {
    var clearAllDataCallCount = 0
    var clearUserContentCallCount = 0

    /** When set, [clearUserContent] throws this after recording the call. */
    var clearUserContentFailure: Throwable? = null

    override suspend fun clearAllData() {
        clearAllDataCallCount++
    }

    override suspend fun clearUserContent() {
        clearUserContentCallCount++
        clearUserContentFailure?.let { throw it }
    }

    /** Number of [deleteAllArtifacts] calls so far (ENC-28). */
    var deleteAllArtifactsCallCount = 0

    /** When set, [deleteAllArtifacts] throws this after recording the call (ENC-28). */
    var deleteAllArtifactsFailure: Throwable? = null

    override suspend fun deleteAllArtifacts() {
        deleteAllArtifactsCallCount++
        deleteAllArtifactsFailure?.let { throw it }
    }
}

class FakeTrustedDomainRepository : TrustedDomainRepositoryPort {
    /** Domains returned by [getTrustedDomains]; empty by default. */
    var domains: Set<String> = emptySet()

    /** Wildcards returned by [getTrustedWildcards]; empty by default. */
    var wildcards: Set<String> = emptySet()

    override suspend fun getTrustedDomains(): Set<String> = domains

    override suspend fun getTrustedWildcards(): Set<String> = wildcards

    override suspend fun syncTrustedDomains(): Result<Unit> = Result.success(Unit)
}

class FakeGpuCapability : GpuCapabilityPort {
    /** Returned by [isLocalImageGenerationSupported]; defaults to the historical false. */
    var localImageGenerationSupported: Boolean = false

    override fun isLocalImageGenerationSupported(): Boolean = localImageGenerationSupported
}

class FakeLocalImageGenerationCapability : LocalImageGenerationCapabilityPort {
    /** Capabilities returned by [getCapabilities]; defaults to the historical all-NO set. */
    @get:JvmName("capabilitiesBacking")
    var capabilities: LocalImageGenerationCapabilities =
        LocalImageGenerationCapabilities(
            npuAvailable = LocalImageGenTriState.NO,
            sd15NpuSupported = LocalImageGenTriState.NO,
            sd15CpuSupported = LocalImageGenTriState.NO,
            sdxlNpuSupported = LocalImageGenTriState.NO,
            recommendedBackend = LocalImageGenerationBackendKind.NONE,
            recommendedModelId = null,
            diagnostics = emptyList(),
        )

    /** Number of [getCapabilities] calls so far (ENC-28). */
    var getCapabilitiesCallCount: Int = 0
        private set

    override fun getCapabilities(): LocalImageGenerationCapabilities {
        getCapabilitiesCallCount++
        return capabilities
    }
}

class FakeQnnPackInstaller : QnnPackInstallerPort {
    /** Value returned by [installIfNeeded]; defaults to the historical false. */
    var installResult: Boolean = false

    /** Every metadata passed to [installIfNeeded], in call order. */
    val installCalls = mutableListOf<LocalModelMetadata>()

    override suspend fun installIfNeeded(
        metadata: LocalModelMetadata,
        progressListener: QnnPackInstallProgressListener?,
    ): Boolean {
        installCalls += metadata
        return installResult
    }
}

class FakeSnapdragonTier : SnapdragonTierPort {
    override fun currentTier(): SnapdragonTier = SnapdragonTier.UNKNOWN
}

class FakeUtilityModelRepository : UtilityModelRepositoryPort {
    /** Per-type metadata returned by [getByUtilityType]; empty (null) by default (ENC-28). */
    val metadataByType = mutableMapOf<UtilityType, UtilityModelMetadata>()

    /** Ids passed to [softDelete], in call order (ENC-28). */
    val softDeletedIds = mutableListOf<UtilityModelId>()

    override suspend fun getByUtilityType(utilityType: UtilityType): UtilityModelMetadata? = metadataByType[utilityType]

    override suspend fun getAllActive(): List<UtilityModelMetadata> = emptyList()

    override fun observeAllActive(): Flow<List<UtilityModelMetadata>> = flowOf(emptyList())

    override suspend fun getById(id: UtilityModelId): UtilityModelMetadata? = null

    override suspend fun getBySha256(sha256: String): UtilityModelMetadata? = null

    override suspend fun upsert(metadata: UtilityModelMetadata): UtilityModelId = metadata.id

    override suspend fun softDelete(id: UtilityModelId) {
        softDeletedIds += id
    }
}

class FakeDistributionConfiguration(
    override var isPlayStoreFlavor: Boolean = false,
) : DistributionConfigurationPort

class FakeAudioCapture : AudioCapturePort {
    override fun audioChunks(): Flow<FloatArray> = emptyFlow()
}

class FakeWhisperInference : WhisperInferencePort {
    override suspend fun initialize(modelPath: String) = Unit

    override suspend fun transcribe(samples: FloatArray): String = ""

    override suspend fun close() = Unit
}

class FakeUtilityModelFile : UtilityModelFilePort {
    /** Path returned by [resolveLocalModelPath]; null (not downloaded) by default (ENC-28). */
    var localModelPath: String? = null

    /** Metadata passed to [resolveLocalModelPath], in call order (ENC-28). */
    val resolveLocalModelPathCalls = mutableListOf<LocalModelMetadata>()

    override suspend fun resolveUtilityModelPath(utilityType: UtilityType): String? = null

    override suspend fun resolveLocalModelPath(metadata: LocalModelMetadata): String? {
        resolveLocalModelPathCalls += metadata
        return localModelPath
    }
}

class FakeMusicGenerationPort : MusicGenerationPort {
    var result: Result<ByteArray> = Result.success(ByteArray(0))

    /** Prompts passed to [generateMusic], in call order (ENC-28). */
    val calls = mutableListOf<String>()

    /** Providers passed to [generateMusic], in call order (ENC-28). */
    val providerCalls = mutableListOf<MediaProviderAsset>()

    /** Settings passed to [generateMusic], in call order (ENC-28). */
    val settingsCalls = mutableListOf<GenerationSettings>()

    override suspend fun generateMusic(
        prompt: String,
        provider: MediaProviderAsset,
        settings: GenerationSettings,
    ): Result<ByteArray> {
        calls += prompt
        providerCalls += provider
        settingsCalls += settings
        return result
    }
}

class FakeApiModelCatalogPort : ApiModelCatalogPort {
    /** Recorded calls for assertions (ENC-28). */
    data class FetchModelsCall(
        val provider: ApiProvider,
        val apiKey: String,
        val baseUrl: String?,
    )

    data class FetchModelCall(
        val provider: ApiProvider,
        val apiKey: String,
        val modelId: String,
        val baseUrl: String?,
    )

    data class FetchModelEndpointsCall(
        val modelId: String,
        val apiKey: String,
        val baseUrl: String?,
    )

    val fetchModelsCalls = mutableListOf<FetchModelsCall>()
    val fetchModelCalls = mutableListOf<FetchModelCall>()
    val fetchModelEndpointsCalls = mutableListOf<FetchModelEndpointsCall>()

    /** Result returned by [fetchModels]; defaults to the historical empty catalog. */
    var modelsResult: ApiModelCatalogResult = ApiModelCatalogResult(models = emptyList())

    /** Model returned by [fetchModel]; defaults to the historical null. */
    var modelResult: DiscoveredApiModel? = null

    /** Endpoints returned by [fetchModelEndpoints]; defaults to the historical empty list. */
    var endpointsResult: List<OpenRouterModelEndpoint> = emptyList()

    override suspend fun fetchModels(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String?,
    ): ApiModelCatalogResult {
        fetchModelsCalls += FetchModelsCall(provider, apiKey, baseUrl)
        return modelsResult
    }

    override suspend fun fetchModel(
        provider: ApiProvider,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
    ): DiscoveredApiModel? {
        fetchModelCalls += FetchModelCall(provider, apiKey, modelId, baseUrl)
        return modelResult
    }

    override suspend fun fetchModelEndpoints(
        modelId: String,
        apiKey: String,
        baseUrl: String?,
    ): List<OpenRouterModelEndpoint> {
        fetchModelEndpointsCalls += FetchModelEndpointsCall(modelId, apiKey, baseUrl)
        return endpointsResult
    }
}
