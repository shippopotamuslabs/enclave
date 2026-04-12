package com.browntowndev.pocketcrew.domain.port.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

interface DownloadWorkSchedulerPort {
    /**
     * Enqueues a download job for the given models.
     * @param models Map of model role to asset (metadata + configs)
     * @param sessionId Optional session identifier for tracking
     * @param wifiOnly Whether to restrict download to WiFi
     */
    fun enqueue(models: Map<ModelType, LocalModelAsset>, sessionId: String?, wifiOnly: Boolean = true)

    /**
     * Convenience method to schedule a download for a single model asset.
     */
    fun scheduleModelDownload(modelType: ModelType, modelAsset: LocalModelAsset)

    /**
     * Cancels any pending unique download work.
     */
    fun cancel()

    /**
     * Cleans up any partial (.tmp) files in the models directory.
     */
    suspend fun cleanupTempFiles()
}
