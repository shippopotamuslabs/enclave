package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.port.media.ImageAttachmentStoragePort
import javax.inject.Inject

open class StageImageAttachmentUseCase
    @Inject
    constructor(
        private val imageAttachmentStoragePort: ImageAttachmentStoragePort,
    ) {
        open suspend operator fun invoke(sourceUri: String): String = imageAttachmentStoragePort.stageImage(sourceUri)
    }
