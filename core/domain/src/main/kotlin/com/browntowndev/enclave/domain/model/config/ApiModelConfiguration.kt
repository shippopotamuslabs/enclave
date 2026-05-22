package com.browntowndev.enclave.domain.model.config

import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort

data class ApiModelConfiguration(
    val id: ApiModelConfigurationId = ApiModelConfigurationId(""),
    val apiCredentialsId: ApiCredentialsId,
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
    val maxTokensDisabled: Boolean = false,
    val temperatureDisabled: Boolean = false,
    val topPDisabled: Boolean = false,
    val topKDisabled: Boolean = false,
    val minPDisabled: Boolean = false,
    val frequencyPenaltyDisabled: Boolean = false,
    val presencePenaltyDisabled: Boolean = false,
    val customHeaders: Map<String, String> = emptyMap(),
    val openRouterRouting: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration(),
) : ModelTuningConfiguration
