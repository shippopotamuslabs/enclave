package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.TextBlockParam
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

        builder.addMessage(buildUserMessage(prompt, options.imageUris))
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
                    .properties(toolProperties())
                    .required(requiredArguments())
                    .build()
            )
            .strict(true)
            .build()

    private fun ToolDefinition.toolProperties(): Tool.InputSchema.Properties =
        when (this) {
            ToolDefinition.TAVILY_WEB_SEARCH -> Tool.InputSchema.Properties.builder()
                .putAdditionalProperty(
                    "query",
                    JsonValue.from(
                        mapOf(
                            "type" to "string"
                        )
                    )
                )
                .build()
            ToolDefinition.TAVILY_EXTRACT -> Tool.InputSchema.Properties.builder()
                .putAdditionalProperty(
                    "urls",
                    JsonValue.from(
                        mapOf(
                            "type" to "array",
                            "items" to mapOf("type" to "string"),
                            "description" to "List of URLs to extract content from"
                        )
                    )
                )
                .putAdditionalProperty(
                    "extract_depth",
                    JsonValue.from(
                        mapOf(
                            "type" to "string",
                            "description" to "Extraction depth: basic or advanced"
                        )
                    )
                )
                .putAdditionalProperty(
                    "format",
                    JsonValue.from(
                        mapOf(
                            "type" to "string",
                            "description" to "Output format: markdown or text"
                        )
                    )
                )
                .build()
            ToolDefinition.ATTACHED_IMAGE_INSPECT -> Tool.InputSchema.Properties.builder()
                .putAdditionalProperty(
                    "question",
                    JsonValue.from(
                        mapOf(
                            "type" to "string"
                        )
                    )
                )
                .build()
            ToolDefinition.SEARCH_CHAT_HISTORY -> Tool.InputSchema.Properties.builder()
                .putAdditionalProperty(
                    "query",
                    JsonValue.from(
                        mapOf(
                            "type" to "string"
                        )
                    )
                )
                .build()
            ToolDefinition.SEARCH_CHAT -> Tool.InputSchema.Properties.builder()
                .putAdditionalProperty(
                    "chat_id",
                    JsonValue.from(
                        mapOf(
                            "type" to "string",
                            "description" to "The ID of the chat to search"
                        )
                    )
                )
                .putAdditionalProperty(
                    "query",
                    JsonValue.from(
                        mapOf(
                            "type" to "string"
                        )
                    )
                )
                .build()
            else -> error("Unsupported tool: $name")
        }

    private fun ToolDefinition.requiredArguments(): List<String> =
        when (this) {
            ToolDefinition.TAVILY_WEB_SEARCH -> listOf("query")
            ToolDefinition.TAVILY_EXTRACT -> listOf("urls", "extract_depth", "format")
            ToolDefinition.ATTACHED_IMAGE_INSPECT -> listOf("question")
            ToolDefinition.SEARCH_CHAT_HISTORY -> listOf("query")
            ToolDefinition.SEARCH_CHAT -> listOf("chat_id", "query")
            else -> error("Unsupported tool: $name")
        }

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

    private fun buildUserMessage(
        prompt: String,
        imageUris: List<String>,
    ): MessageParam {
        if (imageUris.isEmpty()) {
            return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(prompt)
                .build()
        }

        val blocks = buildList {
            add(ContentBlockParam.ofText(TextBlockParam.builder().text(prompt).build()))
            ImagePayloads.fromUris(imageUris).forEach { payload ->
                ImagePayloads.validate(payload)
                add(
                    ContentBlockParam.ofImage(
                        ImageBlockParam.builder()
                            .source(
                                Base64ImageSource.builder()
                                    .data(payload.base64)
                                    .mediaType(payload.mimeType.toAnthropicMediaType())
                                    .build()
                            )
                            .build()
                    )
                )
            }
        }

        return MessageParam.builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(blocks)
            .build()
    }

    private fun String.toAnthropicMediaType(): Base64ImageSource.MediaType =
        when (lowercase()) {
            "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG
            "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF
            "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP
            else -> Base64ImageSource.MediaType.IMAGE_JPEG
        }
}
