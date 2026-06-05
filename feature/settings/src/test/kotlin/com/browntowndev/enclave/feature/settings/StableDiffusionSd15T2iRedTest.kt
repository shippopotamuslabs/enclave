package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StableDiffusionSd15T2iRedTest {
    @Test
    fun sdt2i_015_settingsSelectionSurfacesSd15AsLocalImageGenerationOption() {
        val assignment =
            DefaultModelAssignment(
                modelType = ModelType.IMAGE_GENERATION,
                localConfigId = LocalModelConfigurationId("sd15-local-config"),
                displayName = "Stable Diffusion 1.5",
                providerName = "On-Device",
            )
        val ui = assignment.toUi(isMultimodal = false)
        assertEquals(ModelSource.ON_DEVICE, ui.source)
        assertEquals("Stable Diffusion 1.5", ui.currentModelName)
    }

    @Test
    fun imageGenerationSelectionOnlySurfacesSupportedLocalDiffusionAssets() {
        val mapper = LocalModelAssetUiMapper()
        val bonsai = mapper.map(localAsset("bonsai", LocalImageGenerationModelRegistry.SD15_QNN_SHA256))
        val chatModel = mapper.map(localAsset("chat", "ordinary-chat-model-sha"))

        val selectable =
            selectableLocalAssetsForSlot(
                modelType = ModelType.IMAGE_GENERATION,
                assets = listOf(chatModel, bonsai),
            )

        assertTrue(bonsai.isImageGenerationCapable)
        assertFalse(chatModel.isImageGenerationCapable)
        assertEquals(listOf(bonsai), selectable)
    }

    private fun localAsset(
        id: String,
        sha256: String,
    ): LocalModelAsset {
        val modelId = LocalModelId(id)
        return LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = modelId,
                    displayName = id,
                    huggingFaceModelName = "local/$id",
                    remoteFileName = "$id.gguf",
                    localFileName = "$id.gguf",
                    sha256 = sha256,
                    sizeInBytes = 100L,
                    modelFileFormat = ModelFileFormat.GGUF,
                ),
            configurations =
                listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("$id-config"),
                        localModelId = modelId,
                        displayName = "Default",
                        maxTokens = 512,
                        contextWindow = 4096,
                        temperature = 0.7,
                        topP = 0.95,
                        topK = 40,
                        repetitionPenalty = 1.0,
                        systemPrompt = "",
                    ),
                ),
        )
    }
}
