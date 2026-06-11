package com.browntowndev.enclave.core.data.download

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import androidx.annotation.Keep
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.browntowndev.enclave.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.enclave.core.data.util.formatBytes
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.JuggernautXlQnnPackCatalog
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.download.Sd15QnnPackCatalog
import com.browntowndev.enclave.domain.model.download.SdxlQnnPackCatalog
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import com.browntowndev.enclave.domain.port.download.QnnPackInstallProgressListener
import com.browntowndev.enclave.domain.port.download.QnnPackInstallerPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.download.ResolveSd15QnnPackCatalogAssetUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncLocalModelRegistryUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.SyncUtilityModelRegistryUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Finalizes download work by performing post-download business logic.
 * This worker is the background-owned owner of registry operations.
 *
 * Input keys:
 * - DownloadWorkKeys.KEY_SESSION_ID: Session identifier (required)
 * - DownloadWorkKeys.KEY_REQUEST_KIND: INITIALIZE_MODELS or RESTORE_SOFT_DELETED_MODEL (required)
 * - DownloadWorkKeys.KEY_DOWNLOADED_SHAS: JSON array of downloaded SHA256 hashes (required)
 * - DownloadWorkKeys.KEY_TARGET_MODEL_ID: Model ID for restore requests (required for RESTORE_SOFT_DELETED_MODEL)
 *
 * Output keys:
 * - DownloadWorkKeys.KEY_SESSION_ID: Pass-through session identifier
 * - DownloadWorkKeys.KEY_REQUEST_KIND: Pass-through request kind
 * - DownloadWorkKeys.KEY_WORKER_STAGE: Always "FINALIZE"
 * - DownloadWorkKeys.KEY_ERROR_MESSAGE: Error message if failed (optional)
 */
