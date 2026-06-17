package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.inference.ModelType

data class ParsedModelCatalog(
    val assets: List<LocalModelAsset>,
    val packs: List<ModelPack>,
) {
    fun packById(packId: String): ModelPack? = packs.firstOrNull { it.packId == packId }

    fun memberFileNamesForPack(packId: String): Set<String> =
        packById(packId)?.members?.mapTo(mutableSetOf()) { it.asset.metadata.localFileName } ?: emptySet()

    fun isPackMemberAsset(asset: LocalModelAsset): Boolean =
        packs.any { pack -> pack.members.any { it.asset.metadata.sha256 == asset.metadata.sha256 } }

    fun packMemberSha256sForSlot(slot: ModelType): Set<String> =
        packs
            .filter { it.slot == slot }
            .flatMap { pack -> pack.members.map { it.asset.metadata.sha256 } }
            .toSet()
}

data class DownloadCatalogSlice(
    val slotAssignments: Map<ModelType, LocalModelAsset>,
    val utilityAssets: List<LocalModelAsset>,
    val suppressedPackIds: Set<String>,
    /**
     * Non-utility pack members (e.g. diffusion weights) that lost the single-asset-per-slot
     * competition in [slotAssignments]. They still belong to an unsuppressed pack and must be
     * scanned/downloaded, otherwise a pack like SD 1.5 stays permanently incomplete whenever
     * another pack (SDXL) occupies the same slot.
     */
    val packMemberAssets: List<LocalModelAsset> = emptyList(),
)

fun filterForDownload(
    catalog: ParsedModelCatalog,
    suppressImageGenerationSlot: Boolean,
    suppressImageEditSlot: Boolean = false,
): DownloadCatalogSlice {
    val imageGenPackMemberShas = catalog.packMemberSha256sForSlot(ModelType.IMAGE_GENERATION)
    val imageEditPackMemberShas = catalog.packMemberSha256sForSlot(ModelType.IMAGE_EDIT)
    val suppressedPackIds =
        buildSet {
            if (suppressImageGenerationSlot) {
                catalog.packs
                    .filter { it.slot == ModelType.IMAGE_GENERATION }
                    .forEach { add(it.packId) }
            }
            if (suppressImageEditSlot) {
                catalog.packs
                    .filter { it.slot == ModelType.IMAGE_EDIT }
                    .forEach { add(it.packId) }
            }
        }

    val slotAssignments = mutableMapOf<ModelType, LocalModelAsset>()
    val utilityAssets = mutableListOf<LocalModelAsset>()

    for (asset in catalog.assets) {
        val sha = asset.metadata.sha256
        val isImageGenPackMember = sha in imageGenPackMemberShas
        val isImageEditPackMember = sha in imageEditPackMemberShas
        if (asset.metadata.utilityType != null) {
            val includeUtility =
                when {
                    isImageGenPackMember -> !suppressImageGenerationSlot
                    isImageEditPackMember -> !suppressImageEditSlot
                    else -> true
                }
            if (includeUtility) {
                utilityAssets += asset
            }
        } else {
            for (config in asset.configurations) {
                for (modelType in config.defaultAssignments) {
                    val includeSlot =
                        when (modelType) {
                            ModelType.IMAGE_GENERATION -> !suppressImageGenerationSlot
                            ModelType.IMAGE_EDIT -> !suppressImageEditSlot
                            else -> true
                        }
                    if (includeSlot) {
                        slotAssignments[modelType] = asset
                    }
                }
            }
        }
    }

    val selectedSlotShas = slotAssignments.values.mapTo(mutableSetOf()) { it.metadata.sha256 }
    val packMemberAssets =
        catalog.assets.filter { asset ->
            val sha = asset.metadata.sha256
            asset.metadata.utilityType == null &&
                sha !in selectedSlotShas &&
                (
                    (sha in imageGenPackMemberShas && !suppressImageGenerationSlot) ||
                        (sha in imageEditPackMemberShas && !suppressImageEditSlot)
                )
        }

    return DownloadCatalogSlice(
        slotAssignments = slotAssignments,
        utilityAssets = utilityAssets,
        suppressedPackIds = suppressedPackIds,
        packMemberAssets = packMemberAssets,
    )
}
