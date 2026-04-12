package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.port.media.ImageAttachmentStoragePort
import javax.inject.Inject

class StageImageAttachmentUseCase @Inject constructor(
    private val imageAttachmentStoragePort: ImageAttachmentStoragePort,
) {
    suspend operator fun invoke(sourceUri: String): String =
        imageAttachmentStoragePort.stageImage(sourceUri)
}
