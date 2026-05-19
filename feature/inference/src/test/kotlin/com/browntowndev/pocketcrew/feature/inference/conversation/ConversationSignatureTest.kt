package com.browntowndev.pocketcrew.feature.inference.conversation

import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.SamplerConfig
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConversationSignatureTest {
    @Test
    fun buildConversationSignature_reflectsAvailableTools() {
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
                        availableTools =
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
}
