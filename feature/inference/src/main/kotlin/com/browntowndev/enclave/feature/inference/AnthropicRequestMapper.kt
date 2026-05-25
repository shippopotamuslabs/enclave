package com.browntowndev.enclave.feature.inference

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigEnabled
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.CacheControlEphemeral
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ToolDefinition

object AnthropicRequestMapper {
    private const val SYNTHETIC_API_ERROR_PREFIX = "Error: API Error"
    private const val DEFAULT_MAX_TOKENS = 4096L
    private const val THINKING_BUDGET_MEDIUM = 1536L
    private const val THINKING_BUDGET_XHIGH = 3072L

    @Suppress("CyclomaticComplexMethod")
    fun mapToMessageParams(
        modelId: String,
        prompt: String,
        history: List<ChatMessage>,
        options: GenerationOptions,
    ): MessageCreateParams {
        val provider = options.apiProvider
        val optModelId = options.modelId
        val parameterSupport =
            if (provider != null && optModelId != null) {
                ApiProviderModelPolicy.parameterSupport(provider, optModelId)
            } else {
                null
            }

        val builder =
            MessageCreateParams
                .builder()
                .model(modelId)

        if (!options.maxTokensDisabled && parameterSupport?.supportsMaxTokens != false) {
            builder.maxTokens((options.maxTokens ?: DEFAULT_MAX_TOKENS.toInt()).toLong())
        } else {
            builder.maxTokens(DEFAULT_MAX_TOKENS)
        }

        if (!options.temperatureDisabled && parameterSupport?.supportsTemperature != false) {
            options.temperature?.let { builder.temperature(it.toDouble()) }
        }
        if (!options.topPDisabled && parameterSupport?.supportsTopP != false) {
            options.topP?.let { builder.topP(it.toDouble()) }
        }
        if (!options.topKDisabled && parameterSupport?.supportsTopK != false) {
            options.topK?.let { builder.topK(it.toLong()) }
        }

        resolveThinkingBudgetTokens(options)?.let {
            builder.thinking(
                ThinkingConfigEnabled
                    .builder()
                    .budgetTokens(it)
                    .build(),
            )
        }

        val systemPromptStr = buildSystemPrompt(history, options.systemPrompt)
        val useCaching = AnthropicPromptCacheSupport.supportsPromptCaching(modelId)
        if (systemPromptStr.isNotBlank()) {
            if (useCaching) {
                val systemBlock = TextBlockParam.builder()
                    .text(systemPromptStr)
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build()
                builder.systemOfTextBlockParams(listOf(systemBlock))
            } else {
                builder.system(systemPromptStr)
            }
        }

        val activeHistory = history.filterNot(::isSyntheticAssistantError)
        activeHistory.forEachIndexed { index, message ->
            val isLast = index == activeHistory.size - 1
            val sanitizedContent = ImagePayloads.stripBase64DataUris(message.content)
            if (useCaching && isLast) {
                val textBlockBuilder = TextBlockParam.builder().text(sanitizedContent)
                    .cacheControl(CacheControlEphemeral.builder().build())
                val role = when (message.role) {
                    Role.USER -> MessageParam.Role.USER
                    Role.ASSISTANT -> MessageParam.Role.ASSISTANT
                    Role.SYSTEM -> null
                }
                if (role != null) {
                    builder.addMessage(
                        MessageParam.builder()
                            .role(role)
                            .contentOfBlockParams(listOf(ContentBlockParam.ofText(textBlockBuilder.build())))
                            .build()
                    )
                }
            } else {
                when (message.role) {
                    Role.SYSTEM -> Unit
                    Role.USER -> builder.addUserMessage(sanitizedContent)
                    Role.ASSISTANT -> builder.addAssistantMessage(sanitizedContent)
                }
            }
        }

        builder.addMessage(buildUserMessage(prompt, options.imageUris))
        if (options.toolingEnabled && options.activeTools.isNotEmpty()) {
            options.activeTools.forEachIndexed { idx, toolDef ->
                val isLast = idx == options.activeTools.size - 1
                val cacheControl = if (useCaching && isLast) CacheControlEphemeral.builder().build() else null
                builder.addTool(toolDef.toAnthropicTool(cacheControl))
            }
        }

        return builder.build()
    }

