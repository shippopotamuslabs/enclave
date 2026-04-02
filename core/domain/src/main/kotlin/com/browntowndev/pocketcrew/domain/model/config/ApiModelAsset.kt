package com.browntowndev.pocketcrew.domain.model.config

data class ApiModelAsset(
    val credentials: ApiCredentials,
    val configurations: List<ApiModelConfiguration>
)
