package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.domain.model.ModelConfiguration

data class DownloadCheckResult(
    val canStart: Boolean,
    val errorMessage: String?,
    val missingModels: List<ModelConfiguration>
)
