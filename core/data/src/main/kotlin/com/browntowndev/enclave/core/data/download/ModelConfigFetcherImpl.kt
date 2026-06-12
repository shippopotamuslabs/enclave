package com.browntowndev.enclave.core.data.download

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.RemoteModelAsset
import com.browntowndev.enclave.domain.model.config.RemoteModelPack
import com.browntowndev.enclave.domain.model.config.RemoteModelPackMember
import com.browntowndev.enclave.domain.model.config.effectiveDisplayName
import com.browntowndev.enclave.domain.model.download.ModelPack
import com.browntowndev.enclave.domain.model.download.ModelPackMember
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.model.download.ParsedModelCatalog
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.download.ModelUrlProviderPort
import com.browntowndev.enclave.domain.port.repository.ModelConfigFetcherPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class RemoteConfigResponse(
    val assets: List<RemoteModelAsset>,
    val modelPacks: List<RemoteModelPack> = emptyList(),
)

@Singleton
class ModelConfigFetcherImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
        private val modelUrlProvider: ModelUrlProviderPort,
    ) : ModelConfigFetcherPort {
        companion object {
            private const val TAG = "ModelConfigFetcher"
            private const val CATALOG_CACHE_FILE_NAME = "model_config_cache.json"
            private const val LEGACY_SDXL_DIFFUSION_FILE = "realvis_xl_v5_dmd2.gguf"
            private val json =
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
        }

        @Volatile
        private var lastSuccessfulCatalog: ParsedModelCatalog? = null

        override suspend fun getPersistedParsedCatalog(): Result<ParsedModelCatalog> =
            withContext(Dispatchers.IO) {
                discardStaleCatalogCacheIfNeeded()
                lastSuccessfulCatalog?.let { cached ->
                    if (!isStaleCatalog(cached)) {
                        return@withContext Result.success(cached)
                    }
                    lastSuccessfulCatalog = null
                }
                loadCatalogFromDisk()?.let { catalog ->
                    lastSuccessfulCatalog = catalog
                    return@withContext Result.success(catalog)
                }
                Result.failure(IllegalStateException("No persisted model catalog available"))
            }

        override suspend fun fetchParsedCatalog(): Result<ParsedModelCatalog> =
            withContext(Dispatchers.IO) {
                discardStaleCatalogCacheIfNeeded()
                try {
                    val bodyString = fetchRemoteConfigBody()
                    val catalog = parseCatalogFromJson(bodyString)
                    lastSuccessfulCatalog = catalog
                    persistCatalogJson(bodyString)
                    Result.success(catalog)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch model config: ${e.message}", e)
                    resolveCatalogAfterFetchFailure(e)
                }
            }

        override suspend fun fetchRemoteConfig(): Result<List<LocalModelAsset>> = fetchParsedCatalog().map { it.assets }

        private fun resolveCatalogAfterFetchFailure(cause: Exception): Result<ParsedModelCatalog> {
            lastSuccessfulCatalog?.let { cached ->
                if (!isStaleCatalog(cached)) {
                    Log.w(TAG, "Serving in-memory model catalog (${cached.packs.size} packs)")
                    return Result.success(cached)
                }
                lastSuccessfulCatalog = null
            }
            loadCatalogFromDisk()?.let { catalog ->
                lastSuccessfulCatalog = catalog
                Log.w(TAG, "Serving on-disk model catalog (${catalog.packs.size} packs)")
                return Result.success(catalog)
            }
            return Result.failure(cause)
        }

        private fun isStaleCatalogJson(bodyString: String): Boolean = bodyString.contains(LEGACY_SDXL_DIFFUSION_FILE)

        private fun isStaleCatalog(catalog: ParsedModelCatalog): Boolean =
            catalog.assets.any { asset -> asset.metadata.localFileName == LEGACY_SDXL_DIFFUSION_FILE }

        private fun discardStaleCatalogCacheIfNeeded() {
            val file = catalogCacheFile()
            if (!file.exists()) {
                return
            }
            val body =
                runCatching { file.readText() }.getOrNull()
                    ?: return
            if (!isStaleCatalogJson(body)) {
                return
            }
            Log.w(TAG, "Discarding stale model catalog cache (legacy SDXL GGUF filename)")
            file.delete()
            lastSuccessfulCatalog = null
        }

        private fun catalogCacheFile(): File = File(context.filesDir, CATALOG_CACHE_FILE_NAME)

        private fun persistCatalogJson(bodyString: String) {
            runCatching {
                catalogCacheFile().writeText(bodyString)
            }.onFailure { error ->
                Log.w(TAG, "Failed to persist model catalog cache: ${error.message}")
            }
        }

        private fun loadCatalogFromDisk(): ParsedModelCatalog? {
            val file = catalogCacheFile()
            if (!file.exists()) {
                return null
            }
            val body =
                runCatching { file.readText() }
                    .onFailure { error ->
                        Log.w(TAG, "Failed to read model catalog cache: ${error.message}")
                    }.getOrNull()
                    ?: return null
            if (isStaleCatalogJson(body)) {
                Log.w(TAG, "Discarding stale model catalog cache (legacy SDXL GGUF filename)")
                file.delete()
                return null
            }
            return runCatching {
                parseCatalogFromJson(body)
            }.onFailure { error ->
                Log.w(TAG, "Failed to parse model catalog cache: ${error.message}")
            }.getOrNull()
        }

        private fun parseCatalogFromJson(bodyString: String): ParsedModelCatalog {
            val configResponse = json.decodeFromString<RemoteConfigResponse>(bodyString)
            val assets = toLocalModelAssets(configResponse.assets)
            val packs = buildModelPacks(configResponse.modelPacks, assets)
            Log.i(TAG, "Parsed ${configResponse.assets.size} model assets, ${packs.size} packs")
            return ParsedModelCatalog(assets = assets, packs = packs)
        }

        private fun fetchRemoteConfigBody(): String {
            val configUrl = modelUrlProvider.getConfigUrl()
            Log.i(TAG, "Fetching config from URL: $configUrl")
            val request =
                Request
                    .Builder()
                    .url(configUrl)
                    .header("Cache-Control", "no-cache")
                    .build()

            return okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }
                response.body?.string() ?: throw Exception("Empty response body")
            }
        }

        private fun buildModelPacks(
            remotePacks: List<RemoteModelPack>,
            assets: List<LocalModelAsset>,
        ): List<ModelPack> {
            val packIds = mutableSetOf<String>()

            return remotePacks.map { remotePack ->
                require(packIds.add(remotePack.packId)) {
                    "Duplicate model pack id: ${remotePack.packId}"
                }
                val members =
                    remotePack.members.map { member ->
                        val role =
                            ModelPackRole.fromApiValue(member.role)
                                ?: throw IllegalArgumentException("Unknown pack member role: ${member.role}")
                        val asset = resolvePackMemberAsset(member, role, assets)
                        validatePackMember(role, member, asset)
                        ModelPackMember(role = role, asset = asset)
                    }
                ModelPack(
                    packId = remotePack.packId,
                    displayName = remotePack.displayName,
                    slot = remotePack.slot,
                    members = members,
                )
            }
        }

        /**
         * Resolves a pack member to the correct catalog asset when multiple [assets] rows share
         * the same [RemoteModelPackMember.fileName] (e.g. SDXL diffusion + utility zip).
         */
        private fun resolvePackMemberAsset(
            member: RemoteModelPackMember,
            role: ModelPackRole,
            assets: List<LocalModelAsset>,
        ): LocalModelAsset {
            val candidates = assets.filter { it.metadata.localFileName == member.fileName }
            require(candidates.isNotEmpty()) {
                "Pack member file not found in assets: ${member.fileName}"
            }
            return when (role) {
                ModelPackRole.DIFFUSION ->
                    candidates.firstOrNull { asset ->
                        asset.metadata.utilityType == null &&
                            asset.configurations.any { config ->
                                ModelType.IMAGE_GENERATION in config.defaultAssignments ||
                                    ModelType.IMAGE_EDIT in config.defaultAssignments
                            }
                    } ?: error(
                        "Diffusion pack member must reference a non-utility image asset: ${member.fileName}",
                    )
                ModelPackRole.VAE,
                ModelPackRole.TEXT_ENCODER,
                -> {
                    val utilityType =
                        requireNotNull(member.utilityType) {
                            "Utility pack member must declare utilityType: ${member.fileName}"
                        }
                    candidates.firstOrNull { it.metadata.utilityType == utilityType }
                        ?: error(
                            "Pack member utilityType mismatch for ${member.fileName}: expected $utilityType",
                        )
                }
            }
        }

        private fun validatePackMember(
            role: ModelPackRole,
            member: RemoteModelPackMember,
            asset: LocalModelAsset,
        ) {
            when (role) {
                ModelPackRole.DIFFUSION -> {
                    require(member.utilityType == null) {
                        "Diffusion pack member must not declare utilityType: ${member.fileName}"
                    }
                    require(asset.metadata.utilityType == null) {
                        "Diffusion pack member must reference a non-utility asset: ${member.fileName}"
                    }
                    require(
                        asset.configurations.any { config ->
                            ModelType.IMAGE_GENERATION in config.defaultAssignments ||
                                ModelType.IMAGE_EDIT in config.defaultAssignments
                        },
                    ) {
                        "Diffusion pack member must default-assign image_generation or image_edit: ${member.fileName}"
                    }
                }
                ModelPackRole.VAE,
                ModelPackRole.TEXT_ENCODER,
                -> {
                    val utilityType =
                        member.utilityType
                            ?: throw IllegalArgumentException(
                                "Utility pack member must declare utilityType: ${member.fileName}",
                            )
                    require(asset.metadata.utilityType == utilityType) {
                        "Pack member utilityType mismatch for ${member.fileName}: " +
                            "expected $utilityType, found ${asset.metadata.utilityType}"
                    }
                }
            }
        }

        /**
         * Converts RemoteModelAsset list to a list of LocalModelAsset.
         * Each asset naturally contains its configurations; no deduplication needed.
         */
        override fun toLocalModelAssets(assets: List<RemoteModelAsset>): List<LocalModelAsset> =
            assets.map { asset ->
                Log.d(TAG, "Model Asset File: ${asset.fileName}, configs: ${asset.configurations.size}")

                validateAsset(asset)

                val assetDisplayName = asset.effectiveDisplayName()

                val metadata =
                    LocalModelMetadata(
                        id = LocalModelId(""),
                        displayName = assetDisplayName,
                        huggingFaceModelName = asset.huggingFaceModelName,
                        huggingFacePath = asset.huggingFacePath,
                        remoteFileName = asset.fileName,
                        localFileName = asset.fileName,
                        sha256 = asset.sha256,
                        sizeInBytes = asset.sizeInBytes,
                        modelFileFormat = asset.modelFileFormat,
                        source = asset.source,
                        utilityType = asset.utilityType,
                        isMultimodal = asset.isMultimodal,
                        mmprojRemoteFileName = asset.mmprojFileName,
                        mmprojLocalFileName = asset.mmprojFileName,
                        mmprojSha256 = asset.mmprojSha256,
                        mmprojSizeInBytes = asset.mmprojSizeInBytes,
                    )

                val configurations =
                    asset.configurations.map { config ->
                        LocalModelConfiguration(
                            id = config.configId,
                            localModelId = LocalModelId(""),
                            displayName = config.displayName,
                            maxTokens = config.maxTokens,
                            contextWindow = config.contextWindow,
                            temperature = config.temperature,
                            topP = config.topP,
                            topK = config.topK,
                            minP = config.minP,
                            repetitionPenalty = config.repetitionPenalty,
                            thinkingEnabled = config.thinkingEnabled,
                            systemPrompt = config.systemPrompt,
                            isSystemPreset = true,
                            defaultAssignments = config.defaultAssignments,
                        )
                    }

                LocalModelAsset(
                    metadata = metadata,
                    configurations = configurations,
                )
            }

        private fun validateAsset(asset: RemoteModelAsset) {
            if (asset.utilityType != null) {
                require(asset.configurations.isEmpty()) {
                    "Utility asset ${asset.fileName} must not declare LLM configurations."
                }
                return
            }

            require(asset.configurations.isNotEmpty()) {
                "Non-utility asset ${asset.fileName} must declare at least one configuration."
            }
            require(asset.effectiveDisplayName().isNotBlank()) {
                "Non-utility asset ${asset.fileName} must declare displayName."
            }
        }
    }
