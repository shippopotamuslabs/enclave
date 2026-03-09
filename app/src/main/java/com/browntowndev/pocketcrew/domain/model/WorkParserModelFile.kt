package com.browntowndev.pocketcrew.domain.model

import com.browntowndev.pocketcrew.domain.model.ModelFileFormat

class WorkParserModelFile(
    val sizeBytes: Long,
    val modelTypes: List<ModelType>,
    val modelFileFormat: ModelFileFormat,
    val localFileName: String,
    val sha256: String? = null,
)
