package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration

data class ModelScanResult(
    val missingModels: List<ModelConfiguration>,
    val partialDownloads: Map<String, Long>,
    val allValid: Boolean,
    val directoryError: Boolean = false,
    val invalidModels: List<ModelConfiguration> = emptyList()
)
