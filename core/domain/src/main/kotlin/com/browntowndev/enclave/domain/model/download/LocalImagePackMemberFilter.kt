package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalStableDiffusionModelPolicy

/**
 * Identifies catalog local-image pack members for download gating when [ParsedModelCatalog] is unavailable.
 */
object LocalImagePackMemberFilter {
    private val LOCAL_IMAGE_QNN_UTILITY_TYPES =
        setOf(
            UtilityType.SD15_QNN_PACK,
            UtilityType.SDXL_QNN_PACK,
            UtilityType.JUGGERNAUT_XL_QNN_PACK,
        )

    fun sha256sFromCatalog(
        catalog: ParsedModelCatalog,
        slot: ModelType = ModelType.IMAGE_GENERATION,
    ): Set<String> = catalog.packMemberSha256sForSlot(slot)

    fun sha256sFromAssets(assets: Iterable<LocalModelAsset>): Set<String> =
        assets.filter(LocalStableDiffusionModelPolicy::supports).map { it.metadata.sha256 }.toSet()

    fun excludePackMembersForSlot(
        slot: ModelType,
        result: DownloadModelsResult,
    ): DownloadModelsResult {
        val packMemberShas =
            when (slot) {
                ModelType.IMAGE_GENERATION ->
                    result.imageGenerationPackMemberSha256s.ifEmpty {
                        sha256sFromAssets(result.modelsToDownload + result.utilityAssets)
                    }
                ModelType.IMAGE_EDIT -> result.imageEditPackMemberSha256s
                else -> emptySet()
            }
        if (packMemberShas.isEmpty()) return result
        return result.copy(
            modelsToDownload =
                result.modelsToDownload.filter { asset ->
                    asset.metadata.sha256 !in packMemberShas
                },
            utilityAssets =
                result.utilityAssets.filter { asset ->
                    asset.metadata.sha256 !in packMemberShas
                },
            availableToRedownload =
                result.availableToRedownload.filter { asset ->
                    asset.metadata.sha256 !in packMemberShas
                },
            imageGenerationPackMemberSha256s =
                when (slot) {
                    ModelType.IMAGE_GENERATION -> packMemberShas
                    else -> result.imageGenerationPackMemberSha256s
                },
            imageEditPackMemberSha256s =
                when (slot) {
                    ModelType.IMAGE_EDIT -> packMemberShas
                    else -> result.imageEditPackMemberSha256s
                },
        )
    }

    /**
     * Strips all catalog local-image pack members (both slots) when the user completes API onboarding.
     */
    fun excludeForApiOnboarding(result: DownloadModelsResult): DownloadModelsResult {
        val blockedShas =
            (result.imageGenerationPackMemberSha256s + result.imageEditPackMemberSha256s).ifEmpty {
                sha256sFromAssets(result.modelsToDownload + result.utilityAssets)
            }
        return result.copy(
            allModels = emptyMap(),
            modelsToDownload =
                result.modelsToDownload.filter { asset ->
                    asset.metadata.sha256 !in blockedShas &&
                        asset.metadata.utilityType !in LOCAL_IMAGE_QNN_UTILITY_TYPES
                },
            utilityAssets =
                result.utilityAssets.filter { asset ->
                    asset.metadata.sha256 !in blockedShas &&
                        asset.metadata.utilityType !in LOCAL_IMAGE_QNN_UTILITY_TYPES
                },
            availableToRedownload =
                result.availableToRedownload.filter { asset ->
                    asset.metadata.sha256 !in blockedShas &&
                        asset.metadata.utilityType !in LOCAL_IMAGE_QNN_UTILITY_TYPES
                },
        )
    }
}
