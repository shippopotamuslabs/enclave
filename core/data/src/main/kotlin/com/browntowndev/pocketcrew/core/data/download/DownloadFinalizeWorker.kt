package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import androidx.annotation.Keep
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.download.DownloadRequestKind
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.SyncLocalModelRegistryUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
class DownloadFinalizeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val localModelRepository: LocalModelRepositoryPort,
    private val modelConfigFetcher: ModelConfigFetcherPort,
    private val syncLocalModelRegistryUseCase: SyncLocalModelRegistryUseCase,
    private val logger: LoggingPort
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DownloadFinalizeWorker"

        // Request kinds (matching domain model)
        const val REQUEST_INITIALIZE = "INITIALIZE_MODELS"
        const val REQUEST_RESTORE = "RESTORE_SOFT_DELETED_MODEL"
    }

    override suspend fun doWork(): Result {
        return try {
            // Emit stage in progress data so the repository can identify
            // this worker as the finalizer even while RUNNING.
            // Best-effort: progress reporting may not work in all environments
            // (e.g., unit tests), but must not prevent finalization.
            runCatching {
                setProgress(workDataOf(
                    DownloadWorkKeys.KEY_WORKER_STAGE to DownloadWorkKeys.STAGE_FINALIZE
                ))
            }

            // ===== Parse Required Input =====
            val sessionId = inputData.getString(DownloadWorkKeys.KEY_SESSION_ID)
                ?: return failWithError("Missing required input: $DownloadWorkKeys.KEY_SESSION_ID")

            val requestKind = inputData.getString(DownloadWorkKeys.KEY_REQUEST_KIND)
                ?: return failWithError("Missing required input: $DownloadWorkKeys.KEY_REQUEST_KIND", sessionId, "")

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
            Result.retry()
        }
    }

    /**
     * Handles INITIALIZE_MODELS request:
     * - Fetches remote config
     * - Activates downloaded slots by matching SHA
     */
    private suspend fun handleInitializeModels(
        sessionId: String,
        requestKind: String,
        downloadedShas: List<String>
    ): Result {
        return try {
            // Fetch remote config to get asset information
            val remoteConfigResult = modelConfigFetcher.fetchRemoteConfig()
            if (remoteConfigResult.isFailure) {
                val error = remoteConfigResult.exceptionOrNull()?.message ?: "Failed to fetch remote config"
                logger.warning(TAG, error)
                return Result.retry() // Retry on network failure
            }

            val remoteConfig = remoteConfigResult.getOrNull() ?: emptyList()

            // Find assets whose SHA matches downloaded SHAs and resolve slot assignments
            val matchingAssets = remoteConfig.filter { asset ->
                asset.metadata.sha256 in downloadedShas
            }

            logger.info(TAG, "Found ${matchingAssets.size} matching assets for ${downloadedShas.size} downloaded SHAs")

            // Sync each matching asset's configurations to their assigned slots
            for (asset in matchingAssets) {
                for (config in asset.configurations) {
                    for (modelType in config.defaultAssignments) {
                        try {
                            syncLocalModelRegistryUseCase.invoke(modelType, asset)
                            logger.debug(TAG, "Synced $modelType: ${asset.metadata.localFileName}")
                        } catch (e: Exception) {
                            logger.warning(TAG, "Failed to sync $modelType: ${e.message}")
                            // Continue with other assets - idempotent behavior
                        }
                    }
                }
            }

            // Success output
            Result.success(
                workDataOf(
                    DownloadWorkKeys.KEY_SESSION_ID to sessionId,
                    DownloadWorkKeys.KEY_REQUEST_KIND to requestKind,
                    DownloadWorkKeys.KEY_WORKER_STAGE to DownloadWorkKeys.STAGE_FINALIZE
                )
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
        downloadedShas: List<String>
    ): Result {
        // targetModelId is required for restore
        val targetModelIdStr = inputData.getString(DownloadWorkKeys.KEY_TARGET_MODEL_ID)
            ?: return failWithError(
                "Missing required input for restore: $DownloadWorkKeys.KEY_TARGET_MODEL_ID",
                sessionId,
                requestKind
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
                return Result.retry() // Retry on network failure
            }

            val remoteConfig = remoteConfigResult.getOrNull() ?: emptyList()

            // Find assets whose SHA matches downloaded SHAs
            val matchingAssets = remoteConfig.filter { asset ->
                asset.metadata.sha256 in downloadedShas
            }

            // If the soft-deleted asset's SHA is in the matching assets, use its configurations
            val assetToRestore = matchingAssets.find {
                it.metadata.sha256 == softDeletedAsset.metadata.sha256
            } ?: softDeletedAsset

            // Rebuild system presets with the target model ID.
            // Room auto-generates the ID on insert since we pass an empty-string ID here.
            val rebuiltConfigs = assetToRestore.configurations
                .filter { it.isSystemPreset }
                .map { config ->
                    config.copy(
                        id = com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId(""),
                        localModelId = targetModelId
                    )
                }

            if (rebuiltConfigs.isEmpty()) {
                logger.warning(TAG, "No system presets to restore for $targetModelIdStr")
                // Still return success - nothing to restore
                return Result.success(
                    workDataOf(
                        DownloadWorkKeys.KEY_SESSION_ID to sessionId,
                        DownloadWorkKeys.KEY_REQUEST_KIND to requestKind,
                        DownloadWorkKeys.KEY_WORKER_STAGE to DownloadWorkKeys.STAGE_FINALIZE
                    )
                )
            }

            logger.info(TAG, "Restoring ${rebuiltConfigs.size} system presets for $targetModelIdStr")

            // Restore the configurations
            localModelRepository.restoreSoftDeletedModel(targetModelId, rebuiltConfigs)

            // Success output (no SyncLocalModelRegistryUseCase for restore)
            Result.success(
                workDataOf(
                    DownloadWorkKeys.KEY_SESSION_ID to sessionId,
                    DownloadWorkKeys.KEY_REQUEST_KIND to requestKind,
                    DownloadWorkKeys.KEY_WORKER_STAGE to DownloadWorkKeys.STAGE_FINALIZE
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "Restore failed: ${e.message}", e)
            failWithError("Restore failed: ${e.message}", sessionId, requestKind)
        }
    }

    /**
     * Parses JSON array of SHA256 hashes.
     */
    private fun parseShas(json: String): List<String> {
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            logger.warning(TAG, "Failed to parse SHAs JSON: ${e.message}")
            emptyList()
        }
    }

    /**
     * Returns a failure result with standard metadata.
     */
    private fun failWithError(
        errorMessage: String,
        sessionId: String = "",
        requestKind: String = ""
    ): Result {
        return Result.failure(
            workDataOf(
                DownloadWorkKeys.KEY_ERROR_MESSAGE to errorMessage,
                DownloadWorkKeys.KEY_SESSION_ID to sessionId,
                DownloadWorkKeys.KEY_REQUEST_KIND to requestKind,
                DownloadWorkKeys.KEY_WORKER_STAGE to DownloadWorkKeys.STAGE_FINALIZE
            )
        )
    }
}
