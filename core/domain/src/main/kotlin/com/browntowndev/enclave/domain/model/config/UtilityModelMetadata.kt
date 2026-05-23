package com.browntowndev.enclave.domain.model.config

import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat

data class UtilityModelMetadata(
    val id: UtilityModelId,
    val utilityType: UtilityType,
    val huggingFaceModelName: String,
    val remoteFileName: String,
    val localFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
    val modelFileFormat: ModelFileFormat,
    val source: DownloadSource = DownloadSource.HUGGING_FACE,
)
