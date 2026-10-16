package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.domain.model.download.DownloadProgressUpdate
import com.browntowndev.enclave.domain.model.download.FileProgress
import com.browntowndev.enclave.domain.port.download.DownloadProgressParserPort
import javax.inject.Inject

class ParseDownloadProgressUseCase @Inject constructor(
    private val port: DownloadProgressParserPort,
) {
    operator fun invoke(currentDownloads: List<FileProgress>): DownloadProgressUpdate? =
        port.parseProgress(currentDownloads)
}
