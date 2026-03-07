package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.domain.model.ModelFile

/**
 * Result of checkModels containing both the scan result and models that need downloading.
 * This eliminates duplicate scanning in startDownloads.
 */
data class DownloadModelsResult(
    val modelsToDownload: List<ModelFile>,
    val scanResult: ModelScanResult
)
