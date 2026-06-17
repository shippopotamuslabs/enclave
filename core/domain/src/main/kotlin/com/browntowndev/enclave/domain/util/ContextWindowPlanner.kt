package com.browntowndev.enclave.domain.util

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.usecase.chat.SearchToolPromptComposer

/**
 * Centralized context-window budgeting for preflight history trimming and tool loops.
 */
object ContextWindowPlanner {
    /** Local agent-loop sessions below this use compact static prompts. */
    const val SMALL_CONTEXT_AGENT_LOOP_THRESHOLD = 32_000
    /** Llama.cpp sessions at or above this use FIXED_KV Rust policy; below use SMALL_CONTEXT. */
    const val LOCAL_FIXED_KV_CONTEXT_THRESHOLD = 12_000

    private const val MIN_COMPACT_VOLATILE_CONTEXT_CHARS = 512
    private const val COMPACT_VOLATILE_CONTEXT_CHARS_DIVISOR = 2
    private const val VOLATILE_CONTEXT_TRUNCATION_NOTE = "\n[Context truncated to fit local model window]"

    const val DEFAULT_OUTPUT_RESERVE_TOKENS = 1_024
    const val DEFAULT_SAFETY_BUFFER_TOKENS = 256
    const val DEFAULT_IMAGE_RESERVE_TOKENS = 1_024
    const val DEFAULT_THRESHOLD_RATIO = 0.75
    const val TOOL_RESULT_THRESHOLD_RATIO = 0.85
    const val LOCAL_TOOL_RESULT_BUFFER_TOKENS = 1_000
    const val TOOL_CALL_OVERHEAD_TOKENS = 30

    @Suppress("MaxLineLength")
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
        val discoveryInstructionTokens =
            if (options.toolCatalog.isNotEmpty()) {
                tokenCounter.countTokens(SearchToolPromptComposer.TOOL_DISCOVERY_INSTRUCTION_TEXT, modelId)
            } else {
                0
            }
        val activePayload =
            options.activeTools.joinToString(separator = "\n") { tool ->
                "${tool.name}\n${tool.description}\n${tool.schema}"
            }
        val activeSchemaTokens =
            if (activePayload.isNotEmpty()) {
                tokenCounter.countTokens(activePayload, modelId)
            } else {
                0
            }
        val toolSchemaTokens = discoveryInstructionTokens + activeSchemaTokens
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
        val systemTokens =
            systemPrompt
                .orEmpty()
                .takeIf(String::isNotBlank)
                ?.let { tokenCounter.countTokens(it, modelId) }
                ?: 0
        val historyTokens = history.sumOf { tokenCounter.countTokens(it.content, modelId) }
        val currentPromptTokens =
            currentPrompt
                .takeIf(String::isNotBlank)
                ?.let { tokenCounter.countTokens(it, modelId) }
                ?: 0
        val toolResultTokens =
            toolResultPayloads.sumOf {
                tokenCounter.countTokens(it, modelId) + TOOL_CALL_OVERHEAD_TOKENS
            }

        return systemTokens + historyTokens + currentPromptTokens + toolResultTokens
    }

    fun shouldCompact(
        estimatedPromptTokens: Int,
        budget: Budget,
    ): Boolean {
        val pressureLimit = minOf(budget.usablePromptTokens, budget.thresholdTokens)
        return estimatedPromptTokens > pressureLimit
    }

    fun isSmallContextLocalWindow(
        isLocal: Boolean,
        contextWindowTokens: Int?,
    ): Boolean = isLocal && contextWindowTokens != null && contextWindowTokens < SMALL_CONTEXT_AGENT_LOOP_THRESHOLD

    fun compactVolatileTurnContext(
        context: String?,
        contextWindowTokens: Int?,
    ): String? {
        val trimmed = context?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val window = contextWindowTokens?.takeIf { it > 0 } ?: return trimmed
        val maxChars =
            (window / COMPACT_VOLATILE_CONTEXT_CHARS_DIVISOR)
                .coerceAtLeast(MIN_COMPACT_VOLATILE_CONTEXT_CHARS)
        if (trimmed.length <= maxChars) return trimmed

        return trimmed
            .take((maxChars - VOLATILE_CONTEXT_TRUNCATION_NOTE.length).coerceAtLeast(1))
            .trimEnd() + VOLATILE_CONTEXT_TRUNCATION_NOTE
    }

    fun outputReserveFor(
        contextWindowTokens: Int,
        maxTokens: Int?,
    ): Int {
        val fallback = minOf(DEFAULT_OUTPUT_RESERVE_TOKENS, (contextWindowTokens / 4).coerceAtLeast(1))
        return (maxTokens ?: fallback)
            .coerceAtLeast(1)
            .coerceAtMost((contextWindowTokens / 2).coerceAtLeast(1))
    }
}
