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
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val studioRepository: StudioRepositoryPort,
) : ViewModel() {
    private val userAlbums = studioRepository.observeAllAlbums()
    private val itemAspectRatios = MutableStateFlow<Map<String, Float>>(emptyMap())

    private val mediaItems = studioRepository.observeAllMedia().map { assets ->
        assets.map { asset -> asset.toUi() }
    }

    val uiState: StateFlow<GalleryUiState> = combine(
        mediaItems,
        userAlbums,
        itemAspectRatios,
    ) { media, albums, ratios ->
        val albumItems = mutableMapOf<String, MutableList<StudioMediaUi>>()
        albumItems[DEFAULT_GALLERY_ALBUM_ID] = mutableListOf()
        albums.forEach { albumItems[it.id] = mutableListOf() }

        media.forEach { item ->
            val mappedItem = item.copy(aspectRatio = ratios[item.id])
            val albumId = item.albumId ?: DEFAULT_GALLERY_ALBUM_ID
            albumItems[albumId]?.add(mappedItem)
        }

        GalleryUiState(
            albums = listOf(
                GalleryAlbumUi(
                    id = DEFAULT_GALLERY_ALBUM_ID,
                    name = "Default Album",
                    items = albumItems[DEFAULT_GALLERY_ALBUM_ID] ?: emptyList(),
                ),
            ) + albums.map { album ->
                GalleryAlbumUi(
                    id = album.id,
                    name = album.name,
                    items = albumItems[album.id] ?: emptyList(),
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

        viewModelScope.launch {
            studioRepository.createAlbum(trimmedName)
        }
    }

    fun deleteMedia(mediaIds: Set<String>) {
        viewModelScope.launch {
            mediaIds.forEach { id ->
                studioRepository.deleteMedia(id)
            }
        }
    }

    fun moveMediaToAlbum(mediaIds: Set<String>, targetAlbumId: String) {
        viewModelScope.launch {
            studioRepository.moveMediaToAlbum(mediaIds.toList(), targetAlbumId)
        }
    }

    fun onMediaItemMeasured(id: String, aspectRatio: Float) {
        if (itemAspectRatios.value[id] == aspectRatio) return
        itemAspectRatios.update { currentRatios ->
            currentRatios + (id to aspectRatio)
        }
    }
}
