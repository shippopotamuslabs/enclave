package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort

data class ApiModelConfiguration(
    override val id: Long = 0,
    val apiCredentialsId: Long,
    override val displayName: String,
    override val maxTokens: Int = 4096,
    override val contextWindow: Int = 4096,
    override val temperature: Double = 0.7,
    override val topP: Double = 0.95,
    override val topK: Int? = 40,
    val minP: Double = 0.05,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
    val systemPrompt: String = "",
    val reasoningEffort: ApiReasoningEffort? = null,
    val customHeaders: Map<String, String> = emptyMap(),
    val openRouterRouting: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration(),
) : ModelTuningConfiguration
