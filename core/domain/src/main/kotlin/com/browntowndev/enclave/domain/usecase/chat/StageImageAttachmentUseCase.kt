package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.port.media.ImageAttachmentStoragePort
import javax.inject.Inject

class StageImageAttachmentUseCase
    @Inject
    constructor(
        private val imageAttachmentStoragePort: ImageAttachmentStoragePort,
    ) {
        suspend operator fun invoke(sourceUri: String): String = imageAttachmentStoragePort.stageImage(sourceUri)
    }
