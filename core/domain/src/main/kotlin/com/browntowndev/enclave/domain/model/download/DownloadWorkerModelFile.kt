package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType

data class DownloadWorkerModelFile(
    val sizeBytes: Long,
    val url: String,
    val md5: String? = null,
    val modelTypes: List<ModelType>,
    val originalFileName: String,
    val modelFileFormat: ModelFileFormat,
) {
    val filenames: List<String> =
        modelTypes.map { "${it.name.lowercase()}.${modelFileFormat.extension.removePrefix(".")}" }
}
