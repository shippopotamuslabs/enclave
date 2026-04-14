package com.browntowndev.pocketcrew.domain.port.download

import com.browntowndev.pocketcrew.domain.model.download.DownloadWorkRequest

interface DownloadWorkSchedulerPort {
    /**
     * Enqueues a structured download work request.
     * This is the sole entry point for both startup and re-download scenarios.
     * @param request The structured download work request
     */
    fun enqueue(request: DownloadWorkRequest)

    /**
     * Cancels any pending unique download work.
     */
    fun cancel()

    /**
     * Cleans up any partial (.tmp) files in the models directory.
     */
    suspend fun cleanupTempFiles()
}