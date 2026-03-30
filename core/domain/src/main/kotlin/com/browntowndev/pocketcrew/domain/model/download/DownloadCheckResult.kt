package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset

data class DownloadCheckResult(
    val canStart: Boolean,
    val errorMessage: String?,
    val missingModels: List<LocalModelAsset>
)
