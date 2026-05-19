package com.browntowndev.pocketcrew.feature.studio.coordinators

import com.browntowndev.pocketcrew.domain.port.repository.DEFAULT_GALLERY_ALBUM_ID
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.feature.studio.GalleryAlbumUi
import com.browntowndev.pocketcrew.feature.studio.StudioMediaUi
import com.browntowndev.pocketcrew.feature.studio.toUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class StudioAlbumSelectionCoordinator
    @Inject
    constructor(
        private val studioRepository: StudioRepositoryPort,
    ) {
        private val _sessionSavedAssetIds = MutableStateFlow<Set<String>>(emptySet())
        val sessionSavedAssetIds: StateFlow<Set<String>> = _sessionSavedAssetIds.asStateFlow()

        private val _selectedMediaItemIds = MutableStateFlow<Set<String>>(emptySet())
        val selectedMediaItemIds: StateFlow<Set<String>> = _selectedMediaItemIds.asStateFlow()

        private val _isSaveBottomSheetOpen = MutableStateFlow(false)
        val isSaveBottomSheetOpen: StateFlow<Boolean> = _isSaveBottomSheetOpen.asStateFlow()

        fun sessionMediaFlow(scope: CoroutineScope): StateFlow<List<StudioMediaUi>> =
            studioRepository
                .observeEphemeralMedia()
                .map { assets -> assets.map { it.toUi() } }
                .stateIn(
                    scope = scope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                    initialValue = emptyList(),
                )

        val albumsFlow: Flow<List<GalleryAlbumUi>> =
            combine(
                studioRepository.observeAllAlbums(),
                studioRepository.observeAllMedia(),
            ) { albums, allMedia ->
                val albumItems = mutableMapOf<String, MutableList<StudioMediaUi>>()
                albumItems[DEFAULT_GALLERY_ALBUM_ID] = mutableListOf()
                albums.forEach { albumItems[it.id] = mutableListOf() }

                allMedia.forEach { asset ->
                    val item =
                        asset.toUi().copy(
                            albumId = asset.albumId ?: DEFAULT_GALLERY_ALBUM_ID,
                        )
                    val albumId = asset.albumId ?: DEFAULT_GALLERY_ALBUM_ID
                    albumItems[albumId]?.add(item)
                }

                listOf(
                    GalleryAlbumUi(
                        id = DEFAULT_GALLERY_ALBUM_ID,
                        name = DEFAULT_ALBUM_NAME,
                        items = albumItems[DEFAULT_GALLERY_ALBUM_ID] ?: emptyList(),
                    ),
                ) +
                    albums.map { album ->
                        GalleryAlbumUi(
                            id = album.id,
                            name = album.name,
                            items = albumItems[album.id] ?: emptyList(),
                        )
                    }
            }

        fun toggleMediaSelection(id: String) {
            _selectedMediaItemIds.update { current ->
                if (current.contains(id)) current - id else current + id
            }
        }

        fun clearMediaSelection() {
            _selectedMediaItemIds.value = emptySet()
        }

        fun onToggleSaveBottomSheet() {
            _isSaveBottomSheetOpen.value = !_isSaveBottomSheetOpen.value
        }

        fun onAddAlbum(
            scope: CoroutineScope,
            name: String,
        ) {
            scope.launch {
                studioRepository.createAlbum(name)
            }
        }

        fun onSaveSelectedMediaToAlbum(
            scope: CoroutineScope,
            albumId: String,
        ) {
            val selectedIds = _selectedMediaItemIds.value
            if (selectedIds.isEmpty()) return

            scope.launch {
                val currentEphemeral = studioRepository.observeEphemeralMedia().first()
                currentEphemeral
                    .filter { it.id in selectedIds }
                    .distinctBy { it.id }
                    .forEach { item ->
                        val savedAsset = studioRepository.promoteEphemeralMedia(item.id, albumId)
                        _sessionSavedAssetIds.value = _sessionSavedAssetIds.value + savedAsset.id
                    }

                clearMediaSelection()
                onToggleSaveBottomSheet()
            }
        }

        fun clearSessionSavedAssets() {
            _sessionSavedAssetIds.value = emptySet()
            _selectedMediaItemIds.value = emptySet()
        }

        private companion object {
            private const val SUBSCRIBE_TIMEOUT_MS = 5_000L
            private const val DEFAULT_ALBUM_NAME = "Default Album"
        }
    }
