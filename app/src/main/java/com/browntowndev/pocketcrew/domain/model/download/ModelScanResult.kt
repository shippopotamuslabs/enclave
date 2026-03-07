package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.domain.model.ModelConfiguration

data class ModelScanResult(
    val missingModels: List<ModelConfiguration>,
    val partialDownloads: Map<String, Long>,
    val allValid: Boolean,
    val directoryError: Boolean = false,
    val invalidModels: List<ModelConfiguration> = emptyList()
)
