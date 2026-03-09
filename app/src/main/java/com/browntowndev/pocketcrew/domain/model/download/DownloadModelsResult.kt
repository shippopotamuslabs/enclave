package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration

/**
 * Result of checkModels containing both the scan result and models that need downloading.
 * This eliminates duplicate scanning in startDownloads.
 */
data class DownloadModelsResult(
    val modelsToDownload: List<ModelConfiguration>,
    val scanResult: ModelScanResult
)
