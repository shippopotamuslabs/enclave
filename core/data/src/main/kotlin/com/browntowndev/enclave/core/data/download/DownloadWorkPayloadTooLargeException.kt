package com.browntowndev.enclave.core.data.download

class DownloadWorkPayloadTooLargeException(
    val fileSpecCount: Int,
    cause: Throwable? = null,
) : IllegalArgumentException(
        "Download work payload exceeds WorkManager size limit for $fileSpecCount file specs",
        cause,
    )
