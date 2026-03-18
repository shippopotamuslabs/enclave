package com.browntowndev.pocketcrew.domain.port.download

import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult

/**
 * Port interface for scanning model files on disk.
 * Abstracts the file system operations for testability.
 */
interface ModelFileScannerPort {
    /**
     * Scan the models directory and create it if it doesn't exist.
     * Validates against cache (expected models) for MD5 and format changes.
     *
     * @param downloadedModels List of models actually downloaded (from registry)
     * @param expectedModels List of models expected from remote config (from cache)
     */
    suspend fun scanAndCreateDirIfNotExist(
        downloadedModels: List<ModelConfiguration> = emptyList(),
        expectedModels: List<ModelConfiguration> = emptyList()
    ): ModelScanResult
}
