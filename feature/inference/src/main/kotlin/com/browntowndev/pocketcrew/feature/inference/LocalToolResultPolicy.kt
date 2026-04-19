package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.util.ContextWindowPlanner
import com.browntowndev.pocketcrew.domain.util.JTokkitTokenCounter
import com.browntowndev.pocketcrew.domain.util.NativeToolResultFormatter
import com.browntowndev.pocketcrew.domain.util.ToolContextBudget

internal data class LocalToolResultDecision(
    val finalResult: String,
    val shouldTrackTransientToolResult: Boolean,
    val contextFull: Boolean,
    val totalTokens: Int,
)

internal object LocalToolResultPolicy {

    fun buildContextWindowExceededToolError(toolName: String): String =
        """{"error":"tool_execution_failed","tool":"${escapeJson(toolName)}","exception":"IllegalStateException","message":"${escapeJson(CONTEXT_WINDOW_EXCEEDED_MESSAGE)}"}"""

    fun evaluate(
        rawResultJson: String,
        contextWindowTokens: Int,
        currentSystemPrompt: String,
        history: List<ChatMessage>,
        transientToolResultTokens: Int,
        modelId: String?,
    ): LocalToolResultDecision {
        val tokenCounter = JTokkitTokenCounter
        val systemPromptTokens = ToolContextBudget.countSystemPromptTokens(currentSystemPrompt, modelId, tokenCounter)
        val historyTokens = ToolContextBudget.countHistoryTokens(history, modelId, tokenCounter)
        val estimatedUsedTokens = systemPromptTokens + historyTokens + transientToolResultTokens

        val resultJson = NativeToolResultFormatter.truncateToolResult(
            resultJson = rawResultJson,
            contextWindowTokens = contextWindowTokens,
            estimatedUsedTokens = estimatedUsedTokens,
            bufferTokens = ContextWindowPlanner.LOCAL_TOOL_RESULT_BUFFER_TOKENS,
            tokenCounter = tokenCounter,
            modelId = modelId,
        )

        val evaluation = ToolContextBudget.evaluate(
            contextWindowTokens = contextWindowTokens,
            systemPromptTokens = systemPromptTokens,
            historyTokens = historyTokens,
            transientToolResultTokens = transientToolResultTokens,
            options = GenerationOptions(reasoningBudget = 0),
            modelId = modelId,
            tokenCounter = tokenCounter,
        )

        val finalResult = if (evaluation.contextFull) {
            resultJson + "\n${ContextWindowPlanner.STOP_TOOLS_WARNING}"
        } else {
            resultJson
        }

        return LocalToolResultDecision(
            finalResult = finalResult,
            shouldTrackTransientToolResult = !resultJson.contains("\"error\""),
            contextFull = evaluation.contextFull,
            totalTokens = evaluation.totalTokens,
        )
    }

    private const val CONTEXT_WINDOW_EXCEEDED_MESSAGE =
        "Context window exceeded: Model was warned to stop calling tools but continued. Try simplifying your request."

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
}
