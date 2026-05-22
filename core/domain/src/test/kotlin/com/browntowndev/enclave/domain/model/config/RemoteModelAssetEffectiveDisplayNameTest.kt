package com.browntowndev.enclave.domain.model.config

import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemoteModelAssetEffectiveDisplayNameTest {
    @Test
    fun `prefers asset level displayName`() {
        val asset =
            RemoteModelAsset(
                displayName = "MiniCPM-V-4.6 1.3B",
                huggingFaceModelName = "openbmb/MiniCPM-V-4.6-Thinking-gguf",
                fileName = "MiniCPM-V-4_6-Thinking-Q8_0.gguf",
                sha256 = "abc",
                configurations =
                    listOf(
                        preset(displayName = "Analytic Drafter"),
                    ),
            )

        assertEquals("MiniCPM-V-4.6 1.3B", asset.effectiveDisplayName())
    }

    @Test
    fun `falls back to first preset displayName when asset label is blank`() {
        val asset =
            RemoteModelAsset(
                displayName = "",
                huggingFaceModelName = "openbmb/MiniCPM-V-4.6-Thinking-gguf",
                fileName = "MiniCPM-V-4_6-Thinking-Q8_0.gguf",
                sha256 = "abc",
                configurations = listOf(preset(displayName = "Analytic Drafter")),
            )

        assertEquals("Analytic Drafter", asset.effectiveDisplayName())
    }

    @Test
    fun `falls back to hugging face tail when no labels are set`() {
        val asset =
            RemoteModelAsset(
                displayName = "",
                huggingFaceModelName = "openbmb/MiniCPM-V-4.6-Thinking-gguf",
                fileName = "MiniCPM-V-4_6-Thinking-Q8_0.gguf",
                sha256 = "abc",
                configurations = emptyList(),
            )

        assertEquals("MiniCPM V 4.6 Thinking gguf", asset.effectiveDisplayName())
    }

    private fun preset(displayName: String) =
        RemoteModelConfiguration(
            configId = LocalModelConfigurationId("cfg-1"),
            displayName = displayName,
            systemPrompt = "Hi",
            temperature = 0.7,
            topK = 40,
            topP = 0.95,
            repetitionPenalty = 1.1,
            maxTokens = 2048,
            contextWindow = 4096,
            defaultAssignments = listOf(ModelType.DRAFT_ONE),
        )
}
