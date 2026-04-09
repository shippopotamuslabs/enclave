package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.ThinkingConfigEnabled
import com.anthropic.models.messages.Tool

object AnthropicRequestMapper {
    private const val SYNTHETIC_API_ERROR_PREFIX = "Error: API Error"
    private const val DEFAULT_MAX_TOKENS = 4096L

    fun mapToMessageParams(
        modelId: String,
        prompt: String,
        history: List<ChatMessage>,
        options: GenerationOptions
    ): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(modelId)

        builder.maxTokens((options.maxTokens ?: DEFAULT_MAX_TOKENS.toInt()).toLong())
        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        options.topK?.let { builder.topK(it.toLong()) }
        resolveThinkingBudgetTokens(options)?.let {
            builder.thinking(
                ThinkingConfigEnabled.builder()
                    .budgetTokens(it)
                    .build()
            )
        }

        buildSystemPrompt(history, options.systemPrompt)
            .takeIf { it.isNotBlank() }
            ?.let { builder.system(it) }

        history
            .filterNot(::isSyntheticAssistantError)
            .forEach { message ->
                when (message.role) {
                    Role.SYSTEM -> Unit
                    Role.USER -> builder.addUserMessage(message.content)
                    Role.ASSISTANT -> builder.addAssistantMessage(message.content)
                }
            }

        builder.addUserMessage(prompt)
        if (options.toolingEnabled) {
            options.availableTools.forEach { builder.addTool(it.toAnthropicTool()) }
        }

        return builder.build()
    }

    private fun ToolDefinition.toAnthropicTool(): Tool =
        Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(
                Tool.InputSchema.builder()
                    .type(JsonValue.from("object"))
                    .properties(
                        Tool.InputSchema.Properties.builder()
                            .putAdditionalProperty(
                                "query",
                                JsonValue.from(
                                    mapOf(
                                        "type" to "string"
                                    )
                                )
                            )
                            .build()
                    )
                    .required(listOf("query"))
                    .build()
            )
            .strict(true)
            .build()

    private fun buildSystemPrompt(
        history: List<ChatMessage>,
        systemPrompt: String?
    ): String {
        val parts = buildList {
            systemPrompt
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::add)
            history
                .filterNot(::isSyntheticAssistantError)
                .filter { it.role == Role.SYSTEM }
                .mapNotNull { it.content.trim().takeIf(String::isNotEmpty) }
                .forEach(::add)
        }

        return parts.joinToString(separator = "\n\n")
    }

    private fun resolveThinkingBudgetTokens(options: GenerationOptions): Long? =
        when {
            options.reasoningBudget > 0 -> options.reasoningBudget.toLong().coerceAtLeast(1024L)
            else -> when (options.reasoningEffort) {
                null -> null
                ApiReasoningEffort.LOW -> 1024L
                ApiReasoningEffort.MEDIUM -> 1536L
                ApiReasoningEffort.HIGH -> 2048L
                ApiReasoningEffort.XHIGH -> 3072L
            }
        }

    private fun isSyntheticAssistantError(message: ChatMessage): Boolean =
        message.role == Role.ASSISTANT && message.content.startsWith(SYNTHETIC_API_ERROR_PREFIX)
}
