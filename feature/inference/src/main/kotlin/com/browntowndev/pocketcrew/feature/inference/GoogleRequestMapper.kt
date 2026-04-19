package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
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
        val contents = history
            .filterNot(::isSyntheticAssistantError)
            .filter { it.role != Role.SYSTEM }
            .map(::toContent)
            .toMutableList()
            .apply {
                add(
                    Content.builder()
                        .role("user")
                        .parts(buildUserParts(prompt, options.imageUris))
                        .build()
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

    private fun toContent(message: ChatMessage): Content =
        Content.builder()
            .role(
                when (message.role) {
                    Role.USER -> "user"
                    Role.ASSISTANT -> "model"
                    Role.SYSTEM -> "user"
                }
            )
            .parts(listOf(Part.fromText(ImagePayloads.stripBase64DataUris(message.content))))
            .build()

    private fun buildSystemInstruction(
        history: List<ChatMessage>,
        systemPrompt: String?,
    ): String? {
        val parts = buildList {
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
        return ThinkingConfig.builder()
            .thinkingBudget(options.reasoningBudget)
            .includeThoughts(true)
            .build()
    }

    private fun applyTools(
        builder: GenerateContentConfig.Builder,
        options: GenerationOptions,
    ) {
        if (!options.toolingEnabled || options.availableTools.isEmpty()) {
            return
        }

        builder.tools(options.availableTools.map { it.toGoogleTool() })
        builder.toolConfig(
            ToolConfig.builder()
                .functionCallingConfig(
                    FunctionCallingConfig.builder()
                        .mode(FunctionCallingConfigMode.Known.ANY)
                        .allowedFunctionNames(options.availableTools.map(ToolDefinition::name))
                        .build()
                )
                .build()
        )
    }

    private fun ToolDefinition.toGoogleTool(): Tool =
        Tool.builder()
            .functionDeclarations(
                listOf(
                    FunctionDeclaration.builder()
                        .name(name)
                        .description(description)
                        .parameters(
                            Schema.builder()
                                .type(Type(Type.Known.OBJECT))
                                .properties(toolProperties())
                                .required(schema.required)
                                .build()
                        )
                        .build()
                )
            )
            .build()

    private fun ToolDefinition.toolProperties(): Map<String, Schema> {
        val map = mutableMapOf<String, Schema>()
        schema.properties.forEach { (key, value) ->
            map[key] = value.toGoogleSchema()
        }
        return map
    }

    private fun kotlinx.serialization.json.JsonElement.toGoogleSchema(): Schema {
        val builder = Schema.builder()
        when (this) {
            is kotlinx.serialization.json.JsonObject -> {
                val typeStr = (get("type") as? kotlinx.serialization.json.JsonPrimitive)?.content
                val type = when (typeStr) {
                    "string" -> Type(Type.Known.STRING)
                    "integer" -> Type(Type.Known.INTEGER)
                    "number" -> Type(Type.Known.NUMBER)
                    "boolean" -> Type(Type.Known.BOOLEAN)
                    "array" -> Type(Type.Known.ARRAY)
                    "object" -> Type(Type.Known.OBJECT)
                    else -> Type(Type.Known.STRING)
                }
                builder.type(type)

                (get("description") as? kotlinx.serialization.json.JsonPrimitive)?.content?.let {
                    builder.description(it)
                }

                if (typeStr == "array") {
                    get("items")?.toGoogleSchema()?.let { builder.items(it) }
                }

                if (typeStr == "object") {
                    val props = mutableMapOf<String, Schema>()
                    (get("properties") as? kotlinx.serialization.json.JsonObject)?.forEach { (k, v) ->
                        props[k] = v.toGoogleSchema()
                    }
                    builder.properties(props)

                    val req = (get("required") as? kotlinx.serialization.json.JsonArray)?.map {
                        (it as kotlinx.serialization.json.JsonPrimitive).content
                    }
                    builder.required(req)
                }
                
                // Google SDK doesn't seem to have a direct .enum() method on Schema.Builder in this version.
                // We'll skip it for now or find the correct method.
            }
            else -> builder.type(Type(Type.Known.STRING))
        }
        return builder.build()
    }

    private fun isSyntheticAssistantError(message: ChatMessage): Boolean =
        message.role == Role.ASSISTANT && message.content.startsWith(SYNTHETIC_API_ERROR_PREFIX)

    private fun buildUserParts(
        prompt: String,
        imageUris: List<String>,
    ): List<Part> = buildList {
        add(Part.fromText(prompt))
        ImagePayloads.fromUris(imageUris).forEach { payload ->
            ImagePayloads.validate(payload)
            add(Part.fromBytes(payload.bytes, payload.mimeType))
        }
    }
}
