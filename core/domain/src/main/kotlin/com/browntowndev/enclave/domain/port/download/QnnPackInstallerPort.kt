package com.browntowndev.enclave.domain.port.download

import com.browntowndev.enclave.domain.model.config.LocalModelMetadata

fun interface QnnPackInstallProgressListener {
    suspend fun onProgress(
        bytesExtracted: Long,
        totalBytes: Long,
    )
}

interface QnnPackInstallerPort {
    suspend fun installIfNeeded(
        metadata: LocalModelMetadata,
        progressListener: QnnPackInstallProgressListener? = null,
    ): Boolean
}
