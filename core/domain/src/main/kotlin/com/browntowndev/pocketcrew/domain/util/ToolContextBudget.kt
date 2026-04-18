package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions

/**
 * Encapsulates the business logic for tracking and evaluating context-window
 * pressure during tool-call loops.
 *
 * This logic was previously duplicated across ConversationManagerImpl (LiteRT),
 * LlamaInferenceServiceImpl, and the API services (Anthropic, Google, OpenAI).
 *
 * All computation is pure — no framework or SDK dependencies.
 */
object ToolContextBudget {

    /**
     * Result of evaluating whether the context window is full after a tool result.
     *
     * @property contextFull True when total tokens exceed [thresholdTokens].
     * @property totalTokens The total estimated tokens (system prompt + history + tool results).
     * @property truncateToTokens The per-result token budget, or null if no truncation needed.
     */
    data class Evaluation(
        val contextFull: Boolean,
        val totalTokens: Int,
        val truncateToTokens: Int? = null,
    )

    /**
     * Computes the token budget available for tool results given the current context state.
     *
     * @param contextWindowTokens Total context window size in tokens.
     * @param systemPromptTokens Tokens used by the system prompt.
     * @param historyTokens Tokens used by conversation history.
     * @param transientToolResultTokens Tokens already consumed by previous tool results in this loop.
     * @param options Generation options (for output/reasoning reserves).
     * @param modelId Model identifier for token counting.
     * @param tokenCounter Token counter implementation.
     * @param toolResultCount Number of tool results in the current batch (for per-result budget division).
     * @param thresholdRatio Context pressure ratio (default [ContextWindowPlanner.TOOL_RESULT_THRESHOLD_RATIO]).
     * @return [Evaluation] with context-full status, total tokens, and optional per-result truncation budget.
     */
    fun evaluate(
        contextWindowTokens: Int,
        systemPromptTokens: Int,
        historyTokens: Int,
        transientToolResultTokens: Int,
        options: GenerationOptions,
        modelId: String?,
        tokenCounter: TokenCounter = JTokkitTokenCounter,
        toolResultCount: Int = 1,
        thresholdRatio: Double = ContextWindowPlanner.TOOL_RESULT_THRESHOLD_RATIO,
    ): Evaluation {
        val budget = ContextWindowPlanner.budgetFor(
            contextWindowTokens = contextWindowTokens,
            options = options,
            modelId = modelId,
            tokenCounter = tokenCounter,
            thresholdRatio = thresholdRatio,
        )
        val estimatedUsedTokens = systemPromptTokens + historyTokens + transientToolResultTokens
        val contextFull = estimatedUsedTokens > budget.thresholdTokens

        val truncateToTokens = if (contextFull || estimatedUsedTokens + ContextWindowPlanner.LOCAL_TOOL_RESULT_BUFFER_TOKENS > budget.usablePromptTokens) {
            // Compute per-result token budget
            val availableTokens = (budget.usablePromptTokens - estimatedUsedTokens)
                .coerceAtLeast(0)
            maxOf(100, availableTokens / toolResultCount.coerceAtLeast(1))
        } else {
            null
        }

        return Evaluation(
            contextFull = contextFull,
            totalTokens = estimatedUsedTokens,
            truncateToTokens = truncateToTokens,
        )
    }

    /**
     * Estimates total prompt tokens for an API model tool loop.
     *
     * @param history Conversation history messages.
     * @param systemPrompt Optional system prompt.
     * @param currentPrompt The current user prompt.
     * @param toolResultPayloads Accumulated tool result strings from the current loop.
     * @param modelId Model identifier for token counting.
     * @param tokenCounter Token counter implementation.
     * @return Estimated total prompt tokens.
     */
    fun estimateApiPromptTokens(
        history: List<ChatMessage>,
        systemPrompt: String?,
        currentPrompt: String,
        toolResultPayloads: List<String>,
        modelId: String?,
        tokenCounter: TokenCounter = JTokkitTokenCounter,
    ): Int = ContextWindowPlanner.estimatePromptTokens(
        history = history,
        systemPrompt = systemPrompt,
        currentPrompt = currentPrompt,
        toolResultPayloads = toolResultPayloads,
        modelId = modelId,
        tokenCounter = tokenCounter,
    )

