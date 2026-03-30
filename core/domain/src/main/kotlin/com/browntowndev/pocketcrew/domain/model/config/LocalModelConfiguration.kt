package com.browntowndev.pocketcrew.domain.model.config

data class LocalModelConfiguration(
    override val id: Long = 0,
    val localModelId: Long,
    override val displayName: String,
    override val maxTokens: Int,
    override val contextWindow: Int,
    override val temperature: Double,
    override val topP: Double,
    override val topK: Int?,
    val minP: Double = 0.0,
    val repetitionPenalty: Double,
    val thinkingEnabled: Boolean = false,
    val systemPrompt: String
) : ModelTuningConfiguration
