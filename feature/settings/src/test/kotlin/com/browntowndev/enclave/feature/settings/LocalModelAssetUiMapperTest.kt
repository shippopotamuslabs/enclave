package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalModelAssetUiMapperTest {
    @Test
    fun `SCN-UI-001 local model asset ui mapper uses metadata displayName`() {
        val asset =
            LocalModelAsset(
                metadata =
                    LocalModelMetadata(
                        id = LocalModelId("model-1"),
                        displayName = "Gemma 4 E4B",
                        huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm",
                        remoteFileName = "gemma-4-E4B-it.litertlm",
                        localFileName = "gemma-4-E4B-it.litertlm",
                        sha256 = "gemma-sha",
                        sizeInBytes = 100L,
                        modelFileFormat = ModelFileFormat.LITERTLM,
                    ),
                configurations =
                    listOf(
                        LocalModelConfiguration(
                            id = LocalModelConfigurationId("cfg-1"),
                            localModelId = LocalModelId("model-1"),
                            displayName = "Fast",
                            maxTokens = 2048,
                            contextWindow = 8192,
                            temperature = 0.7,
                            topP = 0.9,
                            topK = 40,
                            repetitionPenalty = 1.0,
                            reasoningBudget = 2048,
                            systemPrompt = "",
                        ),
                    ),
            )

        val ui = LocalModelAssetUiMapper().map(asset)

        assertEquals("Gemma 4 E4B", ui.friendlyName)
        assertEquals("2048", ui.configurations.single().reasoningBudget)
    }
}
