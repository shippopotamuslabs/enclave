package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Result of checkModels containing both the scan result and models that need downloading.
 * This eliminates duplicate scanning in startDownloads.
 */
data class DownloadModelsResult(
    val allModels: Map<ModelType, LocalModelAsset>,
    val modelsToDownload: List<LocalModelAsset>,
    val scanResult: ModelScanResult
)
