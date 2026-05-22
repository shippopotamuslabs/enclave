package com.browntowndev.enclave.domain.port.media

interface ImageAttachmentStoragePort {
    suspend fun stageImage(sourceUri: String): String
}
