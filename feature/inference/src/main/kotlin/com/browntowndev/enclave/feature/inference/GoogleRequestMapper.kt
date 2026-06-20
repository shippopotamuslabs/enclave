package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.feature.inference.appendonly.ProviderAppendOnlyContext
import com.browntowndev.enclave.feature.inference.appendonly.ResolvedSidecar
import com.browntowndev.enclave.feature.inference.appendonly.SessionIndexVolatilePlacement
import com.browntowndev.enclave.feature.inference.appendonly.SidecarMarker
import com.google.genai.types.Content
import com.google.genai.types.FunctionCallingConfig
import com.google.genai.types.FunctionCallingConfigMode
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.ThinkingConfig
import com.google.genai.types.Tool
import com.google.genai.types.ToolConfig
import com.google.genai.types.Type

object GoogleRequestMapper {
    private const val SYNTHETIC_API_ERROR_PREFIX = "Error: API Error"

    data class Request(
        val contents: List<Content>,
        val config: GenerateContentConfig,
    )

    fun mapToGenerateContentRequest(
        prompt: String,
        history: List<ChatMessage>,
        options: GenerationOptions,
    ): Request {
        val contents =
            history
                .filterNot(::isSyntheticAssistantError)
                .filter { it.role != Role.SYSTEM }
                .map(::toContent)
                .toMutableList()
                .apply {
                    add(
                        Content
                            .builder()
                            .role("user")
                            .parts(buildUserParts(prompt, options.imageUris))
                            .build(),
                    )
                }

        val builder = GenerateContentConfig.builder()
        options.temperature?.let { builder.temperature(it) }
        options.topP?.let { builder.topP(it) }
        options.topK?.let { builder.topK(it.toFloat()) }
        options.maxTokens?.let { builder.maxOutputTokens(it) }
        resolveThinkingConfig(options)?.let { builder.thinkingConfig(it) }
        applyTools(builder, options)

        buildSystemInstruction(history, options.systemPrompt)
            ?.let { builder.systemInstruction(Content.fromParts(Part.fromText(it))) }

        return Request(
            contents = contents,
            config = builder.build(),
        )
    }

    /**
     * Builds a [Request] for one Rust agent-loop turn. Mirrors
     * `OpenAiRequestMapper.mapSessionToResponseParams` and `AnthropicRequestMapper.mapSessionToMessageParams`:
     * takes the session transcript + stable prefix + tools + sampler options and emits the
     * provider's native request type. Extracted from `GoogleInferenceServiceImpl.completeSingleTurn`
     * so the full request assembly is testable without an SDK client.
     *
     * @param activeCacheName the prompt-cache name resolved by the service's [GooglePromptCacheSession],
     *   or null when no cache is active (system instruction is set on the config instead).
     * @param deltaHistory the stable-history slice the service has not yet cached, in session order.
     */
    @Suppress("MagicNumber")
    internal fun mapSessionToGenerateContent(
        sessionMessages: List<AgentLoopMessage>,
        systemPrompt: String,
        availableTools: List<ToolDefinition>,
        allowToolCall: Boolean,
        options: GenerationOptions,
        appendOnly: ProviderAppendOnlyContext,
        wireSidecar: ResolvedSidecar?,
        activeCacheName: String?,
        deltaHistory: List<AgentLoopMessage>,
    ): Request {
        val lastUserIndex = GoogleAgentLoopContentMapper.lastNonToolResultUserIndex(sessionMessages)
        val imageUris = options.imageUris.take(3600)
        val latestMessage =
            sessionMessages.lastOrNull()
                ?: throw IllegalArgumentException("sessionMessages cannot be empty")
        val latestIndex = sessionMessages.lastIndex
        val liveSidecarInsertIndex = lastUserIndex.coerceAtLeast(0)

        fun sidecarMarkersForIndex(index: Int): List<SidecarMarker> =
            SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(
                sessionIndex = index,
                frozenSidecars = appendOnly.frozenSidecarsForWireReplay(),
                preRetrievalSidecar = null,
                preRetrievalInsertIndex = null,
                liveSidecar = wireSidecar,
                tailInsertIndex = liveSidecarInsertIndex,
            )

        fun mapAtIndex(
            message: AgentLoopMessage,
            index: Int,
            attachImages: Boolean,
        ): Content? =
            GoogleAgentLoopContentMapper.mapMessage(
                message = message,
                index = index,
                lastUserIndex = if (attachImages) lastUserIndex else -1,
                imageUris = if (attachImages) imageUris else emptyList(),
                sidecarMarkers = sidecarMarkersForIndex(index),
            )

        val configBuilder = GenerateContentConfig.builder()
        options.temperature?.let { configBuilder.temperature(it) }
        options.topP?.let { configBuilder.topP(it) }
        options.topK?.let { configBuilder.topK(it.toFloat()) }
        options.maxTokens?.let { configBuilder.maxOutputTokens(it) }
        resolveThinkingConfig(options)?.let { configBuilder.thinkingConfig(it) }

        if (availableTools.isNotEmpty()) {
            configBuilder.tools(availableTools.map { it.toGoogleTool() })
            val mode =
                if (allowToolCall) {
                    FunctionCallingConfigMode.Known.AUTO
                } else {
                    FunctionCallingConfigMode.Known.NONE
                }
            configBuilder.toolConfig(
                ToolConfig
                    .builder()
                    .functionCallingConfig(
                        FunctionCallingConfig
                            .builder()
                            .mode(mode)
                            .build(),
                    ).build(),
            )
        }

        if (activeCacheName != null) {
            configBuilder.cachedContent(activeCacheName)
        } else if (systemPrompt.isNotBlank()) {
            configBuilder.systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
        }

        val contentsToSend =
            buildList {
                deltaHistory.forEach { msg ->
                    val index = sessionMessages.indexOfFirst { it.id == msg.id }
                    mapAtIndex(msg, index, attachImages = false)?.let(::add)
                }
                mapAtIndex(latestMessage, latestIndex, attachImages = true)?.let(::add)
            }

        return Request(
            contents = contentsToSend,
            config = configBuilder.build(),
        )
    }

