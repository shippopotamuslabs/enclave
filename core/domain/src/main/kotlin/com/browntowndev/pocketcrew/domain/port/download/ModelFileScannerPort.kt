package com.browntowndev.pocketcrew.domain.port.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Port interface for scanning model files on disk.
 * Abstracts the file system operations for testability.
 */
interface ModelFileScannerPort {
    /**
     * Scan the models directory and create it if it doesn't exist.
     * Validates expected models against physical files on disk (existence and size).
     *
     * @param expectedModels Map of model types to assets expected from remote config (from cache)
     * @param utilityAssets Utility assets that are not assigned to model slots
     */
    suspend fun scanAndCreateDirIfNotExist(
        expectedModels: Map<ModelType, LocalModelAsset> = emptyMap(),
        utilityAssets: List<LocalModelAsset> = emptyList(),
    ): ModelScanResult

    /**
     * Deletes the physical model file from disk for the given local model ID.
     * Called during soft-delete of a local model.
     */
    suspend fun deleteModelFile(localModelId: LocalModelId)
}
