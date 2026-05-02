package com.browntowndev.pocketcrew.feature.studio

import androidx.compose.runtime.Immutable
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset

@Immutable
data class StudioMediaUi(
    val id: String,
    val localUri: String,
    val prompt: String,
    val mediaType: MediaCapability,
    val createdAt: Long,
    val albumId: String? = null,
    val aspectRatio: Float? = null,
)

fun StudioMediaAsset.toUi(): StudioMediaUi =
    StudioMediaUi(
        id = id,
        localUri = localUri,
        prompt = prompt,
        mediaType = when (mediaType) {
            MediaCapability.VIDEO.name -> MediaCapability.VIDEO
            MediaCapability.MUSIC.name -> MediaCapability.MUSIC
            else -> MediaCapability.IMAGE
        },
        createdAt = createdAt,
        albumId = albumId,
    )
