package com.browntowndev.enclave.domain.port.download

import com.browntowndev.enclave.domain.model.download.DownloadProgressUpdate
import com.browntowndev.enclave.domain.model.download.FileProgress

/**
 * Parses the latest observed download work into a progress update (ENC-21).
 * The WorkInfo-based parser lives in :core:data; implementations keep the
 * latest work internally so ViewModels never touch WorkManager types.
 */
interface DownloadProgressParserPort {
    fun parseProgress(currentDownloads: List<FileProgress>): DownloadProgressUpdate?
}
