package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig

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
                        .parts(listOf(Part.fromText(prompt)))
                        .build()
                )
            }

        val builder = GenerateContentConfig.builder()
        options.temperature?.let { builder.temperature(it) }
        options.topP?.let { builder.topP(it) }
        options.topK?.let { builder.topK(it.toFloat()) }
        options.maxTokens?.let { builder.maxOutputTokens(it) }
        resolveThinkingConfig(options)?.let { builder.thinkingConfig(it) }

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
            .parts(listOf(Part.fromText(message.content)))
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

    private fun isSyntheticAssistantError(message: ChatMessage): Boolean =
        message.role == Role.ASSISTANT && message.content.startsWith(SYNTHETIC_API_ERROR_PREFIX)
}
