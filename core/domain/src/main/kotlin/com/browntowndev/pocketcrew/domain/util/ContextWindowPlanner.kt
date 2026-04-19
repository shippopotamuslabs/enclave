package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions

/**
 * Centralized context-window budgeting for preflight history trimming and tool loops.
 */
object ContextWindowPlanner {
    const val DEFAULT_OUTPUT_RESERVE_TOKENS = 1_024
    const val DEFAULT_SAFETY_BUFFER_TOKENS = 256
    const val DEFAULT_IMAGE_RESERVE_TOKENS = 1_024
    const val DEFAULT_THRESHOLD_RATIO = 0.75
    const val TOOL_RESULT_THRESHOLD_RATIO = 0.85
    const val LOCAL_TOOL_RESULT_BUFFER_TOKENS = 1_000
    const val TOOL_CALL_OVERHEAD_TOKENS = 30

    const val STOP_TOOLS_WARNING =
        "[SYSTEM: Context window is nearly full. Do not call any more tools. Provide a final response based on the information you already have.]"

    data class Budget(
        val contextWindowTokens: Int,
        val outputReserveTokens: Int,
        val reasoningReserveTokens: Int,
        val toolSchemaTokens: Int,
        val mediaReserveTokens: Int,
        val safetyBufferTokens: Int = DEFAULT_SAFETY_BUFFER_TOKENS,
        val thresholdRatio: Double = DEFAULT_THRESHOLD_RATIO,
    ) {
        val reservedTokens: Int
            get() = outputReserveTokens + reasoningReserveTokens + toolSchemaTokens + mediaReserveTokens + safetyBufferTokens

        val thresholdTokens: Int
            get() = (contextWindowTokens * thresholdRatio).toInt()

        val usablePromptTokens: Int
            get() = (contextWindowTokens - reservedTokens).coerceAtLeast(0)
    }

    fun budgetFor(
        contextWindowTokens: Int,
        options: GenerationOptions,
        modelId: String? = null,
        tokenCounter: TokenCounter = JTokkitTokenCounter,
        thresholdRatio: Double = DEFAULT_THRESHOLD_RATIO,
    ): Budget {
        val outputReserve = outputReserveFor(contextWindowTokens, options.maxTokens)
        val reasoningReserve = options.reasoningBudget.coerceAtLeast(0)
        val toolSchemaTokens = options.availableTools.sumOf { tool ->
            tokenCounter.countTokens("${tool.name}\n${tool.description}\n${tool.schema}", modelId)
        }
        val mediaReserve = options.imageUris.size * DEFAULT_IMAGE_RESERVE_TOKENS

        return Budget(
            contextWindowTokens = contextWindowTokens,
            outputReserveTokens = outputReserve,
            reasoningReserveTokens = reasoningReserve,
            toolSchemaTokens = toolSchemaTokens,
            mediaReserveTokens = mediaReserve,
            thresholdRatio = thresholdRatio,
        )
    }

    fun estimatePromptTokens(
        history: List<ChatMessage>,
        systemPrompt: String?,
        currentPrompt: String,
        toolResultPayloads: List<String> = emptyList(),
        modelId: String? = null,
        tokenCounter: TokenCounter = JTokkitTokenCounter,
    ): Int {
        val systemTokens = systemPrompt.orEmpty().takeIf(String::isNotBlank)
            ?.let { tokenCounter.countTokens(it, modelId) }
            ?: 0
        val historyTokens = history.sumOf { tokenCounter.countTokens(it.content, modelId) }
        val currentPromptTokens = currentPrompt.takeIf(String::isNotBlank)
            ?.let { tokenCounter.countTokens(it, modelId) }
            ?: 0
        val toolResultTokens = toolResultPayloads.sumOf {
            tokenCounter.countTokens(it, modelId) + TOOL_CALL_OVERHEAD_TOKENS
        }

        return systemTokens + historyTokens + currentPromptTokens + toolResultTokens
    }

    fun shouldCompact(estimatedPromptTokens: Int, budget: Budget): Boolean {
        val pressureLimit = minOf(budget.usablePromptTokens, budget.thresholdTokens)
        return estimatedPromptTokens > pressureLimit
    }

    fun outputReserveFor(contextWindowTokens: Int, maxTokens: Int?): Int {
        val fallback = minOf(DEFAULT_OUTPUT_RESERVE_TOKENS, (contextWindowTokens / 4).coerceAtLeast(1))
        return (maxTokens ?: fallback)
            .coerceAtLeast(1)
            .coerceAtMost((contextWindowTokens / 2).coerceAtLeast(1))
    }
}
