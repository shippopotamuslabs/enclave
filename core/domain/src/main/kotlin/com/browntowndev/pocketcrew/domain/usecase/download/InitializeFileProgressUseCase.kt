package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import javax.inject.Inject

data class FileProgressInitResult(
    val fileProgressList: List<FileProgress>,
    val modelsTotal: Int,
    val modelsComplete: Int,
    val overallProgress: Float
)

class InitializeFileProgressUseCase @Inject constructor() {

    operator fun invoke(
        scanResult: ModelScanResult,
        allModels: Map<ModelType, LocalModelAsset>,
        existingDownloads: List<FileProgress> = emptyList()
    ): FileProgressInitResult {
        val existingFailedFiles = existingDownloads
            .filter { it.status == FileStatus.FAILED }
            .associateBy { it.filename }

        val missingAssets = scanResult.missingModels
        val partialDownloadAssets = scanResult.partialDownloads.keys
            .filter { filename -> missingAssets.none { filename == it.metadata.localFileName } }
            .mapNotNull { filename ->
                allModels.values.find { filename == it.metadata.localFileName }
            }

        val allAssetsToDownload = (missingAssets + partialDownloadAssets).distinctBy { it.metadata.sha256 }

        // Map SHA256 to list of ModelTypes that use it
        val sha256ToModelTypes = allModels.entries
            .groupBy({ it.value.metadata.sha256 }, { it.key })

        val fileProgressList = allAssetsToDownload.map { asset ->
            val combinedModelTypes = sha256ToModelTypes[asset.metadata.sha256] ?: emptyList()
            val trackingKey = asset.metadata.localFileName
            val partialBytes = scanResult.partialDownloads[trackingKey] ?: 0L
            val existingFailed = existingFailedFiles[trackingKey]
            val resumeBytes = when {
                partialBytes > 0 -> partialBytes
                existingFailed != null -> existingFailed.bytesDownloaded
                else -> 0L
            }

            FileProgress(
                filename = trackingKey,
                modelTypes = combinedModelTypes,
                bytesDownloaded = resumeBytes,
                totalBytes = asset.metadata.sizeInBytes,
                status = if (partialBytes > 0) FileStatus.DOWNLOADING else FileStatus.QUEUED,
                speedMBs = null
            )
        }

        // Calculate totals based on unique model types (not files), since multiple modelTypes can share a file
        val allMissingModelTypes = allAssetsToDownload.flatMap { asset ->
            sha256ToModelTypes[asset.metadata.sha256] ?: emptyList()
        }.distinct()
        
        val totalModels = allModels.size
        val modelsMissing = allMissingModelTypes.size
        val modelsComplete = totalModels - modelsMissing
        
        val totalBytes = fileProgressList.sumOf { it.totalBytes }
        val downloadedBytes = fileProgressList.sumOf { it.bytesDownloaded }
        val overallProgress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f

        return FileProgressInitResult(
            fileProgressList = fileProgressList,
            modelsTotal = totalModels,
            modelsComplete = modelsComplete,
            overallProgress = overallProgress
        )
    }
}
