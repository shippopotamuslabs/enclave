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
    /** Sorted active-tool names (options ∪ session cache); drives LiteRT tool-provider rebuild. */
    val activeToolNamesKey: String,
)

internal fun buildConversationSignature(
    modelType: ModelType,
    configId: LocalModelConfigurationId,
    thinkingEnabled: Boolean,
    systemPrompt: String,
    samplerConfig: SamplerConfig,
    options: GenerationOptions?,
    cachedActiveToolNames: List<String> = emptyList(),
): ConversationSignature {
    val activeToolNames =
        buildSet {
            options?.activeTools?.forEach { tool -> add(tool.name) }
            cachedActiveToolNames.forEach { name -> add(name) }
        }
    val activeTools = activeToolNames.mapNotNull(ToolDefinition::fromName)
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
        activeToolNamesKey = activeToolNames.sorted().joinToString(","),
    )
}
