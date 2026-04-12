package com.browntowndev.pocketcrew.domain.port.media

interface ImageAttachmentStoragePort {
    suspend fun stageImage(sourceUri: String): String
}
