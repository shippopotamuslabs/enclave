package com.browntowndev.pocketcrew.domain.model.inference

data class DiscoveredApiModel(
    val id: String,
    val name: String? = null,
    val contextWindowTokens: Int? = null,
    val maxOutputTokens: Int? = null,
    val created: Long? = null,
    val promptPrice: Double? = null,
    val completionPrice: Double? = null
)
