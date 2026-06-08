package com.browntowndev.enclave.feature.inference.conversation

import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.SamplerConfig
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConversationSignatureTest {
    @Test
    fun buildConversationSignature_reflectsActiveTools() {
        val signature =
            buildConversationSignature(
                modelType = ModelType.FAST,
                configId = LocalModelConfigurationId("cfg-1"),
                thinkingEnabled = true,
                systemPrompt = "prompt",
                samplerConfig = SamplerConfig(temperature = 0.5, topP = 0.9, topK = 20),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        activeTools =
                            listOf(
                                ToolDefinition.WEB_SEARCH,
                                ToolDefinition.SEARCH_CHAT,
                                ToolDefinition.START_ARTIFACT,
                            ),
                    ),
            )

        assertTrue(signature.hasSearchTool)
        assertFalse(signature.hasExtractTool)
        assertTrue(signature.hasMemoryTools)
        assertTrue(signature.hasArtifactTools)
        assertEquals("prompt", signature.systemPrompt)
    }

    @Test
    fun buildConversationSignature_activeToolNamesKey_includesCachedDiscoveryActivations() {
        val signature =
            buildConversationSignature(
                modelType = ModelType.FAST,
                configId = LocalModelConfigurationId("cfg-1"),
                thinkingEnabled = false,
                systemPrompt = "prompt",
                samplerConfig = SamplerConfig(temperature = 0.5, topP = 0.9, topK = 20),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        activeTools = listOf(ToolDefinition.SEARCH_TOOLS, ToolDefinition.WEB_SEARCH),
                    ),
                cachedActiveToolNames = listOf("append_blocks"),
            )

        assertEquals("append_blocks,search_tools,web_search", signature.activeToolNamesKey)
    }

    @Test
    fun buildConversationSignature_usesActiveToolsForArtifactFlags() {
        val signature =
            buildConversationSignature(
                modelType = ModelType.FAST,
                configId = LocalModelConfigurationId("cfg-1"),
                thinkingEnabled = true,
                systemPrompt = "prompt",
                samplerConfig = SamplerConfig(temperature = 0.5, topP = 0.9, topK = 20),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        activeTools =
                            listOf(
                                ToolDefinition.SEARCH_TOOLS,
                                ToolDefinition.WEB_SEARCH,
                            ),
                    ),
            )

        assertTrue(signature.hasSearchTool)
        assertFalse(signature.hasArtifactTools)
    }
}
