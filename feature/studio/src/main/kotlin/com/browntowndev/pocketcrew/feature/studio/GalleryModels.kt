package com.browntowndev.pocketcrew.feature.studio

import androidx.compose.runtime.Immutable

internal const val DEFAULT_GALLERY_ALBUM_ID = "default"

@Immutable
data class GalleryUiState(
    val albums: List<GalleryAlbumUi> = emptyList(),
)

@Immutable
data class GalleryAlbumUi(
    val id: String,
    val name: String,
    val items: List<StudioMediaUi>,
) {
    val itemCount: Int = items.size
    val coverItems: List<StudioMediaUi> = items.take(4)
}

internal data class GalleryAlbumDraft(
    val id: String,
    val name: String,
)