@Keep
@HiltWorker
class DownloadFinalizeWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val localModelRepository: LocalModelRepositoryPort,
        private val modelConfigFetcher: ModelConfigFetcherPort,
        private val syncLocalModelRegistryUseCase: SyncLocalModelRegistryUseCase,
        private val syncUtilityModelRegistryUseCase: SyncUtilityModelRegistryUseCase,
        private val setDefaultModelUseCase: SetDefaultModelUseCase,
        private val defaultModelRepository: DefaultModelRepositoryPort,
        private val qnnPackInstaller: QnnPackInstallerPort,
        private val resolveSd15QnnPackCatalogAssetUseCase: ResolveSd15QnnPackCatalogAssetUseCase,
        private val embeddingCoordinator: EmbeddingRegenerationCoordinator,
        private val notificationManager: DownloadNotificationManager,
        private val logger: LoggingPort,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val TAG = "DownloadFinalizeWorker"
            private const val NOTIFICATION_TITLE = "Installing Models"
            private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L

            // Request kinds (matching domain model)
            const val REQUEST_INITIALIZE = "INITIALIZE_MODELS"
            const val REQUEST_RESTORE = "RESTORE_SOFT_DELETED_MODEL"
        }

        private var lastNotificationUpdateMs = 0L

        override suspend fun doWork(): Result {
            // Run as a foreground service so the multi-minute QNN pack extraction
            // survives OEM background restrictions (e.g. "SmartBackground") that
            // otherwise kill the process mid-unzip. Mirrors ModelDownloadWorker.
            try {
                notificationManager.createNotificationChannel()
                val cancelIntent =
                    WorkManager
                        .getInstance(applicationContext)
                        .createCancelPendingIntent(id)
                setForeground(
                    notificationManager.createForegroundInfo(
                        cancelPendingIntent = cancelIntent,
                        title = NOTIFICATION_TITLE,
                    ),
                )
            } catch (e: ForegroundServiceStartNotAllowedException) {
                logger.warning(TAG, "Foreground not allowed (app not in foreground), returning retry: ${e.message}")
                return Result.retry()
            } catch (e: Exception) {
                // Best-effort: foreground promotion may fail in restricted or test
                // environments, but finalization must still be attempted.
                logger.warning(TAG, "Failed to promote finalize worker to foreground: ${e.message}")
            }

            return try {
                // Emit stage in progress data so the repository can identify
                // this worker as the finalizer even while RUNNING.
                // Best-effort: progress reporting may not work in all environments
                // (e.g., unit tests), but must not prevent finalization.
                runCatching {
                    setProgress(
                        workDataOf(
                            DownloadWorkKeys.KEY_WORKER_STAGE to DownloadWorkKeys.STAGE_FINALIZE,
                        ),
                    )
                }

                // ===== Parse Required Input =====
                val sessionId =
                    inputData.getString(DownloadWorkKeys.KEY_SESSION_ID)
                        ?: return failWithError("Missing required input: ${DownloadWorkKeys.KEY_SESSION_ID}")

                val requestKind =
                    inputData.getString(DownloadWorkKeys.KEY_REQUEST_KIND)
                        ?: return failWithError("Missing required input: ${DownloadWorkKeys.KEY_REQUEST_KIND}", sessionId, "")

                val downloadedShasJson = inputData.getString(DownloadWorkKeys.KEY_DOWNLOADED_SHAS) ?: "[]"
                val downloadedShas = parseShas(downloadedShasJson)

                logger.info(TAG, "Finalizing download: sessionId=$sessionId, requestKind=$requestKind, shas=${downloadedShas.size}")

                // ===== Route by Request Kind =====
                when (requestKind) {
                    REQUEST_INITIALIZE -> handleInitializeModels(sessionId, requestKind, downloadedShas)
                    REQUEST_RESTORE -> handleRestoreSoftDeletedModel(sessionId, requestKind, downloadedShas)
                    else -> failWithError("Unknown request kind: $requestKind", sessionId, requestKind)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(TAG, "Finalization error: ${e.message}", e)
                if (runAttemptCount >= ModelConfig.MAX_RETRY_ATTEMPTS) {
                    failWithError(
                        "Finalization failed after ${runAttemptCount + 1} attempts: ${e.message}",
                    )
                } else {
                    Result.retry()
                }
            }
        }

        private fun shouldRetry(): Boolean = runAttemptCount < ModelConfig.MAX_RETRY_ATTEMPTS

        /**
         * Handles INITIALIZE_MODELS request:
         * - Fetches remote config
         * - Activates downloaded slots by matching SHA
         */
        private suspend fun handleInitializeModels(
            sessionId: String,
            requestKind: String,
            downloadedShas: List<String>,
        ): Result {
            return try {
                // Fetch remote config to get asset information
                val remoteConfigResult = modelConfigFetcher.fetchRemoteConfig()
                if (remoteConfigResult.isFailure) {
                    val error = remoteConfigResult.exceptionOrNull()?.message ?: "Failed to fetch remote config"
                    logger.warning(TAG, error)
                    return if (shouldRetry()) {
                        Result.retry()
                    } else {
                        failWithError(
                            "Failed to fetch remote config after ${runAttemptCount + 1} attempts: $error",
                            sessionId,
                            requestKind,
                        )
                    }
                }

                val remoteConfig = remoteConfigResult.getOrNull() ?: emptyList()

                val matchingAssets = resolveMatchingAssets(remoteConfig, downloadedShas)

                logger.info(TAG, "Found ${matchingAssets.size} matching assets for ${downloadedShas.size} downloaded SHAs")

                syncMatchingAssets(matchingAssets, downloadedShas)

                // Trigger embedding regeneration if any embedding models were finalized
                val hasEmbeddingModel =
                    matchingAssets.any {
                        it.metadata.utilityType == UtilityType.ONNX_MODEL || it.metadata.utilityType == UtilityType.ONNX_VOCAB
                    }
                if (hasEmbeddingModel) {
                    logger.info(TAG, "Embedding model finalized, triggering regeneration")
                    embeddingCoordinator.enqueueFullRegeneration()
                }

                // Success output
                Result.success(
                    workDataOf(
                        DownloadWorkKeys.KEY_SESSION_ID to sessionId,
                        DownloadWorkKeys.KEY_REQUEST_KIND to requestKind,
                        DownloadWorkKeys.KEY_WORKER_STAGE to DownloadWorkKeys.STAGE_FINALIZE,
                    ),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(TAG, "Initialize models failed: ${e.message}", e)
                failWithError("Initialization failed: ${e.message}", sessionId, requestKind)
            }
        }

        /**
         * Handles RESTORE_SOFT_DELETED_MODEL request:
         * - Requires targetModelId
         * - Gets soft-deleted asset from repository
         * - Rebuilds system presets from remote config
         * - Restores configs to repository
         */
        private suspend fun handleRestoreSoftDeletedModel(
            sessionId: String,
            requestKind: String,
            downloadedShas: List<String>,
        ): Result {
            // targetModelId is required for restore
            val targetModelIdStr =
                inputData.getString(DownloadWorkKeys.KEY_TARGET_MODEL_ID)
                    ?: return failWithError(
                        "Missing required input for restore: $DownloadWorkKeys.KEY_TARGET_MODEL_ID",
                        sessionId,
                        requestKind,
                    )

            val targetModelId = LocalModelId(targetModelIdStr)

            return try {
                // Get the soft-deleted asset by ID
                val softDeletedAsset = localModelRepository.getAssetById(targetModelId)
                if (softDeletedAsset == null) {
                    logger.error(TAG, "Soft-deleted asset not found: $targetModelIdStr")
                    return failWithError("Asset not found: $targetModelIdStr", sessionId, requestKind)
                }

                logger.info(TAG, "Found soft-deleted asset: ${softDeletedAsset.metadata.localFileName}")

                // Fetch remote config to get preset configurations
                val remoteConfigResult = modelConfigFetcher.fetchRemoteConfig()
                if (remoteConfigResult.isFailure) {
                    val error = remoteConfigResult.exceptionOrNull()?.message ?: "Failed to fetch remote config"
                    logger.warning(TAG, error)
                    return if (shouldRetry()) {
                        Result.retry()
                    } else {
                        failWithError(
                            "Failed to fetch remote config after ${runAttemptCount + 1} attempts: $error",
                            sessionId,
                            requestKind,
                        )
                    }
                }

                val remoteConfig = remoteConfigResult.getOrNull() ?: emptyList()

                // Find assets whose SHA matches downloaded SHAs
                val matchingAssets =
                    remoteConfig.filter { asset ->
                        asset.metadata.sha256 in downloadedShas
                    }

                // If the soft-deleted asset's SHA is in the matching assets, use its configurations
                val assetToRestore =
                    matchingAssets.find {
                        it.metadata.sha256 == softDeletedAsset.metadata.sha256
                    } ?: softDeletedAsset

                // Rebuild system presets with the target model ID.
                // Room auto-generates the ID on insert since we pass an empty-string ID here.
                val rebuiltConfigs =
                    assetToRestore.configurations
                        .filter { it.isSystemPreset }
                        .map { config ->
                            config.copy(
                                id =
                                    com.browntowndev.enclave.domain.model.config
                                        .LocalModelConfigurationId(""),
                                localModelId = targetModelId,
                            )
                        }

                if (rebuiltConfigs.isEmpty()) {
                    logger.warning(TAG, "No system presets to restore for $targetModelIdStr")
                    // Still return success - nothing to restore
                    return Result.success(
                        workDataOf(
                            DownloadWorkKeys.KEY_SESSION_ID to sessionId,
                            DownloadWorkKeys.KEY_REQUEST_KIND to requestKind,
                            DownloadWorkKeys.KEY_WORKER_STAGE to DownloadWorkKeys.STAGE_FINALIZE,
                        ),
                    )
                }

                logger.info(TAG, "Restoring ${rebuiltConfigs.size} system presets for $targetModelIdStr")

                // Restore the configurations
                localModelRepository.restoreSoftDeletedModel(targetModelId, rebuiltConfigs)

                // Trigger embedding regeneration if the restored asset is an embedding model
                if (assetToRestore.metadata.utilityType == UtilityType.ONNX_MODEL ||
                    assetToRestore.metadata.utilityType == UtilityType.ONNX_VOCAB
                ) {
                    logger.info(TAG, "Embedding model restored, triggering regeneration")
                    embeddingCoordinator.enqueueFullRegeneration()
                }

                // Success output (no SyncLocalModelRegistryUseCase for restore)
                Result.success(
                    workDataOf(
                        DownloadWorkKeys.KEY_SESSION_ID to sessionId,
                        DownloadWorkKeys.KEY_REQUEST_KIND to requestKind,
                        DownloadWorkKeys.KEY_WORKER_STAGE to DownloadWorkKeys.STAGE_FINALIZE,
                    ),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(TAG, "Restore failed: ${e.message}", e)
                failWithError("Restore failed: ${e.message}", sessionId, requestKind)
            }
        }

        private fun resolveMatchingAssets(
            remoteConfig: List<LocalModelAsset>,
            downloadedShas: List<String>,
        ): List<LocalModelAsset> =
            remoteConfig
                .filter { asset ->
                    asset.metadata.sha256 in downloadedShas ||
                        (
                            asset.metadata.utilityType == UtilityType.SD15_QNN_PACK &&
                                downloadedShas.any { sha -> Sd15QnnPackCatalog.findBySha256(sha) != null }
                        ) ||
                        (
                            asset.metadata.utilityType == UtilityType.SDXL_QNN_PACK &&
                                downloadedShas.any { sha -> SdxlQnnPackCatalog.findBySha256(sha) != null }
                        ) ||
                        (
                            asset.metadata.utilityType == UtilityType.JUGGERNAUT_XL_QNN_PACK &&
                                downloadedShas.any { sha -> JuggernautXlQnnPackCatalog.findBySha256(sha) != null }
                        )
                }.map { asset ->
                    when (asset.metadata.utilityType) {
                        UtilityType.SD15_QNN_PACK -> {
                            val variant =
                                downloadedShas.firstNotNullOfOrNull { sha ->
                                    Sd15QnnPackCatalog.findBySha256(sha)
                                }
                            if (variant != null) {
                                resolveSd15QnnPackCatalogAssetUseCase.applyVariant(asset, variant)
                            } else {
                                asset
                            }
                        }
                        else -> asset
                    }
                }

        private suspend fun syncMatchingAssets(
            matchingAssets: List<LocalModelAsset>,
            downloadedShas: List<String>,
        ) {
            // Utilities install first so the QNN pack is on disk before any slot assignment
            // validates pack completeness. Track utility and slot SHAs separately: a slot
            // (config) asset legitimately shares its SHA with the QNN pack utility asset
            // (same zip, e.g. Juggernaut/SDXL), so a processed utility SHA must not suppress
            // the matching slot assignment.
            val processedUtilityShas = mutableSetOf<String>()
            for (asset in matchingAssets) {
                if (asset.metadata.utilityType != null) {
                    if (asset.metadata.sha256 in processedUtilityShas) continue
                    syncUtilityAsset(asset, downloadedShas)
                    processedUtilityShas += asset.metadata.sha256
                }
            }
            val processedSlotShas = mutableSetOf<String>()
            for (asset in matchingAssets) {
                if (asset.metadata.utilityType == null && asset.metadata.sha256 !in processedSlotShas) {
                    syncSlotAsset(asset)
                    processedSlotShas += asset.metadata.sha256
                }
            }
        }

        private suspend fun syncUtilityAsset(
            asset: LocalModelAsset,
            downloadedShas: List<String>,
        ) {
            try {
                when (asset.metadata.utilityType) {
                    UtilityType.SD15_QNN_PACK,
                    UtilityType.SDXL_QNN_PACK,
                    UtilityType.JUGGERNAUT_XL_QNN_PACK,
                    -> {
                        val requiresInstall = asset.metadata.sha256 in downloadedShas
                        val installed =
                            qnnPackInstaller.installIfNeeded(
                                asset.metadata,
                                progressListener = qnnPackProgressListener(asset.metadata.displayName),
                            )
                        if (requiresInstall && !installed) {
                            val modelsDir = File(applicationContext.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
                            val zipOnDisk = File(modelsDir, asset.metadata.localFileName).isFile
                            if (!zipOnDisk) {
                                logger.warning(
                                    TAG,
                                    "QNN pack zip missing for ${asset.metadata.localFileName}; " +
                                        "skipping registry sync so startup can re-queue download",
                                )
                                return
                            }
                            error("QNN pack install failed for ${asset.metadata.localFileName}")
                        }
                        if (!installed) {
                            logger.warning(TAG, "QNN pack was not installed; skipping utility registry sync")
                            return
                        }
                    }
                    else -> Unit
                }
                persistUtilityAsset(asset)
            } catch (e: Exception) {
                logger.warning(
                    TAG,
                    "Failed to sync utility ${asset.metadata.utilityType}: ${e.message}",
                )
                if (
                    asset.metadata.utilityType in
                    setOf(
                        UtilityType.SD15_QNN_PACK,
                        UtilityType.SDXL_QNN_PACK,
                        UtilityType.JUGGERNAUT_XL_QNN_PACK,
                    ) &&
                    asset.metadata.sha256 in downloadedShas
                ) {
                    throw e
                }
            }
        }

        private fun qnnPackProgressListener(displayName: String): QnnPackInstallProgressListener =
            QnnPackInstallProgressListener { bytesExtracted, totalBytes ->
                runCatching {
                    setProgress(
                        workDataOf(
                            DownloadWorkKeys.KEY_WORKER_STAGE to DownloadWorkKeys.STAGE_FINALIZE,
                            com.browntowndev.enclave.domain.model.download.DownloadKey.POST_PROCESS_BYTES.key to bytesExtracted,
                            com.browntowndev.enclave.domain.model.download.DownloadKey.POST_PROCESS_TOTAL.key to totalBytes,
                        ),
                    )
                }
                updateExtractionNotification(displayName, bytesExtracted, totalBytes)
            }

        /**
         * Updates the foreground notification with extraction progress, throttled so
         * the 8MB-interval installer callbacks don't spam the notification manager.
         * Best-effort: notification failures must never abort extraction.
         */
        private fun updateExtractionNotification(
            displayName: String,
            bytesExtracted: Long,
            totalBytes: Long,
        ) {
            val now = System.currentTimeMillis()
            val isComplete = totalBytes > 0 && bytesExtracted >= totalBytes
            if (!isComplete && now - lastNotificationUpdateMs < NOTIFICATION_UPDATE_INTERVAL_MS) return
            lastNotificationUpdateMs = now
            runCatching {
                val cancelIntent =
                    WorkManager
                        .getInstance(applicationContext)
                        .createCancelPendingIntent(id)
                val progress =
                    if (totalBytes > 0) {
                        (bytesExtracted.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                setForegroundAsync(
                    notificationManager.createForegroundInfoForProgress(
                        progress = progress,
                        currentFile = "Unzipping $displayName",
                        subText = "${formatBytes(bytesExtracted)} / ${formatBytes(totalBytes)}",
                        cancelPendingIntent = cancelIntent,
                        title = NOTIFICATION_TITLE,
                    ),
                )
            }
        }

        private suspend fun syncSlotAsset(asset: LocalModelAsset) {
            for (config in asset.configurations) {
                for (modelType in config.defaultAssignments) {
                    try {
                        val resolved =
                            syncLocalModelRegistryUseCase.invoke(
                                modelType,
                                asset,
                                assignAsDefault = false,
                            )
                        if (shouldAssignDefaultForSlot(modelType, asset)) {
                            runCatching {
                                setDefaultModelUseCase(
                                    modelType = modelType,
                                    localConfigId = resolved.selectedConfig.id,
                                    apiConfigId = null,
                                )
                            }.onFailure { error ->
                                logger.warning(
                                    TAG,
                                    "Skipped default assignment for $modelType: ${error.message}",
                                )
                            }
                        }
                        logger.debug(TAG, "Synced $modelType: ${asset.metadata.localFileName}")
                    } catch (e: Exception) {
                        logger.warning(TAG, "Failed to sync $modelType: ${e.message}")
                    }
                }
            }
        }

        /**
         * Empty slots are always filled. The IMAGE_GENERATION slot additionally lets a
         * higher-priority local model supersede a lower-priority auto-assigned default, so
         * SDXL wins over SD1.5 regardless of which pack finalizes first. Other slots and
         * non-local assignments are never overwritten here.
         */
        private suspend fun shouldAssignDefaultForSlot(
            modelType: ModelType,
            incomingAsset: LocalModelAsset,
        ): Boolean {
            val existing = defaultModelRepository.getDefault(modelType) ?: return true
            if (modelType != ModelType.IMAGE_GENERATION) {
                return false
            }
            val existingConfigId = existing.localConfigId ?: return false
            val existingModelId =
                localModelRepository
                    .getAssetByConfigId(existingConfigId)
                    ?.let { LocalImageGenerationModelRegistry.descriptorFor(it)?.id }
            val incomingModelId = LocalImageGenerationModelRegistry.descriptorFor(incomingAsset)?.id
            return LocalImageGenerationModelRegistry.imageGenerationSlotPriority(incomingModelId) >
                LocalImageGenerationModelRegistry.imageGenerationSlotPriority(existingModelId)
        }

        private suspend fun persistUtilityAsset(asset: LocalModelAsset) {
            syncUtilityModelRegistryUseCase.invoke(asset)
            val utilityType = asset.metadata.utilityType
            logger.debug(TAG, "Synced utility $utilityType: ${asset.metadata.localFileName}")
        }

        /**
         * Parses JSON array of SHA256 hashes.
         */
        private fun parseShas(json: String): List<String> =
            try {
                Json.decodeFromString(json)
            } catch (e: Exception) {
                logger.warning(TAG, "Failed to parse SHAs JSON: ${e.message}")
                emptyList()
            }

        /**
         * Returns a failure result with standard metadata.
         */
        private fun failWithError(
            errorMessage: String,
            sessionId: String = "",
            requestKind: String = "",
        ): Result =
            Result.failure(
                workDataOf(
                    DownloadWorkKeys.KEY_ERROR_MESSAGE to errorMessage,
                    DownloadWorkKeys.KEY_SESSION_ID to sessionId,
                    DownloadWorkKeys.KEY_REQUEST_KIND to requestKind,
                    DownloadWorkKeys.KEY_WORKER_STAGE to DownloadWorkKeys.STAGE_FINALIZE,
                ),
            )
    }
