package com.browntowndev.enclave.feature.inference.conversation

import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.SamplerConfig
import com.browntowndev.enclave.domain.model.inference.ToolDefinition

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
    val activeTools = options?.activeTools.orEmpty()
    return ConversationSignature(
        modelType = modelType,
        configId = configId,
        thinkingEnabled = thinkingEnabled,
        systemPrompt = systemPrompt,
        temperature = samplerConfig.temperature,
        topP = samplerConfig.topP,
        topK = samplerConfig.topK,
        hasSearchTool = ToolDefinition.WEB_SEARCH in activeTools,
        hasExtractTool = ToolDefinition.WEB_EXTRACT in activeTools,
        hasImageTool = ToolDefinition.ATTACHED_IMAGE_INSPECT in activeTools,
        hasMemoryTools =
            ToolDefinition.SEARCH_CHAT_HISTORY in activeTools ||
                ToolDefinition.SEARCH_CHAT in activeTools,
        hasGetMessageContextTool = ToolDefinition.GET_MESSAGE_CONTEXT in activeTools,
        hasArtifactTools = ToolDefinition.START_ARTIFACT in activeTools,
    )
}
