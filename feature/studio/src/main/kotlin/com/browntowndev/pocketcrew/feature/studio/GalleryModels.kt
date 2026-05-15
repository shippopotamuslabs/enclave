package com.browntowndev.pocketcrew.feature.studio

import androidx.compose.runtime.Immutable
import com.browntowndev.pocketcrew.core.ui.model.AlbumSelectionItem

import com.browntowndev.pocketcrew.domain.port.repository.DEFAULT_GALLERY_ALBUM_ID

@Immutable
data class GalleryUiState(
    val albums: List<GalleryAlbumUi> = emptyList(),
    val isLoading: Boolean = false,
    val hasVideoModel: Boolean = false,
)

@Immutable
data class GalleryAlbumUi(
    val id: String,
    val name: String,
    val items: List<StudioMediaUi>,
) {
    val itemCount: Int = items.size
    val coverItems: List<StudioMediaUi> = items.take(4)

    fun toAlbumSelectionItem(): AlbumSelectionItem =
        AlbumSelectionItem(
            id = id,
            name = name,
            itemCount = itemCount,
            coverUri = coverItems.firstOrNull()?.localUri,
            coverMediaType = coverItems.firstOrNull()?.mediaType
        )
}

internal data class GalleryAlbumDraft(
    val id: String,
    val name: String,
)
