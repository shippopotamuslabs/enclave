package com.browntowndev.pocketcrew.domain.port.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Port interface for scanning model files on disk.
 * Abstracts the file system operations for testability.
 */
interface ModelFileScannerPort {
    /**
     * Scan the models directory and create it if it doesn't exist.
     * Validates against cache (expected models) for MD5 and format changes.
     *
     * @param downloadedModels Map of model types to assets actually downloaded (from registry)
     * @param expectedModels Map of model types to assets expected from remote config (from cache)
     */
    suspend fun scanAndCreateDirIfNotExist(
        downloadedModels: Map<ModelType, LocalModelAsset> = emptyMap(),
        expectedModels: Map<ModelType, LocalModelAsset> = emptyMap()
    ): ModelScanResult

    /**
     * Deletes the physical model file from disk for the given local model ID.
     * Called during soft-delete of a local model.
     */
    suspend fun deleteModelFile(localModelId: Long)
}