    private fun toContent(message: ChatMessage): Content =
        Content
            .builder()
            .role(
                when (message.role) {
                    Role.USER -> "user"
                    Role.ASSISTANT -> "model"
                    Role.SYSTEM -> "user"
                },
            ).parts(listOf(Part.fromText(ImagePayloads.stripBase64DataUris(message.content))))
            .build()

    private fun buildSystemInstruction(
        history: List<ChatMessage>,
        systemPrompt: String?,
    ): String? {
        val parts =
            buildList {
                systemPrompt?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
                history
                    .filterNot(::isSyntheticAssistantError)
                    .filter { it.role == Role.SYSTEM }
                    .mapNotNull { it.content.trim().takeIf(String::isNotEmpty) }
                    .forEach(::add)
            }

        return parts
            .joinToString(separator = "\n\n")
            .takeIf { it.isNotBlank() }
    }

    private fun resolveThinkingConfig(options: GenerationOptions): ThinkingConfig? {
        if (options.reasoningBudget <= 0) {
            return null
        }
        return ThinkingConfig
            .builder()
            .thinkingBudget(options.reasoningBudget)
            .includeThoughts(true)
            .build()
    }

    private fun applyTools(
        builder: GenerateContentConfig.Builder,
        options: GenerationOptions,
    ) {
        val toolsToRegister = options.activeTools
        if (!options.toolingEnabled || toolsToRegister.isEmpty()) {
            return
        }

        builder.tools(toolsToRegister.map { it.toGoogleTool() })
        builder.toolConfig(
            ToolConfig
                .builder()
                .functionCallingConfig(
                    FunctionCallingConfig
                        .builder()
                        .mode(FunctionCallingConfigMode.Known.AUTO)
                        .build(),
                ).build(),
        )
    }

    private fun isSyntheticAssistantError(message: ChatMessage): Boolean =
        message.role == Role.ASSISTANT && message.content.startsWith(SYNTHETIC_API_ERROR_PREFIX)

    private fun buildUserParts(
        prompt: String,
        imageUris: List<String>,
    ): List<Part> =
        buildList {
            add(Part.fromText(prompt))
            ImagePayloads.fromUris(imageUris).forEach { payload ->
                ImagePayloads.validate(payload)
                add(Part.fromBytes(payload.bytes, payload.mimeType))
            }
        }
}

internal fun ToolDefinition.toGoogleTool(): Tool =
    Tool
        .builder()
        .functionDeclarations(
            listOf(
                FunctionDeclaration
                    .builder()
                    .name(name)
                    .description(description)
                    .parameters(
                        Schema
                            .builder()
                            .type(Type(Type.Known.OBJECT))
                            .properties(googleToolProperties())
                            .required(schema.required)
                            .build(),
                    ).build(),
            ),
        ).build()

private fun ToolDefinition.googleToolProperties(): Map<String, Schema> {
    val map = mutableMapOf<String, Schema>()
    schema.properties.forEach { (key, value) ->
        map[key] = value.toGoogleSchemaExt()
    }
    return map
}

private fun kotlinx.serialization.json.JsonElement.toGoogleSchemaExt(): Schema {
    val builder = Schema.builder()
    when (this) {
        is kotlinx.serialization.json.JsonObject -> {
            val typeStr = (get("type") as? kotlinx.serialization.json.JsonPrimitive)?.content
            val type =
                when (typeStr) {
                    "string" -> Type(Type.Known.STRING)
                    "integer" -> Type(Type.Known.INTEGER)
                    "number" -> Type(Type.Known.NUMBER)
                    "boolean" -> Type(Type.Known.BOOLEAN)
                    "array" -> Type(Type.Known.ARRAY)
                    "object" -> Type(Type.Known.OBJECT)
                    else -> Type(Type.Known.STRING)
                }
            builder.type(type)
            (get("description") as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { builder.description(it) }
            if (typeStr == "array") {
                get("items")?.toGoogleSchemaExt()?.let { builder.items(it) }
            }
            if (typeStr == "object") {
                val props = mutableMapOf<String, Schema>()
                (get("properties") as? kotlinx.serialization.json.JsonObject)?.forEach { (k, v) -> props[k] = v.toGoogleSchemaExt() }
                builder.properties(props)
                val req =
                    (get("required") as? kotlinx.serialization.json.JsonArray)?.map {
                        (it as kotlinx.serialization.json.JsonPrimitive).content
                    }
                builder.required(req)
            }
        }
        else -> builder.type(Type(Type.Known.STRING))
    }
    return builder.build()
}
