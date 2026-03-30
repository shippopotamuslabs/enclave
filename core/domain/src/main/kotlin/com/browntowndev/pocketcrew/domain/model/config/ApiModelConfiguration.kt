package com.browntowndev.pocketcrew.domain.model.config

data class ApiModelConfiguration(
    override val id: Long = 0,
    val apiCredentialsId: Long,
    override val displayName: String,
    override val maxTokens: Int = 4096,
    override val contextWindow: Int = 4096,
    override val temperature: Double = 0.7,
    override val topP: Double = 0.95,
    override val topK: Int? = null,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
    val stopSequences: List<String> = emptyList(),
    val customHeadersAndParams: Map<String, String> = emptyMap(),
) : ModelTuningConfiguration
