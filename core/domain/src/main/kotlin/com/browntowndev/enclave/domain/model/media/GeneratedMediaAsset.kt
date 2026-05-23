package com.browntowndev.enclave.domain.model.media

data class GeneratedMediaAsset(
    val id: String,
    val localUri: String,
    val prompt: String,
    val mediaType: String,
    val createdAt: Long,
)
