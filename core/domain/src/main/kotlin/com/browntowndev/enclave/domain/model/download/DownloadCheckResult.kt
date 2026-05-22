package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset

data class DownloadCheckResult(
    val canStart: Boolean,
    val errorMessage: String?,
    val missingModels: List<LocalModelAsset>,
)
