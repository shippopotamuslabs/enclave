package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalStableDiffusionModelPolicy

/**
 * Identifies catalog local-image pack members for download gating when [ParsedModelCatalog] is unavailable.
 */
object LocalImagePackMemberFilter {
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
     * Strips models from the download result that are configured to use an API model.
     *
     * The nested [DownloadModelsResult.scanResult] is filtered too, otherwise
     * [InitializeFileProgressUseCase] would keep reading the unfiltered
     * `missingModels`/`partialDownloads`/`invalidModels` and the download screen would
     * show suppressed LLMs as QUEUED, prompting the user to resume/retry and re-enqueue them.
     */
    fun excludeForApiOnboarding(
        result: DownloadModelsResult,
        apiSlots: Set<ModelType>,
    ): DownloadModelsResult {
        fun isSuppressed(asset: LocalModelAsset): Boolean {
            val assignedSlots = asset.configurations.flatMap { it.defaultAssignments }
            if (assignedSlots.isNotEmpty()) {
                return assignedSlots.all { it in apiSlots }
            }
            val utilityType = asset.metadata.utilityType
            return when (utilityType) {
                UtilityType.SD15_QNN_PACK, UtilityType.SDXL_QNN_PACK ->
                    ModelType.IMAGE_GENERATION in apiSlots
                UtilityType.JUGGERNAUT_XL_QNN_PACK ->
                    ModelType.IMAGE_EDIT in apiSlots
                else -> false
            }
        }

        val suppressedLocalFileNames =
            (result.modelsToDownload + result.utilityAssets + result.availableToRedownload)
                .filter(::isSuppressed)
                .mapTo(mutableSetOf()) { it.metadata.localFileName }

        val filteredScan =
            result.scanResult.copy(
                missingModels = result.scanResult.missingModels.filter { !isSuppressed(it) },
                partialDownloads =
                    result.scanResult.partialDownloads.filterKeys { filename ->
                        filename !in suppressedLocalFileNames
                    },
                invalidModels = result.scanResult.invalidModels.filter { !isSuppressed(it) },
            )

        return result.copy(
            allModels = result.allModels.filterKeys { it !in apiSlots },
            modelsToDownload = result.modelsToDownload.filter { !isSuppressed(it) },
            utilityAssets = result.utilityAssets.filter { !isSuppressed(it) },
            availableToRedownload = result.availableToRedownload.filter { !isSuppressed(it) },
            scanResult = filteredScan,
        )
    }
}
