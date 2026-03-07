package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.FileProgress
import com.browntowndev.pocketcrew.domain.model.FileStatus
import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import javax.inject.Inject

data class FileProgressInitResult(
    val fileProgressList: List<FileProgress>,
    val modelsTotal: Int,
    val modelsComplete: Int,
    val overallProgress: Float
)

class InitializeFileProgressUseCase @Inject constructor() {

    companion object {
        private const val TAG = "InitializeFileProgress"
    }

    operator fun invoke(
        scanResult: ModelScanResult,
        allModels: List<ModelConfiguration>,
        existingDownloads: List<FileProgress> = emptyList()
    ): FileProgressInitResult {
        val existingFailedFiles = existingDownloads
            .filter { it.status == FileStatus.FAILED }
            .associateBy { it.filename }

        val allModelsToDownload = buildList {
            addAll(scanResult.missingModels)
            scanResult.partialDownloads.keys
                .filter { filename -> scanResult.missingModels.none { filename == it.metadata.localFileName } }
                .mapNotNull { filename ->
                    allModels.find { filename == it.metadata.localFileName }
                }
                .let { addAll(it) }
        }

        // Group models by remote filename to combine multiple modelTypes
        // that share the same remote file (e.g., VISION + FAST both use gemma_3n_e4b_it_int4.litertlm)
        val modelsByRemoteFile = allModelsToDownload.groupBy { it.metadata.remoteFileName }

        val fileProgressList = modelsByRemoteFile.map { (remoteFilename, models) ->
            val model = models.first() // All models in this group share the same remote filename
            val combinedModelTypes = models.map { it.modelType }.distinct()
            val trackingKey = model.metadata.localFileName
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
                totalBytes = model.metadata.sizeInBytes,
                status = if (partialBytes > 0) FileStatus.DOWNLOADING else FileStatus.QUEUED,
                speedMBs = null
            ).also { Log.d("InitializeFileProgress", "[DEBUG] Created FileProgress: filename=${it.filename}, modelTypes=${it.modelTypes}") }
        }

        // Calculate totals based on unique model types (not files), since multiple modelTypes can share a file
        val uniqueModelTypes = allModelsToDownload.map { it.modelType }.distinct()
        val totalModels = uniqueModelTypes.size
        val totalBytes = fileProgressList.sumOf { it.totalBytes }
        val downloadedBytes = fileProgressList.sumOf { it.bytesDownloaded }
        val modelsComplete = fileProgressList.filter { it.status == FileStatus.COMPLETE }
            .sumOf { it.modelTypes.size }
        val overallProgress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f

        return FileProgressInitResult(
            fileProgressList = fileProgressList,
            modelsTotal = totalModels,
            modelsComplete = modelsComplete,
            overallProgress = overallProgress
        )
    }
}
