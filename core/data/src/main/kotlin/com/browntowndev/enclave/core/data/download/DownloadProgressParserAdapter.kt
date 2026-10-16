package com.browntowndev.enclave.core.data.download

import androidx.work.WorkInfo
import com.browntowndev.enclave.core.data.repository.DownloadWorkRepository
import com.browntowndev.enclave.domain.model.download.DownloadProgressUpdate
import com.browntowndev.enclave.domain.model.download.FileProgress
import com.browntowndev.enclave.domain.port.download.DownloadProgressParserPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DownloadProgressParserPort] backed by the WorkInfo parser (ENC-21).
 * Keeps the latest WorkInfo from the repository flow so callers parse
 * without ever seeing WorkManager types.
 */
@Singleton
class DownloadProgressParserAdapter @Inject constructor(
    repository: DownloadWorkRepository,
    private val parser: WorkProgressParser,
) : DownloadProgressParserPort {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var latestWorkInfo: WorkInfo? = null

    init {
        scope.launch {
            repository.observeDownloadProgress().collect { latestWorkInfo = it }
        }
    }

    override fun parseProgress(currentDownloads: List<FileProgress>): DownloadProgressUpdate? =
        latestWorkInfo?.let { parser.parse(it, currentDownloads) }
}
