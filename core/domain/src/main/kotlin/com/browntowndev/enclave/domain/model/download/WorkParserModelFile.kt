package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType

class WorkParserModelFile(
    val sizeBytes: Long,
    val modelTypes: List<ModelType>,
    val modelFileFormat: ModelFileFormat,
    val localFileName: String,
    val sha256: String? = null,
)
