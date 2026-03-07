package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.domain.model.ModelFile

data class ModelScanResult(
    val missingModels: List<ModelFile>,
    val partialDownloads: Map<String, Long>,
    val allValid: Boolean,
    val directoryError: Boolean = false,
    val invalidModels: List<ModelFile> = emptyList()
)
