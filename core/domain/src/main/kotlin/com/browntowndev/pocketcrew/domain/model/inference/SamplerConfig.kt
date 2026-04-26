package com.browntowndev.pocketcrew.domain.model.inference

/**
 * A domain-level sampling configuration for LiteRT models.
 * This is mapped to the native LiteRT SamplerConfig in the inference module.
 */
data class SamplerConfig(
    val temperature: Double = 0.7,
    val topK: Int = 40,
    val topP: Double = 0.95
)
