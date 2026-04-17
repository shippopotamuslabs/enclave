package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ModelType

data class LocalModelAsset(
    val metadata: LocalModelMetadata,
    val configurations: List<LocalModelConfiguration>
)

/**
 * Resolves a list of LocalModelAsset into a slot-based map using defaultAssignments
 * from each configuration's RemoteModelConfiguration.
 *
 * Since configurations don't carry defaultAssignments after parsing (they're consumed
 * during resolution), this function accepts an explicit slot-to-config mapping.
 *
 * For each slot, the asset containing the assigned configuration is looked up.
 */
fun resolveSlotAssignments(
    assets: List<LocalModelAsset>,
    slotAssignments: Map<ModelType, LocalModelConfigurationId>
): Map<ModelType, LocalModelAsset> {
    val result = mutableMapOf<ModelType, LocalModelAsset>()
    for ((modelType, configId) in slotAssignments) {
        val asset = assets.find { asset ->
            asset.configurations.any { it.id == configId }
        }
        if (asset != null) {
            result[modelType] = asset
        }
    }
    return result
}
