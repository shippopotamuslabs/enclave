package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset

data class ModelScanResult(
    val missingModels: List<LocalModelAsset>,
    val partialDownloads: Map<String, Long>,
    val allValid: Boolean,
    val directoryError: Boolean = false,
    val invalidModels: List<LocalModelAsset> = emptyList()
)
