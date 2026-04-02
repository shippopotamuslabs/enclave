package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat

data class LocalModelMetadata(
    val id: Long = 0,
    val huggingFaceModelName: String,
    val remoteFileName: String,
    val localFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
    val modelFileFormat: ModelFileFormat,
    val visionCapable: Boolean = false
)
