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
import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.port.repository.TrustedDomainRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Minimal port fakes (ENC-28) used to construct real use-case objects in tests
 * whose behavior is exercised through other collaborators. Each returns inert
 * defaults; recording variants live in their own fakes where tests need them.
 */
class FakeModelConfigFetcher : ModelConfigFetcherPort {
    override suspend fun fetchRemoteConfig(): Result<List<LocalModelAsset>> = Result.success(emptyList())

    override suspend fun fetchParsedCatalog(): Result<ParsedModelCatalog> =
        Result.failure(UnsupportedOperationException("not used in tests"))

    override suspend fun getPersistedParsedCatalog(): Result<ParsedModelCatalog> =
        Result.failure(UnsupportedOperationException("not used in tests"))

    override fun toLocalModelAssets(assets: List<RemoteModelAsset>): List<LocalModelAsset> = emptyList()
}

class FakeTransactionProvider : TransactionProvider {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
}

class FakeModelFileScanner : ModelFileScannerPort {
    override suspend fun scanAndCreateDirIfNotExist(
        expectedModels: Map<ModelType, LocalModelAsset>,
        utilityAssets: List<LocalModelAsset>,
    ): ModelScanResult =
        ModelScanResult(missingModels = emptyList(), partialDownloads = emptyMap(), allValid = true)

    override suspend fun deleteModelFile(localModelId: LocalModelId) = Unit

    override suspend fun deleteModelFileByLocalFileName(localFileName: String) = Unit
}

class FakeApiKeyProvider : ApiKeyProviderPort {
    private val keys = mutableMapOf<String, String>()

    override suspend fun getApiKey(credentialAlias: String): String? = keys[credentialAlias]

    override fun saveApiKey(credentialAlias: String, apiKey: String) {
        keys[credentialAlias] = apiKey
    }
}

class FakeCleanupRepository : CleanupRepository {
    var clearAllDataCallCount = 0

    override suspend fun clearAllData() {
        clearAllDataCallCount++
    }

    override suspend fun clearUserContent() = Unit

    override suspend fun deleteAllArtifacts() = Unit
}

class FakeTrustedDomainRepository : TrustedDomainRepositoryPort {
    override suspend fun getTrustedDomains(): Set<String> = emptySet()

    override suspend fun getTrustedWildcards(): Set<String> = emptySet()

    override suspend fun syncTrustedDomains(): Result<Unit> = Result.success(Unit)
}

class FakeGpuCapability : GpuCapabilityPort {
    override fun isLocalImageGenerationSupported(): Boolean = false
}

class FakeLocalImageGenerationCapability : LocalImageGenerationCapabilityPort {
    override fun getCapabilities(): LocalImageGenerationCapabilities =
        LocalImageGenerationCapabilities(
            npuAvailable = LocalImageGenTriState.NO,
            sd15NpuSupported = LocalImageGenTriState.NO,
            sd15CpuSupported = LocalImageGenTriState.NO,
            sdxlNpuSupported = LocalImageGenTriState.NO,
            recommendedBackend = LocalImageGenerationBackendKind.NONE,
            recommendedModelId = null,
            diagnostics = emptyList(),
        )
}

class FakeQnnPackInstaller : QnnPackInstallerPort {
    override suspend fun installIfNeeded(
        metadata: LocalModelMetadata,
        progressListener: QnnPackInstallProgressListener?,
    ): Boolean = false
}

class FakeSnapdragonTier : SnapdragonTierPort {
    override fun currentTier(): SnapdragonTier = SnapdragonTier.UNKNOWN
}

class FakeUtilityModelRepository : UtilityModelRepositoryPort {
    override suspend fun getByUtilityType(utilityType: UtilityType): UtilityModelMetadata? = null

    override suspend fun getAllActive(): List<UtilityModelMetadata> = emptyList()

    override fun observeAllActive(): Flow<List<UtilityModelMetadata>> = flowOf(emptyList())

    override suspend fun getById(id: UtilityModelId): UtilityModelMetadata? = null

    override suspend fun getBySha256(sha256: String): UtilityModelMetadata? = null

    override suspend fun upsert(metadata: UtilityModelMetadata): UtilityModelId = metadata.id

    override suspend fun softDelete(id: UtilityModelId) = Unit
}
