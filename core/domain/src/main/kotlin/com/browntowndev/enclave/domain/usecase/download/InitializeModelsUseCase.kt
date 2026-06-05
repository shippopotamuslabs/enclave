package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.ImageGenerationPackMemberFilter
import com.browntowndev.enclave.domain.model.download.ParsedModelCatalog
import com.browntowndev.enclave.domain.model.download.filterForDownload
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.enclave.domain.port.repository.TrustedDomainRepositoryPort
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncLocalModelRegistryUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncUtilityModelRegistryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for initializing models at app startup.
 * Fetches remote config, registers expected models in registry, and checks which models need to be downloaded.
 */
class InitializeModelsUseCase
    @Inject
    constructor(
        private val modelConfigFetcher: ModelConfigFetcherPort,
        private val activeModelProvider: ActiveModelProviderPort,
        private val syncLocalModelRegistryUseCase: SyncLocalModelRegistryUseCase,
        private val localModelRepository: com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort,
        private val modelDownloadOrchestrator: ModelDownloadOrchestratorPort,
        private val checkModelsUseCase: CheckModelsUseCase,
        private val syncUtilityModelRegistryUseCase: SyncUtilityModelRegistryUseCase,
        private val trustedDomainRepository: TrustedDomainRepositoryPort,
        private val gpuCapabilityPort: GpuCapabilityPort,
        private val capabilityPort: LocalImageGenerationCapabilityPort,
        private val resolveSd15QnnPackCatalogAssetUseCase: ResolveSd15QnnPackCatalogAssetUseCase,
        @param:ApplicationScope private val externalScope: CoroutineScope,
        private val logPort: LoggingPort,
    ) {
        companion object {
            private const val TAG = "InitializeModelsUseCase"
            private const val SDXL_PACK_ID = "sdxl-t2i"
        }

        /**
         * Executes the model initialization logic.
         * @return DownloadModelsResult containing which models need to be downloaded
         */
        suspend operator fun invoke(): DownloadModelsResult =
            withContext(Dispatchers.IO) {
                checkModelsResult()
            }

        /**
         * Returns the full DownloadModelsResult including scan result and models to download.
         * This allows passing scan results downstream to avoid duplicate scanning.
         */
        private suspend fun checkModelsResult(): DownloadModelsResult {
            val localImageGenerationSupported = gpuCapabilityPort.isLocalImageGenerationSupported()

            // Sync trusted domains asynchronously at startup
            externalScope.launch {
                runCatching {
                    trustedDomainRepository.syncTrustedDomains()
                }
            }

            val catalogResult = modelConfigFetcher.fetchParsedCatalog()
            val catalog =
                catalogResult.getOrElse {
                    logPort.error(TAG, "Failed to fetch remote config: ${it.message}")

                    val currentModels =
                        ModelType.entries
                            .associateWith { modelType ->
                                val config = activeModelProvider.getActiveConfiguration(modelType)
                                if (config != null && config.isLocal) {
                                    localModelRepository.getAssetByConfigId(config.id as LocalModelConfigurationId)
                                } else {
                                    null
                                }
                            }.filterValues { it != null }
                            .mapValues { it.value!! }

                    if (currentModels.isEmpty()) throw it

                    val imageGenerationIsApi =
                        activeModelProvider.getActiveConfiguration(ModelType.IMAGE_GENERATION)?.isLocal == false
                    val suppressImageGenerationDownloads =
                        imageGenerationIsApi || !localImageGenerationSupported
                    val registryAssets = localModelRepository.getAllLocalAssets()
                    val fallbackPackShas =
                        ImageGenerationPackMemberFilter.sha256sFromAssets(
                            currentModels.values + registryAssets,
                        )
                    val modelsResult =
                        checkModelsUseCase(expectedModels = currentModels).copy(
                            imageGenerationPackMemberSha256s = fallbackPackShas,
                        )
                    return if (suppressImageGenerationDownloads) {
                        ImageGenerationPackMemberFilter.excludeFromDownloadResult(modelsResult)
                    } else {
                        modelsResult
                    }
                }

            val remoteAssets = catalog.assets
            logPort.debug(TAG, "Fetched ${remoteAssets.size} remote assets")

            val imageGenerationIsApi =
                activeModelProvider.getActiveConfiguration(ModelType.IMAGE_GENERATION)?.isLocal == false
            val suppressImageGenerationDownloads =
                imageGenerationIsApi || !localImageGenerationSupported
            val downloadSlice = filterForDownload(catalog, suppressImageGenerationDownloads)
            val slotAssignments = downloadSlice.slotAssignments
            val utilityAssets =
                augmentSd15QnnPackUtilities(
                    utilities = downloadSlice.utilityAssets,
                    suppressImageGenerationDownloads = suppressImageGenerationDownloads,
                    hasLocalImageGenerationSlot = ModelType.IMAGE_GENERATION in downloadSlice.slotAssignments,
                )

            // Source of truth for soft-deleted models is getSoftDeletedModels()
            val softDeletedAssets = localModelRepository.getSoftDeletedModels()

            if (softDeletedAssets.isNotEmpty()) {
                logPort.debug(
                    TAG,
                    "Found ${softDeletedAssets.size} soft-deleted models available for re-download: ${
                        softDeletedAssets.map { it.metadata.huggingFaceModelName }
                    }",
                )
            }

            // Exclude soft-deleted model types and slots the user has actively configured as API
            // so CheckModelsUseCase doesn't queue them for download.
            val softDeletedShaSet = softDeletedAssets.mapTo(mutableSetOf()) { it.metadata.sha256 }
            val imageGenPackMemberShas = catalog.packMemberSha256sForSlot(ModelType.IMAGE_GENERATION)
            val filteredSlotAssignments =
                buildMap<ModelType, LocalModelAsset> {
                    for ((modelType, asset) in slotAssignments) {
                        val isDeleted = asset.metadata.sha256 in softDeletedShaSet
                        val activeConfig = activeModelProvider.getActiveConfiguration(modelType)
                        val isApi = activeConfig?.isLocal == false
                        if (!isDeleted) {
                            if (isApi) {
                                logPort.debug(TAG, "Skipping local download for $modelType — slot is configured as API")
                            } else {
                                put(modelType, asset)
                            }
                        }
                    }
                }
            val filteredUtilityAssets =
                buildList {
                    addAll(
                        utilityAssets.filter { asset ->
                            val suppressedSd15 =
                                suppressImageGenerationDownloads &&
                                    asset.metadata.utilityType == UtilityType.SD15_QNN_PACK
                            !suppressedSd15 && asset.metadata.sha256 !in softDeletedShaSet
                        },
                    )
                    if (!suppressImageGenerationDownloads) {
                        val packUtilityAssets =
                            remoteAssets.filter { asset ->
                                asset.metadata.utilityType != null && asset.metadata.sha256 in imageGenPackMemberShas
                            }
                        addAll(
                            packUtilityAssets.filter { asset ->
                                asset.metadata.sha256 !in softDeletedShaSet
                            },
                        )
                    }
                }

            val (tierAwareSlotAssignments, tierAwareUtilityAssets) =
                applyTierAwareImagePackDownloadFilter(
                    catalog = catalog,
                    suppressImageGenerationDownloads = suppressImageGenerationDownloads,
                    slotAssignments = filteredSlotAssignments,
                    utilityAssets = filteredUtilityAssets,
                )

            // Check if models are ready using CheckModelsUseCase
            val modelsResult =
                checkModelsUseCase(
                    expectedModels = tierAwareSlotAssignments,
                    utilityAssets = tierAwareUtilityAssets,
                )

            // Include soft-deleted models in availableToRedownload ONLY if they are still on remote.
            val remoteShaMap = remoteAssets.associateBy { it.metadata.sha256 }
            val availableSoftDeleted =
                softDeletedAssets
                    .filter { it.metadata.sha256 in remoteShaMap.keys }
                    .map { asset ->
                        val remoteAsset = remoteShaMap[asset.metadata.sha256]!!
                        asset.copy(
                            metadata =
                                asset.metadata.copy(
                                    source = remoteAsset.metadata.source,
                                ),
                        )
                    }
            val availableToRedownload =
                (modelsResult.availableToRedownload + availableSoftDeleted)
                    .distinctBy { it.metadata.sha256 }

            // Deferred Activation Strategy:
            // 1. If physical file is already valid (not in modelsToDownload): Update registry immediately.
            // 2. If physical file is missing/invalid: DO NOT update registry yet. Activation happens after download success.

            val modelsToDownloadShaSet = modelsResult.modelsToDownload.mapTo(mutableSetOf()) { it.metadata.sha256 }

            slotAssignments.forEach { (modelType, remoteAsset) ->
                if (remoteAsset.metadata.sha256 !in modelsToDownloadShaSet) {
                    // The physical asset is already present locally and valid.
                    // Apply the slot config immediately instead of waiting for a download that will never be scheduled.
                    syncLocalModelRegistryUseCase(modelType, remoteAsset)
                    logPort.debug(TAG, "Applied slot activation for $modelType immediately")
                } else {
                    logPort.debug(TAG, "Deferring registration for $modelType until download success")
                }
            }

            tierAwareUtilityAssets.forEach { utilityAsset ->
                if (utilityAsset.metadata.sha256 !in modelsToDownloadShaSet) {
                    persistUtilityAsset(utilityAsset)
                    logPort.debug(
                        TAG,
                        "Persisted utility ${utilityAsset.metadata.utilityType} at init",
                    )
                } else {
                    logPort.debug(
                        TAG,
                        "Deferring utility ${utilityAsset.metadata.utilityType} until download success",
                    )
                }
            }

            val startupResultWithAvailableModels =
                modelsResult.copy(
                    availableToRedownload = availableToRedownload,
                    imageGenerationPackMemberSha256s = imageGenPackMemberShas,
                )
            val startupResult =
                if (suppressImageGenerationDownloads) {
                    ImageGenerationPackMemberFilter.excludeFromDownloadResult(startupResultWithAvailableModels)
                } else {
                    startupResultWithAvailableModels
                }

            // Initialize the orchestrator only after device/API download gating is applied.
            modelDownloadOrchestrator.initializeWithStartupResult(startupResult)
            return startupResult
        }

        private suspend fun persistUtilityAsset(asset: LocalModelAsset) {
            syncUtilityModelRegistryUseCase(asset)
        }

        private fun applyTierAwareImagePackDownloadFilter(
            catalog: ParsedModelCatalog,
            suppressImageGenerationDownloads: Boolean,
            slotAssignments: Map<ModelType, LocalModelAsset>,
            utilityAssets: List<LocalModelAsset>,
        ): Pair<Map<ModelType, LocalModelAsset>, List<LocalModelAsset>> {
            if (suppressImageGenerationDownloads || catalog.packs.none { it.packId == SDXL_PACK_ID }) {
                return slotAssignments to utilityAssets
            }
            capabilityPort.getCapabilities()
            throw NotImplementedError("TDD Red")
        }

        private fun augmentSd15QnnPackUtilities(
            utilities: List<LocalModelAsset>,
            suppressImageGenerationDownloads: Boolean,
            hasLocalImageGenerationSlot: Boolean,
        ): List<LocalModelAsset> {
            if (suppressImageGenerationDownloads || !hasLocalImageGenerationSlot) {
                return utilities.map(resolveSd15QnnPackCatalogAssetUseCase::invoke)
            }
            val resolved = utilities.map(resolveSd15QnnPackCatalogAssetUseCase::invoke)
            if (resolved.any { it.metadata.utilityType == UtilityType.SD15_QNN_PACK }) {
                return resolved
            }
            return resolved + resolveSd15QnnPackCatalogAssetUseCase.syntheticAsset()
        }
    }
