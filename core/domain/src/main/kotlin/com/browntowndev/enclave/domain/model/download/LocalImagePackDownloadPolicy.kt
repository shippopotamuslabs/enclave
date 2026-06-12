package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationCapabilities

object LocalImagePackDownloadPolicy {
    private val LOCAL_IMAGE_SLOTS = setOf(ModelType.IMAGE_GENERATION, ModelType.IMAGE_EDIT)

    private val IMAGE_QNN_PACK_TYPES =
        setOf(
            UtilityType.SD15_QNN_PACK,
            UtilityType.SDXL_QNN_PACK,
            UtilityType.JUGGERNAUT_XL_QNN_PACK,
        )

    fun applySlotDownloadSuppression(
        result: DownloadModelsResult,
        suppressImageGenerationSlot: Boolean,
        suppressImageEditSlot: Boolean,
    ): DownloadModelsResult {
        var current = result
        if (suppressImageGenerationSlot) {
            current =
                LocalImagePackMemberFilter.excludePackMembersForSlot(
                    ModelType.IMAGE_GENERATION,
                    current,
                )
        }
        if (suppressImageEditSlot) {
            current =
                LocalImagePackMemberFilter.excludePackMembersForSlot(
                    ModelType.IMAGE_EDIT,
                    current,
                )
        }
        return current
    }

    fun applyTierAwareFilter(
        catalog: ParsedModelCatalog,
        capabilities: LocalImageGenerationCapabilities,
        slotAssignments: Map<ModelType, LocalModelAsset>,
        utilityAssets: List<LocalModelAsset>,
    ): Pair<Map<ModelType, LocalModelAsset>, List<LocalModelAsset>> {
        val blockedShas = blockedPackMemberSha256s(catalog, capabilities)
        if (blockedShas.isEmpty()) {
            return slotAssignments to utilityAssets
        }
        val blockedUtilities = blockedQnnUtilityTypes(capabilities)
        val filteredSlots =
            slotAssignments.filterValues { asset ->
                asset.metadata.sha256 !in blockedShas
            }
        val filteredUtilities =
            utilityAssets.filter { asset ->
                asset.metadata.sha256 !in blockedShas &&
                    asset.metadata.utilityType !in blockedUtilities
            }
        return filteredSlots to filteredUtilities
    }

    fun blockedPackMemberSha256s(
        catalog: ParsedModelCatalog,
        capabilities: LocalImageGenerationCapabilities,
    ): Set<String> {
        if (capabilities.sd15NpuSupported == LocalImageGenTriState.NO &&
            capabilities.sd15CpuSupported != LocalImageGenTriState.YES
        ) {
            return catalog.packs
                .filter { it.slot in LOCAL_IMAGE_SLOTS }
                .flatMap { pack -> pack.members.map { it.asset.metadata.sha256 } }
                .toSet()
        }
        return catalog.packs
            .filter { it.slot in LOCAL_IMAGE_SLOTS }
            .filter { pack -> !isPackEligible(pack.packId, capabilities) }
            .flatMap { pack -> pack.members.map { it.asset.metadata.sha256 } }
            .toSet()
    }

    fun eligibleQnnUtilityTypes(capabilities: LocalImageGenerationCapabilities): Set<UtilityType> =
        buildSet {
            // SD15_QNN_PACK is the SD1.5 weights slot; the download resolver substitutes the
            // MNN CPU zip on tiers whose NPU cannot run the QNN graphs, so the slot stays
            // eligible whenever either backend works.
            if (capabilities.sd15NpuSupported == LocalImageGenTriState.YES ||
                capabilities.sd15CpuSupported == LocalImageGenTriState.YES
            ) {
                add(UtilityType.SD15_QNN_PACK)
            }
            if (capabilities.sdxlNpuSupported == LocalImageGenTriState.YES) {
                add(UtilityType.SDXL_QNN_PACK)
            }
            if (capabilities.juggernautXlNpuSupported == LocalImageGenTriState.YES) {
                add(UtilityType.JUGGERNAUT_XL_QNN_PACK)
            }
        }

    fun isPackEligible(
        packId: String,
        capabilities: LocalImageGenerationCapabilities,
    ): Boolean =
        when (packId) {
            LocalImagePackIds.SD15 ->
            capabilities.sd15NpuSupported == LocalImageGenTriState.YES ||
                capabilities.sd15CpuSupported == LocalImageGenTriState.YES
            LocalImagePackIds.SDXL -> capabilities.sdxlNpuSupported == LocalImageGenTriState.YES
            LocalImagePackIds.JUGGERNAUT -> capabilities.juggernautXlNpuSupported == LocalImageGenTriState.YES
            else -> true
        }

    private fun blockedQnnUtilityTypes(capabilities: LocalImageGenerationCapabilities): Set<UtilityType> =
        IMAGE_QNN_PACK_TYPES - eligibleQnnUtilityTypes(capabilities)
}
