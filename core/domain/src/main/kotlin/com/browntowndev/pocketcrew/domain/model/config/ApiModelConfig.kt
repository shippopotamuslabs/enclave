package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

/**
 * Domain model for a configured API model. Pure Kotlin — no framework deps.
 * API keys are NEVER stored here — managed exclusively by ApiKeyManager in :data.
 *
 * Tuning fields are exhaustive across Anthropic, OpenAI, and Google APIs.
 */
data class ApiModelConfig(
    val id: Long = 0,
    val displayName: String,
    val provider: ApiProvider,
    val modelId: String,
    val baseUrl: String? = null,
    val isVision: Boolean = false,
    val thinkingEnabled: Boolean = false,
    val maxTokens: Int = 4096,
    val contextWindow: Int = 4096,
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val topK: Int? = null,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
    val stopSequences: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)
