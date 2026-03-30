package com.browntowndev.pocketcrew.domain.model.config

interface ModelTuningConfiguration {
    val id: Long
    val displayName: String
    val temperature: Double
    val topP: Double
    val topK: Int?
    val maxTokens: Int
    val contextWindow: Int
}
