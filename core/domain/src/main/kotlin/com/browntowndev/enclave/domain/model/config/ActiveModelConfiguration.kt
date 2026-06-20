package com.browntowndev.enclave.domain.model.config

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat

data class ActiveModelConfiguration(
    val id: ModelConfigurationId,
    val isLocal: Boolean,
    val name: String,
    val modelId: String,
    val systemPrompt: String?,
    val isMultimodal: Boolean = false,
    val reasoningEffort: ApiReasoningEffort? = null,
    val temperature: Double?,
    val topK: Int?,
    val topP: Double?,
    val maxTokens: Int?,
    val minP: Double?,
    val repetitionPenalty: Double?,
    val contextWindow: Int?,
    val thinkingEnabled: Boolean,
    val reasoningBudget: Int? = null,
    val maxTokensDisabled: Boolean = false,
    val temperatureDisabled: Boolean = false,
    val topPDisabled: Boolean = false,
    val topKDisabled: Boolean = false,
    val minPDisabled: Boolean = false,
    val frequencyPenaltyDisabled: Boolean = false,
    val presencePenaltyDisabled: Boolean = false,
    val localModelFormat: ModelFileFormat? = null,
    val apiProvider: ApiProvider? = null,
    val apiModelPricing: ApiModelSavedPricing? = null,
    val apiBaseUrl: String? = null,
    val includeReasoningContentOnRequest: Boolean = false,
)
