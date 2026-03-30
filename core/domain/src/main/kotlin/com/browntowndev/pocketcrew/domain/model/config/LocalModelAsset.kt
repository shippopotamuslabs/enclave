package com.browntowndev.pocketcrew.domain.model.config

data class LocalModelAsset(
    val metadata: LocalModelMetadata,
    val configurations: List<LocalModelConfiguration>
)
