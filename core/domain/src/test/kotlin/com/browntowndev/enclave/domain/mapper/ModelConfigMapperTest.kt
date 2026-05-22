package com.browntowndev.enclave.domain.mapper

import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.RemoteModelAsset
import com.browntowndev.enclave.domain.model.config.RemoteModelConfiguration
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModelConfigMapperTest {
    private val mapper = ModelConfigMapper()

    @Test
    fun `SCN-MAPPER-001 model config mapper uses asset display name`() {
        val asset =
            RemoteModelAsset(
                displayName = "Gemma 4 E4B",
                fileName = "gemma-4-E4B-it.litertlm",
                sha256 = "gemma-sha",
                configurations =
                    listOf(
                        RemoteModelConfiguration(
                            configId = LocalModelConfigurationId("cfg-fast"),
                            displayName = "Fast",
                            systemPrompt = "Be quick.",
                            temperature = 0.8,
                            topK = 50,
                            topP = 0.9,
                            repetitionPenalty = 1.05,
                            maxTokens = 2048,
                            contextWindow = 8192,
                            defaultAssignments = listOf(ModelType.FAST),
                        ),
                    ),
            )

        val files = mapper.toModelFiles(listOf(asset))

        assertEquals("Gemma 4 E4B", files[0].displayName)
    }
}
