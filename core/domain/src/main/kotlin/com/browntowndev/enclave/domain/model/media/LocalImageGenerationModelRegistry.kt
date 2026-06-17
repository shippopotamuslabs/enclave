package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.config.LocalModelAsset

import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.inference.ModelType

enum class LocalImageGenerationModelId {
    SD15_QNN,
    SD15_CPU,
    SDXL_QNN,
    JUGGERNAUT_XL_QNN,
}

enum class LocalImageGenerationModelFamily {
    SD15,
    SDXL,
}

enum class LocalImageGenerationInstallState {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED,
    CORRUPT,
    INCOMPATIBLE,
}

data class LocalImageGenerationModelDescriptor(
    val id: LocalImageGenerationModelId,
    val family: LocalImageGenerationModelFamily,
    val displayName: String,
    val packId: String,
    val catalogSha256: String,
    val utilityType: UtilityType?,
    val relativeModelDir: String,
    val preferredBackend: LocalImageGenerationBackendKind,
    val minInstallBytes: Long,
    val version: String,
)

object LocalImageGenerationModelRegistry {
    const val SD15_QNN_SHA256: String =
        "c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b"
    const val SD15_CPU_SHA256: String =
        "0000000000000000000000000000000000000000000000000000000000000001"
    const val SDXL_QNN_SHA256: String =
        "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"
    const val JUGGERNAUT_XL_QNN_SHA256: String =
        "b27231823434c4e4184a61f77401227264a5308c6846c6fc628c37583e76413c"

    val descriptors: List<LocalImageGenerationModelDescriptor> =
        listOf(
            LocalImageGenerationModelDescriptor(
                id = LocalImageGenerationModelId.SD15_QNN,
                family = LocalImageGenerationModelFamily.SD15,
                displayName = "Stable Diffusion 1.5 (NPU)",
                packId = "sd15-t2i",
                catalogSha256 = SD15_QNN_SHA256,
                utilityType = UtilityType.SD15_QNN_PACK,
                relativeModelDir = "sd15-qnn",
                preferredBackend = LocalImageGenerationBackendKind.QNN_NPU,
                minInstallBytes = 1_500_000_000L,
                version = "1",
            ),
            LocalImageGenerationModelDescriptor(
                id = LocalImageGenerationModelId.SD15_CPU,
                family = LocalImageGenerationModelFamily.SD15,
                displayName = "Stable Diffusion 1.5 (CPU)",
                packId = "sd15-cpu-pack",
                catalogSha256 = SD15_CPU_SHA256,
                utilityType = null,
                relativeModelDir = "sd15-cpu",
                preferredBackend = LocalImageGenerationBackendKind.CPU_MNN,
                minInstallBytes = 800_000_000L,
                version = "1",
            ),
            LocalImageGenerationModelDescriptor(
                id = LocalImageGenerationModelId.SDXL_QNN,
                family = LocalImageGenerationModelFamily.SDXL,
                displayName = "Stable Diffusion XL (NPU)",
                packId = "sdxl-t2i",
                catalogSha256 = SDXL_QNN_SHA256,
                utilityType = UtilityType.SDXL_QNN_PACK,
                relativeModelDir = "sdxl-qnn",
                preferredBackend = LocalImageGenerationBackendKind.QNN_NPU,
                minInstallBytes = 3_500_000_000L,
                version = "1",
            ),
            LocalImageGenerationModelDescriptor(
                id = LocalImageGenerationModelId.JUGGERNAUT_XL_QNN,
                family = LocalImageGenerationModelFamily.SDXL,
                displayName = "Juggernaut XL (NPU)",
                packId = "juggernaut-xl-t2i",
                catalogSha256 = JUGGERNAUT_XL_QNN_SHA256,
                utilityType = UtilityType.JUGGERNAUT_XL_QNN_PACK,
                relativeModelDir = "juggernaut-qnn",
                preferredBackend = LocalImageGenerationBackendKind.QNN_NPU,
                minInstallBytes = 3_700_000_000L,
                version = "1",
            ),
        )

    fun descriptorFor(asset: LocalModelAsset): LocalImageGenerationModelDescriptor? =
        descriptors.firstOrNull { desc ->
            asset.metadata.sha256.equals(desc.catalogSha256, ignoreCase = true) &&
                asset.configurations.isNotEmpty()
        }

    fun descriptorForId(id: LocalImageGenerationModelId): LocalImageGenerationModelDescriptor? =
        descriptors.firstOrNull { it.id == id }

    /**
     * Authoritative slot membership for a local image-generation asset. The catalog declares a
     * single default via `defaultAssignments`, but that field is not persisted to the local
     * registry, so the settings UI must derive the *assignable* slots from the descriptor instead.
     * This is broader than the catalog default: the SDXL-family models (SDXL, Juggernaut XL) are
     * full diffusion models that handle both text-to-image (IMAGE_GENERATION) and image-edit
     * (IMAGE_EDIT), so users may assign either to either slot. SD1.5 fills text-to-image only.
     * (Hardware eligibility and img2img-encoder presence are enforced downstream at assignment.)
     */
    fun assignableSlotsFor(asset: LocalModelAsset): List<ModelType> =
        when (descriptorFor(asset)?.id) {
            LocalImageGenerationModelId.SD15_QNN,
            LocalImageGenerationModelId.SD15_CPU,
            -> listOf(ModelType.IMAGE_GENERATION)
            LocalImageGenerationModelId.SDXL_QNN,
            LocalImageGenerationModelId.JUGGERNAUT_XL_QNN,
            -> listOf(ModelType.IMAGE_GENERATION, ModelType.IMAGE_EDIT)
            null -> emptyList()
        }

    /**
     * Preference for filling the IMAGE_GENERATION slot when more than one local text-to-image
     * model is installable on the device: SDXL (RealVis) outranks SD1.5. Higher wins. Device
     * eligibility is gated upstream, so a model only reaches assignment when it is supported.
     * Models that never fill the IMAGE_GENERATION slot (e.g. the IMAGE_EDIT-only Juggernaut)
     * rank 0.
     */
    fun imageGenerationSlotPriority(id: LocalImageGenerationModelId?): Int =
        when (id) {
            LocalImageGenerationModelId.SDXL_QNN -> 2
            LocalImageGenerationModelId.SD15_QNN,
            LocalImageGenerationModelId.SD15_CPU,
            -> 1
            LocalImageGenerationModelId.JUGGERNAUT_XL_QNN,
            null,
            -> 0
        }
}
