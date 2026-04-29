package com.browntowndev.pocketcrew.feature.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    studioRepository: StudioRepositoryPort,
) : ViewModel() {

    private val userAlbums = MutableStateFlow<List<GalleryAlbumDraft>>(emptyList())
    private val mediaItems = studioRepository.observeAllMedia().map { assets ->
        assets.map { asset -> asset.toUi() }
    }

    val uiState: StateFlow<GalleryUiState> = combine(
        mediaItems,
        userAlbums,
    ) { media, albums ->
        GalleryUiState(
            albums = listOf(
                GalleryAlbumUi(
                    id = DEFAULT_GALLERY_ALBUM_ID,
                    name = "Default Album",
                    items = media,
                ),
            ) + albums.map { album ->
                GalleryAlbumUi(
                    id = album.id,
                    name = album.name,
                    items = emptyList(),
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GalleryUiState(
            albums = listOf(
                GalleryAlbumUi(
                    id = DEFAULT_GALLERY_ALBUM_ID,
                    name = "Default Album",
                    items = emptyList(),
                ),
            ),
        ),
    )

    fun addAlbum(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return
        }

        userAlbums.update { currentAlbums ->
            currentAlbums + GalleryAlbumDraft(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
            )
        }
    }
}
