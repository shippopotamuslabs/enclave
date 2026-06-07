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
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
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

    @Test
    fun imageGenerationSelectionSurfacesSd15EvenWhenPersistedConfigDropsAssignments() {
        // The local registry persists configs without defaultAssignments, so the mapper must
        // backfill the slot from the descriptor or SD1.5/SDXL vanish from the assignment options.
        val mapper = LocalModelAssetUiMapper()
        val sd15 = mapper.map(localAsset("sd15", LocalImageGenerationModelRegistry.SD15_QNN_SHA256, assignments = emptyList()))

        assertTrue(sd15.isImageGenerationCapable)
        assertTrue(sd15.configurations.any { ModelType.IMAGE_GENERATION in it.defaultAssignments })

        val selectable =
            selectableLocalAssetsForSlot(
                modelType = ModelType.IMAGE_GENERATION,
                assets = listOf(sd15),
            )
        assertEquals(listOf(sd15), selectable)
    }

    @Test
    fun juggernautIsAssignableToBothImageEditAndImageGeneration() {
        val mapper = LocalModelAssetUiMapper()
        val juggernaut =
            mapper.map(localAsset("juggernaut", LocalImageGenerationModelRegistry.JUGGERNAUT_XL_QNN_SHA256, assignments = emptyList()))

        assertEquals(
            listOf(juggernaut),
            selectableLocalAssetsForSlot(modelType = ModelType.IMAGE_EDIT, assets = listOf(juggernaut)),
        )
        assertEquals(
            listOf(juggernaut),
            selectableLocalAssetsForSlot(modelType = ModelType.IMAGE_GENERATION, assets = listOf(juggernaut)),
        )
    }

    @Test
    fun sdxlIsAssignableToBothImageGenerationAndImageEdit() {
        val mapper = LocalModelAssetUiMapper()
        val sdxl =
            mapper.map(localAsset("sdxl", LocalImageGenerationModelRegistry.SDXL_QNN_SHA256, assignments = emptyList()))

        assertEquals(
            listOf(sdxl),
            selectableLocalAssetsForSlot(modelType = ModelType.IMAGE_GENERATION, assets = listOf(sdxl)),
        )
        assertEquals(
            listOf(sdxl),
            selectableLocalAssetsForSlot(modelType = ModelType.IMAGE_EDIT, assets = listOf(sdxl)),
        )
    }

    @Test
    fun applyLocalImageAssignmentEligibility_hidesBlockedSd15AndSdxlFromAssignmentOptions() {
        val mapper = LocalModelAssetUiMapper()
        val sd15 = mapper.map(ModelPackTestFixtures.diffusionAsset())
        val sdxl = mapper.map(ModelPackTestFixtures.sdxlDiffusionAsset())
        val blockedPacks =
            listOf(
                ModelPackUi(
                    packId = ModelPackTestFixtures.PACK_ID,
                    displayName = ModelPackTestFixtures.PACK_DISPLAY,
                    completenessLabel = "0/2",
                    installedCount = 0,
                    totalCount = 2,
                    showDownloadAction = false,
                    isDiffusionSoftDeleted = false,
                    disabledReason = "Insufficient RAM for local diffusion (need >= 8 GiB).",
                    isEligible = false,
                ),
                ModelPackUi(
                    packId = ModelPackTestFixtures.SDXL_PACK_ID,
                    displayName = ModelPackTestFixtures.SDXL_PACK_DISPLAY,
                    completenessLabel = "0/2",
                    installedCount = 0,
                    totalCount = 2,
                    showDownloadAction = false,
                    isDiffusionSoftDeleted = false,
                    disabledReason = "Insufficient RAM for local diffusion (need >= 8 GiB).",
                    isEligible = false,
                ),
            )

        val filtered =
            applyLocalImageAssignmentEligibility(
                assets = listOf(sd15, sdxl),
                modelPacks = blockedPacks,
            )
        val selectable =
            selectableLocalAssetsForSlot(
                modelType = ModelType.IMAGE_GENERATION,
                assets = filtered,
                modelPacks = blockedPacks,
            )

        assertTrue(selectable.isEmpty())
    }

    private fun localAsset(
        id: String,
        sha256: String,
        assignments: List<ModelType>? = null,
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
                        defaultAssignments =
                            assignments
                                ?: if (sha256 == LocalImageGenerationModelRegistry.SD15_QNN_SHA256) {
                                    listOf(ModelType.IMAGE_GENERATION)
                                } else {
                                    emptyList()
                                },
                    ),
                ),
        )
    }
}
