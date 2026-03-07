package com.browntowndev.pocketcrew.domain.model

import com.browntowndev.pocketcrew.domain.model.ModelFileFormat

data class DownloadWorkerModelFile(
    val sizeBytes: Long,
    val url: String,
    val md5: String? = null,
    val modelTypes: List<ModelType>,
    val originalFileName: String,
    val modelFileFormat: ModelFileFormat,
) {
    val filenames: List<String>
        get() = modelTypes.map { "${it.name.lowercase()}.${modelFileFormat.extension.removePrefix(".")}" }
}
