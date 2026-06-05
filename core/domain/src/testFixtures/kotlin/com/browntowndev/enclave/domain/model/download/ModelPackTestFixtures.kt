package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry

object ModelPackTestFixtures {
    const val PACK_ID = "sd15-t2i"
    const val PACK_DISPLAY = "Stable Diffusion 1.5 (NPU)"
    const val DIFFUSION_FILE = "stable-diffusion-v1-5-Q4_0.gguf"
    const val DIFFUSION_SHA = LocalImageGenerationModelRegistry.SD15_QNN_SHA256
    const val SD15_CONFIG_ID = "sd15-local-config"
    const val PACK_INCOMPLETE_MESSAGE =
        "Local image pack is not fully downloaded. Download all pack files from Local AI Models before assigning."
    const val WHISPER_SHA = "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002"
    const val TOTAL_MEMBERS = 1

    val packMemberShas: Set<String> = parsedCatalog(includeWhisper = false).packMemberSha256sForSlot(ModelType.IMAGE_GENERATION)

    fun diffusionAsset(): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd15-diffusion"),
                    displayName = PACK_DISPLAY,
                    huggingFaceModelName = "gpustack/stable-diffusion-v1-5-GGUF",
                    remoteFileName = DIFFUSION_FILE,
                    localFileName = DIFFUSION_FILE,
                    sha256 = DIFFUSION_SHA,
                    sizeInBytes = 1_747_190_784L,
                    modelFileFormat = ModelFileFormat.GGUF,
                    source = DownloadSource.HUGGING_FACE,
                ),
            configurations =
                listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId(SD15_CONFIG_ID),
                        localModelId = LocalModelId("sd15-diffusion"),
                        displayName = PACK_DISPLAY,
                        maxTokens = 0,
                        contextWindow = 0,
                        temperature = 0.0,
                        topP = 1.0,
                        topK = 1,
                        repetitionPenalty = 1.0,
                        systemPrompt = "",
                        defaultAssignments = listOf(ModelType.IMAGE_GENERATION),
                    ),
                ),
        )

    fun whisperAsset(): LocalModelAsset =
        utilityAsset(
            fileName = "ggml-base.en.bin",
            sha256 = WHISPER_SHA,
            sizeInBytes = 147_964_211L,
            utilityType = com.browntowndev.enclave.domain.model.config.UtilityType.WHISPER,
            format = ModelFileFormat.BIN,
        )

    fun vaeAsset(): LocalModelAsset =
        utilityAsset(
            fileName = "AnythingV5_qnn2.28_8gen2.zip",
            sha256 = "b744c6ec55cf8ce38b641e4b9a1d8d8c9c336f410dca01841be4fe52e290e33f",
            sizeInBytes = 1_057_820_237L,
            utilityType = com.browntowndev.enclave.domain.model.config.UtilityType.SD15_QNN_PACK,
            format = ModelFileFormat.BIN,
        )

    fun encoderAsset(): LocalModelAsset =
        utilityAsset(
            fileName = "encoder-test.bin",
            sha256 = "encoder-test-sha",
            sizeInBytes = 2000L,
            utilityType = com.browntowndev.enclave.domain.model.config.UtilityType.SD15_QNN_PACK,
            format = ModelFileFormat.BIN,
        )

    fun sd15Pack(): ModelPack =
        ModelPack(
            packId = PACK_ID,
            displayName = PACK_DISPLAY,
            slot = ModelType.IMAGE_GENERATION,
            members = listOf(ModelPackMember(ModelPackRole.DIFFUSION, diffusionAsset())),
        )

    fun parsedCatalog(
        includeWhisper: Boolean = true,
        includePack: Boolean = true,
    ): ParsedModelCatalog {
        val assets =
            buildList {
                add(diffusionAsset())
                if (includeWhisper) add(whisperAsset())
            }
        val packs = if (includePack) listOf(sd15Pack()) else emptyList()
        return ParsedModelCatalog(assets = assets, packs = packs)
    }

    private fun utilityAsset(
        fileName: String,
        sha256: String,
        sizeInBytes: Long,
        utilityType: com.browntowndev.enclave.domain.model.config.UtilityType,
        format: ModelFileFormat,
    ): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("utility-$fileName"),
                    displayName = "",
                    huggingFaceModelName = "hf/$fileName",
                    remoteFileName = fileName,
                    localFileName = fileName,
                    sha256 = sha256,
                    sizeInBytes = sizeInBytes,
                    modelFileFormat = format,
                    source = DownloadSource.HUGGING_FACE,
                    utilityType = utilityType,
                ),
            configurations = emptyList(),
        )
}