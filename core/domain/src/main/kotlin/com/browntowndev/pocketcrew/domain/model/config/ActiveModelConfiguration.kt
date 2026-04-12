package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort

data class ActiveModelConfiguration(
    val id: ModelConfigurationId,
    val isLocal: Boolean,
    val name: String,
    val systemPrompt: String?,
    val visionCapable: Boolean = false,
    val reasoningEffort: ApiReasoningEffort? = null,
    val temperature: Double?,
    val topK: Int?,
    val topP: Double?,
    val maxTokens: Int?,
    val minP: Double?,
    val repetitionPenalty: Double?,
    val contextWindow: Int?,
    val thinkingEnabled: Boolean
)
