package com.browntowndev.pocketcrew.domain.model.config

data class ActiveModelConfiguration(
    val id: Long,
    val isLocal: Boolean,
    val name: String,
    val systemPrompt: String?,
    val temperature: Double?,
    val topK: Int?,
    val topP: Double?,
    val maxTokens: Int?,
    val minP: Double?,
    val repetitionPenalty: Double?,
    val contextWindow: Int?,
    val thinkingEnabled: Boolean
)
