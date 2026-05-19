package com.browntowndev.pocketcrew.feature.inference.conversation

import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.SamplerConfig
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition

internal data class ConversationSignature(
    val modelType: ModelType,
    val configId: LocalModelConfigurationId,
    val thinkingEnabled: Boolean,
    val systemPrompt: String,
    val temperature: Double,
    val topP: Double,
    val topK: Int,
    val hasSearchTool: Boolean,
    val hasExtractTool: Boolean,
    val hasImageTool: Boolean,
    val hasMemoryTools: Boolean,
    val hasGetMessageContextTool: Boolean,
    val hasArtifactTools: Boolean,
)

internal fun buildConversationSignature(
    modelType: ModelType,
    configId: LocalModelConfigurationId,
    thinkingEnabled: Boolean,
    systemPrompt: String,
    samplerConfig: SamplerConfig,
    options: GenerationOptions?,
): ConversationSignature {
    val availableTools = options?.availableTools.orEmpty()
    return ConversationSignature(
        modelType = modelType,
        configId = configId,
        thinkingEnabled = thinkingEnabled,
        systemPrompt = systemPrompt,
        temperature = samplerConfig.temperature,
        topP = samplerConfig.topP,
        topK = samplerConfig.topK,
        hasSearchTool = ToolDefinition.WEB_SEARCH in availableTools,
        hasExtractTool = ToolDefinition.WEB_EXTRACT in availableTools,
        hasImageTool = ToolDefinition.ATTACHED_IMAGE_INSPECT in availableTools,
        hasMemoryTools =
            ToolDefinition.SEARCH_CHAT_HISTORY in availableTools ||
                ToolDefinition.SEARCH_CHAT in availableTools,
        hasGetMessageContextTool = ToolDefinition.GET_MESSAGE_CONTEXT in availableTools,
        hasArtifactTools = ToolDefinition.START_ARTIFACT in availableTools,
    )
}
