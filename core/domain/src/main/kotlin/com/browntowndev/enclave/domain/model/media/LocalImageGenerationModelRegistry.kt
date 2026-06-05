package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.config.LocalModelAsset

import com.browntowndev.enclave.domain.model.config.UtilityType

enum class LocalImageGenerationModelId {
    SD15_QNN,
    SD15_CPU,
    SDXL_QNN,
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
        )

    fun descriptorFor(asset: LocalModelAsset): LocalImageGenerationModelDescriptor? =
        descriptors.firstOrNull { desc ->
            asset.metadata.sha256.equals(desc.catalogSha256, ignoreCase = true) &&
                asset.configurations.isNotEmpty()
        }

    fun descriptorForId(id: LocalImageGenerationModelId): LocalImageGenerationModelDescriptor? =
        descriptors.firstOrNull { it.id == id }
}
