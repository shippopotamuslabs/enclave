package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.LocalImagePackDownloadPolicy
import com.browntowndev.enclave.domain.model.download.LocalImagePackMemberFilter
import com.browntowndev.enclave.domain.model.download.ParsedModelCatalog
import com.browntowndev.enclave.domain.model.download.filterForDownload
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.enclave.domain.port.download.QnnPackInstallerPort
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
        private val qnnPackInstaller: QnnPackInstallerPort,
        @param:ApplicationScope private val externalScope: CoroutineScope,
        private val logPort: LoggingPort,
    ) {
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

                    return handleCatalogFetchFailure(
                        currentModels = currentModels,
                        localImageGenerationSupported = localImageGenerationSupported,
                    )
                }

            val remoteAssets = catalog.assets
            logPort.debug(TAG, "Fetched ${remoteAssets.size} remote assets")

            ensureInstalledQnnPackUtilities(remoteAssets)

            val suppressImageGenerationSlot =
                activeModelProvider.getActiveConfiguration(ModelType.IMAGE_GENERATION)?.isLocal == false ||
                    !localImageGenerationSupported
            val suppressImageEditSlot =
                activeModelProvider.getActiveConfiguration(ModelType.IMAGE_EDIT)?.isLocal == false ||
                    !localImageGenerationSupported
            val downloadSlice =
                filterForDownload(
                    catalog,
                    suppressImageGenerationSlot = suppressImageGenerationSlot,
                    suppressImageEditSlot = suppressImageEditSlot,
                )
            val slotAssignments = downloadSlice.slotAssignments
            val utilityAssets =
                augmentSd15QnnPackUtilities(
                    utilities = downloadSlice.utilityAssets,
                    suppressImageGenerationDownloads = suppressImageGenerationSlot,
                    hasLocalImageGenerationSlot = ModelType.IMAGE_GENERATION in downloadSlice.slotAssignments,
                )

            val softDeletedAssets = localModelRepository.getSoftDeletedModels()

            if (softDeletedAssets.isNotEmpty()) {
                logPort.debug(
                    TAG,
                    "Found ${softDeletedAssets.size} soft-deleted models available for re-download: ${
                        softDeletedAssets.map { it.metadata.huggingFaceModelName }
                    }",
                )
            }

            val softDeletedShaSet = softDeletedAssets.mapTo(mutableSetOf()) { it.metadata.sha256 }
            val imageGenPackMemberShas = catalog.packMemberSha256sForSlot(ModelType.IMAGE_GENERATION)
            val imageEditPackMemberShas = catalog.packMemberSha256sForSlot(ModelType.IMAGE_EDIT)
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
                                suppressImageGenerationSlot &&
                                    asset.metadata.utilityType == UtilityType.SD15_QNN_PACK
                            val suppressedSdxl =
                                suppressImageGenerationSlot &&
                                    asset.metadata.utilityType == UtilityType.SDXL_QNN_PACK
                            val suppressedJuggernaut =
                                suppressImageEditSlot &&
                                    asset.metadata.utilityType == UtilityType.JUGGERNAUT_XL_QNN_PACK
                            !suppressedSd15 &&
                                !suppressedSdxl &&
                                !suppressedJuggernaut &&
                                asset.metadata.sha256 !in softDeletedShaSet
                        },
                    )
                    if (!suppressImageGenerationSlot) {
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
                    if (!suppressImageEditSlot) {
                        val editPackUtilityAssets =
                            remoteAssets.filter { asset ->
                                asset.metadata.utilityType != null && asset.metadata.sha256 in imageEditPackMemberShas
                            }
                        addAll(
                            editPackUtilityAssets.filter { asset ->
                                asset.metadata.sha256 !in softDeletedShaSet
                            },
                        )
                    }
                }

            val capabilities = capabilityPort.getCapabilities()
            val (tierAwareSlotAssignments, tierAwareUtilityAssets) =
                LocalImagePackDownloadPolicy.applyTierAwareFilter(
                    catalog = catalog,
                    capabilities = capabilities,
                    slotAssignments = filteredSlotAssignments,
                    utilityAssets = filteredUtilityAssets,
                )

            val modelsResult =
                checkModelsUseCase(
                    expectedModels = tierAwareSlotAssignments,
                    utilityAssets = tierAwareUtilityAssets,
                )

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

            val modelsToDownloadShaSet = modelsResult.modelsToDownload.mapTo(mutableSetOf()) { it.metadata.sha256 }

            tierAwareSlotAssignments.forEach { (modelType, remoteAsset) ->
                if (remoteAsset.metadata.sha256 !in modelsToDownloadShaSet) {
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
                    imageEditPackMemberSha256s = imageEditPackMemberShas,
                )
            val startupResult =
                LocalImagePackDownloadPolicy.applySlotDownloadSuppression(
                    result = startupResultWithAvailableModels,
                    suppressImageGenerationSlot = suppressImageGenerationSlot,
                    suppressImageEditSlot = suppressImageEditSlot,
                )

            modelDownloadOrchestrator.initializeWithStartupResult(startupResult)
            return startupResult
        }

        private suspend fun persistUtilityAsset(asset: LocalModelAsset) {
            syncUtilityModelRegistryUseCase(asset)
        }

        private suspend fun ensureInstalledQnnPackUtilities(assets: List<LocalModelAsset>) {
            val eligibleUtilities =
                LocalImagePackDownloadPolicy.eligibleQnnUtilityTypes(capabilityPort.getCapabilities())
            assets
                .filter { asset -> asset.metadata.utilityType in eligibleUtilities }
                .forEach { asset ->
                    runCatching {
                        if (qnnPackInstaller.installIfNeeded(asset.metadata)) {
                            logPort.debug(
                                TAG,
                                "Ensured QNN pack extracted for ${asset.metadata.utilityType}",
                            )
                        }
                    }.onFailure { error ->
                        logPort.warning(
                            TAG,
                            "QNN pack install attempt failed for ${asset.metadata.utilityType}: ${error.message}",
                        )
                    }
                }
        }

        private suspend fun handleCatalogFetchFailure(
            currentModels: Map<ModelType, LocalModelAsset>,
            localImageGenerationSupported: Boolean,
        ): DownloadModelsResult {
            val suppressImageGenerationSlot =
                activeModelProvider.getActiveConfiguration(ModelType.IMAGE_GENERATION)?.isLocal == false ||
                    !localImageGenerationSupported
            val suppressImageEditSlot =
                activeModelProvider.getActiveConfiguration(ModelType.IMAGE_EDIT)?.isLocal == false ||
                    !localImageGenerationSupported
            val registryAssets = localModelRepository.getAllLocalAssets()
            val fallbackImageGenShas =
                LocalImagePackMemberFilter.sha256sFromAssets(
                    currentModels[ModelType.IMAGE_GENERATION]?.let { listOf(it) }.orEmpty() +
                        registryAssets.filter { asset ->
                            asset.configurations.any { ModelType.IMAGE_GENERATION in it.defaultAssignments }
                        },
                )
            val fallbackImageEditShas =
                LocalImagePackMemberFilter.sha256sFromAssets(
                    currentModels[ModelType.IMAGE_EDIT]?.let { listOf(it) }.orEmpty() +
                        registryAssets.filter { asset ->
                            asset.configurations.any { ModelType.IMAGE_EDIT in it.defaultAssignments }
                        },
                )
            val modelsResult =
                checkModelsUseCase(expectedModels = currentModels).copy(
                    imageGenerationPackMemberSha256s = fallbackImageGenShas,
                    imageEditPackMemberSha256s = fallbackImageEditShas,
                )
            return LocalImagePackDownloadPolicy.applySlotDownloadSuppression(
                result = modelsResult,
                suppressImageGenerationSlot = suppressImageGenerationSlot,
                suppressImageEditSlot = suppressImageEditSlot,
            )
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

        private companion object {
            private const val TAG = "InitializeModelsUseCase"
        }
    }