    private fun buildSystemPrompt(
        history: List<ChatMessage>,
        systemPrompt: String?,
    ): String {
        val parts =
            buildList {
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
            else ->
                when (options.reasoningEffort) {
                    null -> null
                    ApiReasoningEffort.NONE -> null
                    ApiReasoningEffort.LOW -> 1024L
                    ApiReasoningEffort.MEDIUM -> THINKING_BUDGET_MEDIUM
                    ApiReasoningEffort.HIGH -> 2048L
                    ApiReasoningEffort.XHIGH -> THINKING_BUDGET_XHIGH
                }
        }

    private fun isSyntheticAssistantError(message: ChatMessage): Boolean =
        message.role == Role.ASSISTANT && message.content.startsWith(SYNTHETIC_API_ERROR_PREFIX)

    private fun buildUserMessage(
        prompt: String,
        imageUris: List<String>,
    ): MessageParam {
        if (imageUris.isEmpty()) {
            return MessageParam
                .builder()
                .role(MessageParam.Role.USER)
                .content(prompt)
                .build()
        }

        val blocks =
            buildList {
                add(ContentBlockParam.ofText(TextBlockParam.builder().text(prompt).build()))
                ImagePayloads.fromUris(imageUris).forEach { payload ->
                    ImagePayloads.validate(payload)
                    add(
                        ContentBlockParam.ofImage(
                            ImageBlockParam
                                .builder()
                                .source(
                                    Base64ImageSource
                                        .builder()
                                        .data(payload.base64)
                                        .mediaType(payload.mimeType.toAnthropicMediaType())
                                        .build(),
                                ).build(),
                        ),
                    )
                }
            }

        return MessageParam
            .builder()
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

internal fun ToolDefinition.toAnthropicTool(cacheControl: CacheControlEphemeral? = null): Tool {
    val builder = Tool
        .builder()
        .name(name)
        .description(description)
        .inputSchema(
            Tool.InputSchema
                .builder()
                .type(JsonValue.from("object"))
                .properties(anthropicToolProperties())
                .required(schema.required)
                .build(),
        ).strict(true)
    if (cacheControl != null) {
        builder.cacheControl(cacheControl)
    }
    return builder.build()
}

private fun ToolDefinition.anthropicToolProperties(): Tool.InputSchema.Properties {
    val builder = Tool.InputSchema.Properties.builder()
    schema.properties.forEach { (key, value) ->
        builder.putAdditionalProperty(key, value.toAnthropicJsonValueExt())
    }
    return builder.build()
}

private fun kotlinx.serialization.json.JsonElement.toAnthropicJsonValueExt(): JsonValue =
    when (this) {
        is kotlinx.serialization.json.JsonPrimitive -> {
            if (isString) {
                JsonValue.from(content)
            } else {
                content.toBooleanStrictOrNull()?.let { JsonValue.from(it) }
                    ?: content.toLongOrNull()?.let { JsonValue.from(it) }
                    ?: content.toDoubleOrNull()?.let { JsonValue.from(it) }
                    ?: JsonValue.from(content)
            }
        }
        is kotlinx.serialization.json.JsonObject -> {
            val map = mutableMapOf<String, JsonValue>()
            forEach { (k, v) -> map[k] = v.toAnthropicJsonValueExt() }
            JsonValue.from(map)
        }
        is kotlinx.serialization.json.JsonArray -> {
            val list = map { it.toAnthropicJsonValueExt() }
            JsonValue.from(list)
        }
        else -> JsonValue.from(null as String?)
    }
