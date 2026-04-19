package com.browntowndev.pocketcrew.domain.model.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

data class ToolCallRequest(
    val toolName: String,
    val argumentsJson: String,
    val provider: String,
    val modelType: ModelType,
    val chatId: ChatId? = null,
    val userMessageId: MessageId? = null,
) {
    val parameters: ToolParameters by lazy {
        val definition = ToolDefinition.fromName(toolName)
            ?: throw IllegalArgumentException("Unknown tool: $toolName")
        definition.decodeArguments(argumentsJson)
    }
}
