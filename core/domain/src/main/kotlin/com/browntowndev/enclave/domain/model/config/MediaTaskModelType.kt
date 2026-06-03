package com.browntowndev.enclave.domain.model.config

import com.browntowndev.enclave.domain.model.inference.ModelType

fun MediaTask.toModelType(): ModelType =
    when (this) {
        MediaTask.TEXT_TO_IMAGE -> ModelType.IMAGE_GENERATION
        MediaTask.EDIT_IMAGE -> ModelType.IMAGE_EDIT
        MediaTask.TEXT_TO_VIDEO -> ModelType.VIDEO_GENERATION
        MediaTask.IMAGE_TO_VIDEO -> ModelType.IMAGE_TO_VIDEO
    }

fun ModelType.toMediaTaskOrNull(): MediaTask? =
    when (this) {
        ModelType.IMAGE_GENERATION -> MediaTask.TEXT_TO_IMAGE
        ModelType.IMAGE_EDIT -> MediaTask.EDIT_IMAGE
        ModelType.VIDEO_GENERATION -> MediaTask.TEXT_TO_VIDEO
        ModelType.IMAGE_TO_VIDEO -> MediaTask.IMAGE_TO_VIDEO
        else -> null
    }
