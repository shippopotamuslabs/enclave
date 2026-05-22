package com.browntowndev.enclave.domain.model.inference

data class DiscoveredApiModel(
    val id: String,
    val name: String? = null,
    val contextWindowTokens: Int? = null,
    val maxOutputTokens: Int? = null,
    val created: Long? = null,
    val promptPrice: Double? = null,
    val completionPrice: Double? = null,
    val isMultimodal: Boolean? = null,
    val isChatCapable: Boolean = true,
    val isImageGenerationCapable: Boolean = false,
    val isVideoGenerationCapable: Boolean = false,
    val isAudioGenerationCapable: Boolean = false,
)