    /**
     * Evaluates whether an API model's context is exceeded and whether mid-loop
     * compaction should be triggered.
     *
     * @param contextWindowTokens Total context window size.
     * @param history Conversation history messages.
     * @param systemPrompt Optional system prompt text.
     * @param currentPrompt The current user prompt.
     * @param toolResultPayloads Accumulated tool result strings.
     * @param options Generation options.
     * @param modelId Model identifier for token counting.
     * @param tokenCounter Token counter implementation.
     * @return True if the context window threshold is exceeded.
     */
    fun isApiContextExceeded(
        contextWindowTokens: Int,
        history: List<ChatMessage>,
        systemPrompt: String?,
        currentPrompt: String,
        toolResultPayloads: List<String>,
        options: GenerationOptions,
        modelId: String?,
        tokenCounter: TokenCounter = JTokkitTokenCounter,
    ): Boolean {
        val budget = ContextWindowPlanner.budgetFor(
            contextWindowTokens = contextWindowTokens,
            options = options,
            modelId = modelId,
            tokenCounter = tokenCounter,
            thresholdRatio = ContextWindowPlanner.TOOL_RESULT_THRESHOLD_RATIO,
        )
        val totalTokens = estimateApiPromptTokens(
            history = history,
            systemPrompt = systemPrompt,
            currentPrompt = currentPrompt,
            toolResultPayloads = toolResultPayloads,
            modelId = modelId,
            tokenCounter = tokenCounter,
        )
        return ContextWindowPlanner.shouldCompact(totalTokens, budget)
    }

    /**
     * Computes the available token budget for API model tool result truncation.
     *
     * @param contextWindowTokens Total context window size, or null if unknown.
     * @param history Conversation history messages.
     * @param systemPrompt Optional system prompt text.
     * @param currentPrompt The current user prompt.
     * @param toolResultPayloads Accumulated tool result strings.
     * @param toolResultCount Number of tool results in the current batch.
     * @param options Generation options.
     * @param modelId Model identifier for token counting.
     * @param tokenCounter Token counter implementation.
     * @return Available tokens per result, or null if context window is unknown.
     */
    fun apiTruncationBudget(
        contextWindowTokens: Int?,
        history: List<ChatMessage>,
        systemPrompt: String?,
        currentPrompt: String,
        toolResultPayloads: List<String>,
        toolResultCount: Int,
        options: GenerationOptions,
        modelId: String?,
        tokenCounter: TokenCounter = JTokkitTokenCounter,
    ): Int? {
        if (contextWindowTokens == null) return null
        val budget = ContextWindowPlanner.budgetFor(
            contextWindowTokens = contextWindowTokens,
            options = options,
            modelId = modelId,
            tokenCounter = tokenCounter,
            thresholdRatio = ContextWindowPlanner.TOOL_RESULT_THRESHOLD_RATIO,
        )
        val usedTokens = estimateApiPromptTokens(
            history = history,
            systemPrompt = systemPrompt,
            currentPrompt = currentPrompt,
            toolResultPayloads = toolResultPayloads,
            modelId = modelId,
            tokenCounter = tokenCounter,
        )
        val availableTokens = (budget.usablePromptTokens - usedTokens).coerceAtLeast(0)
        return maxOf(100, availableTokens / toolResultCount.coerceAtLeast(1))
    }

    /**
     * Computes the available token budget for local model tool result truncation.
     *
     * @param contextWindowTokens Total context window size.
     * @param systemPromptTokens Tokens used by the system prompt.
     * @param historyTokens Tokens used by conversation history.
     * @param transientToolResultTokens Tokens already consumed by previous tool results.
     * @param bufferTokens Safety buffer tokens (default [ContextWindowPlanner.LOCAL_TOOL_RESULT_BUFFER_TOKENS]).
     * @return Available tokens for the current tool result, or 0 if no room.
     */
    fun localTruncationBudget(
        contextWindowTokens: Int,
        systemPromptTokens: Int,
        historyTokens: Int,
        transientToolResultTokens: Int,
        bufferTokens: Int = ContextWindowPlanner.LOCAL_TOOL_RESULT_BUFFER_TOKENS,
    ): Int = (contextWindowTokens - systemPromptTokens - historyTokens - transientToolResultTokens - bufferTokens)
        .coerceAtLeast(0)

    /**
     * Counts tokens in all history messages.
     */
    fun countHistoryTokens(
        history: List<ChatMessage>,
        modelId: String?,
        tokenCounter: TokenCounter = JTokkitTokenCounter,
    ): Int = history.sumOf { tokenCounter.countTokens(it.content, modelId) }

    /**
     * Counts tokens in a system prompt string.
     */
    fun countSystemPromptTokens(
        systemPrompt: String,
        modelId: String?,
        tokenCounter: TokenCounter = JTokkitTokenCounter,
    ): Int = tokenCounter.countTokens(systemPrompt, modelId)
}