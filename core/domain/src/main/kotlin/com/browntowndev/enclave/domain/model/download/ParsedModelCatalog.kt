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
)

fun filterForDownload(
    catalog: ParsedModelCatalog,
    imageGenerationIsApi: Boolean,
): DownloadCatalogSlice {
    val imageGenPacks = catalog.packs.filter { it.slot == ModelType.IMAGE_GENERATION }
    val imageGenPackMemberShas = catalog.packMemberSha256sForSlot(ModelType.IMAGE_GENERATION)
    val suppressedPackIds =
        if (imageGenerationIsApi) {
            imageGenPacks.map { it.packId }.toSet()
        } else {
            emptySet()
        }

    val slotAssignments = mutableMapOf<ModelType, LocalModelAsset>()
    val utilityAssets = mutableListOf<LocalModelAsset>()

    for (asset in catalog.assets) {
        val isImageGenPackMember = asset.metadata.sha256 in imageGenPackMemberShas
        if (asset.metadata.utilityType != null) {
            if (!isImageGenPackMember) {
                utilityAssets += asset
            }
        } else {
            for (config in asset.configurations) {
                for (modelType in config.defaultAssignments) {
                    val includeSlot =
                        modelType != ModelType.IMAGE_GENERATION || !imageGenerationIsApi
                    if (includeSlot) {
                        slotAssignments[modelType] = asset
                    }
                }
            }
        }
    }

    return DownloadCatalogSlice(
        slotAssignments = slotAssignments,
        utilityAssets = utilityAssets,
        suppressedPackIds = suppressedPackIds,
    )
}
