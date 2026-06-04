package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncLocalModelRegistryUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncUtilityModelRegistryUseCase
import com.browntowndev.enclave.domain.port.repository.TrustedDomainRepositoryPort
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
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
        @param:ApplicationScope private val externalScope: CoroutineScope,
        private val logPort: LoggingPort,
    ) {
        companion object {
            private const val TAG = "InitializeModelsUseCase"
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
            // Sync trusted domains asynchronously at startup
            externalScope.launch {
                runCatching {
                    trustedDomainRepository.syncTrustedDomains()
                }
            }

            // Fetch remote config
            val remoteConfigResult = modelConfigFetcher.fetchRemoteConfig()
            val remoteAssets =
                remoteConfigResult.getOrElse {
                    logPort.error(TAG, "Failed to fetch remote config: ${it.message}")

                    // If config fetch error occurs, fallback to using current models
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

                    // If there are no current models, throw the error
                    if (currentModels.isEmpty()) throw it

                    // Return a minimal result with current models
                    return checkModelsUseCase(expectedModels = currentModels)
                }

            logPort.debug(TAG, "Fetched ${remoteAssets.size} remote assets")

            // Build slot-to-asset map from defaultAssignments
            val slotAssignments = mutableMapOf<ModelType, LocalModelAsset>()
            val utilityAssets = mutableListOf<LocalModelAsset>()
            for (asset in remoteAssets) {
                if (asset.metadata.utilityType != null) {
                    utilityAssets += asset
                } else {
                    for (config in asset.configurations) {
                        for (modelType in config.defaultAssignments) {
                            slotAssignments[modelType] = asset
                        }
                    }
                }
            }

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
                utilityAssets.filter { asset ->
                    asset.metadata.sha256 !in softDeletedShaSet
                }

            // Check if models are ready using CheckModelsUseCase
            val modelsResult =
                checkModelsUseCase(
                    expectedModels = filteredSlotAssignments,
                    utilityAssets = filteredUtilityAssets,
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

            filteredUtilityAssets.forEach { utilityAsset ->
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

            // Initialize the orchestrator with the startup result
            modelDownloadOrchestrator.initializeWithStartupResult(modelsResult)

            // Return result with soft-deleted models included in availableToRedownload
            return modelsResult.copy(availableToRedownload = availableToRedownload)
        }

        private suspend fun persistUtilityAsset(asset: LocalModelAsset) {
            syncUtilityModelRegistryUseCase(asset)
        }
    }
