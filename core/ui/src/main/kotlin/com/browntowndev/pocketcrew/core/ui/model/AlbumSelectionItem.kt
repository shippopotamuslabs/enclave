package com.browntowndev.pocketcrew.core.ui.model

import androidx.compose.runtime.Immutable
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability

@Immutable
data class AlbumSelectionItem(
    val id: String,
    val name: String,
    val itemCount: Int,
    val coverUri: String? = null,
    val coverMediaType: MediaCapability? = null
)
