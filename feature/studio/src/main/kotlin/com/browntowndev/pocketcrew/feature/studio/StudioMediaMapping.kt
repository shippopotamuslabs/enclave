package com.browntowndev.pocketcrew.feature.studio

import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset

internal fun StudioMediaUi.toAsset(): StudioMediaAsset =
    StudioMediaAsset(
        id = id,
        localUri = localUri,
        prompt = prompt,
        mediaType = mediaType.name,
        createdAt = createdAt,
        albumId = albumId,
    )

internal fun StudioMediaAsset.toMediaCapability(): MediaCapability =
    try {
        MediaCapability.valueOf(mediaType)
    } catch (_: Exception) {
        MediaCapability.IMAGE
    }

internal fun buildRehydratedAssets(
    rehydrateIds: List<String>,
    rehydrateUris: List<String>,
    rehydrateTypes: List<String>,
    rehydratePrompts: List<String>,
): List<StudioMediaAsset> {
    val baseTime = System.currentTimeMillis()
    return rehydrateUris.indices.mapNotNull { index ->
        val uri = rehydrateUris[index]
        val type = rehydrateTypes.getOrNull(index)
        val prompt = rehydratePrompts.getOrNull(index)
        val id = rehydrateIds.getOrNull(index) ?: "rehydrated_${baseTime}_$index"

        StudioMediaAsset(
            id = id,
            localUri = uri,
            prompt = prompt ?: "",
            mediaType = type ?: MediaCapability.IMAGE.name,
            createdAt = baseTime + index,
        )
    }
}
