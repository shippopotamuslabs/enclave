package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.download.DownloadFileSpec
import com.browntowndev.pocketcrew.domain.model.download.DownloadRequestKind
import com.browntowndev.pocketcrew.domain.model.download.DownloadWorkRequest
import com.browntowndev.pocketcrew.domain.model.download.ScheduledDownload
import com.browntowndev.pocketcrew.domain.port.download.DownloadWorkSchedulerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import java.util.UUID
import javax.inject.Inject

/**
 * Use case to re-download a soft-deleted local model.
 *
 * This use case schedules the download pipeline and returns session metadata.
 * Repository restoration is handled by DownloadFinalizeWorker AFTER bytes land,
 * ensuring the database is never in an inconsistent state where the model is
 * "restored" but the physical file is still missing.
 */
class ReDownloadModelUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepositoryPort,
    private val downloadWorkScheduler: DownloadWorkSchedulerPort,
    private val loggingPort: LoggingPort
) {
    /**
     * Schedules re-download for a soft-deleted model.
     *
     * IMPORTANT: This does NOT call restoreSoftDeletedModel() here.
     * The restoration happens in DownloadFinalizeWorker after bytes are verified.
     *
     * @param modelId The ID of the soft-deleted model to re-download
     * @return Result containing ScheduledDownload with session metadata for UI tracking
     */
    suspend operator fun invoke(modelId: LocalModelId): Result<ScheduledDownload> = runCatching {
        loggingPort.debug("ReDownloadModelUseCase", "Starting re-download for model: $modelId")

        // 1. Get the existing model asset (which is currently soft-deleted)
        val existingAsset = localModelRepository.getAssetById(modelId)
            ?: throw IllegalStateException("Soft-deleted model $modelId not found in repository")

        // 2. Build DownloadFileSpec from the soft-deleted asset
        val fileSpec = DownloadFileSpec(
            remoteFileName = existingAsset.metadata.remoteFileName,
            localFileName = existingAsset.metadata.localFileName,
            sha256 = existingAsset.metadata.sha256,
            sizeInBytes = existingAsset.metadata.sizeInBytes,
            huggingFaceModelName = existingAsset.metadata.huggingFaceModelName,
            huggingFacePath = existingAsset.metadata.huggingFacePath,
            source = existingAsset.metadata.source.name,
            modelFileFormat = existingAsset.metadata.modelFileFormat.name,
            mmprojRemoteFileName = existingAsset.metadata.mmprojRemoteFileName,
            mmprojLocalFileName = existingAsset.metadata.mmprojLocalFileName,
            mmprojSha256 = existingAsset.metadata.mmprojSha256,
            mmprojSizeInBytes = existingAsset.metadata.mmprojSizeInBytes,
        )

        // 3. Create a new session ID for progress tracking
        val sessionId = UUID.randomUUID().toString()

        // 4. Build and enqueue the structured request
        val request = DownloadWorkRequest(
            files = listOf(fileSpec),
            sessionId = sessionId,
            requestKind = DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL,
            targetModelId = modelId,
            wifiOnly = true,
        )

        downloadWorkScheduler.enqueue(request)

        loggingPort.debug("ReDownloadModelUseCase", "Successfully scheduled re-download for model: $modelId")

        // 5. Return session metadata for UI progress tracking
        ScheduledDownload(
            sessionId = sessionId,
            requestKind = DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL,
            targetModelId = modelId,
        )
    }
}

