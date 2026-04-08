package com.browntowndev.pocketcrew.domain.model.inference

/**
 * Per-response generation options passed at request time.
 * Derive reasoningBudget from LocalModelConfiguration.thinkingEnabled:
 * - thinkingEnabled = true  -> reasoningBudget = 2048
 * - thinkingEnabled = false -> reasoningBudget = 0
 */
data class GenerationOptions(
    val reasoningBudget: Int,        // 0 = reasoning OFF, >0 = reasoning ON with budget
    val modelType: ModelType? = null, // The requesting role (FAST, THINKING, etc.)
    val systemPrompt: String? = null,
    val reasoningEffort: ApiReasoningEffort? = null,
    val temperature: Float? = null,
    val topK: Int? = null,
    val topP: Float? = null,
    val minP: Float? = null,
    val maxTokens: Int? = null,
    val contextWindow: Int? = null,
)
