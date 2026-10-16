package com.browntowndev.enclave.feature.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.DEFAULT_GALLERY_ALBUM_ID
import com.browntowndev.enclave.domain.usecase.studio.CreateStudioAlbumUseCase
import com.browntowndev.enclave.domain.usecase.studio.DeleteStudioAlbumUseCase
import com.browntowndev.enclave.domain.usecase.studio.DeleteStudioMediaUseCase
import com.browntowndev.enclave.domain.usecase.studio.MoveStudioMediaToAlbumUseCase
import com.browntowndev.enclave.domain.usecase.studio.ObserveDefaultModelAssignmentsUseCase
import com.browntowndev.enclave.domain.usecase.studio.ObserveStudioAlbumsUseCase
import com.browntowndev.enclave.domain.usecase.studio.ObserveStudioMediaUseCase
import com.browntowndev.enclave.domain.usecase.studio.RenameStudioAlbumUseCase
import com.browntowndev.enclave.domain.usecase.studio.ShareStudioMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel
    @Inject
    constructor(
        private val observeStudioAlbums: ObserveStudioAlbumsUseCase,
        private val observeStudioMedia: ObserveStudioMediaUseCase,
        private val shareStudioMedia: ShareStudioMediaUseCase,
        private val observeDefaultModelAssignments: ObserveDefaultModelAssignmentsUseCase,
        private val createStudioAlbum: CreateStudioAlbumUseCase,
        private val renameStudioAlbum: RenameStudioAlbumUseCase,
        private val deleteStudioMedia: DeleteStudioMediaUseCase,
        private val deleteStudioAlbum: DeleteStudioAlbumUseCase,
        private val moveStudioMediaToAlbum: MoveStudioMediaToAlbumUseCase,
    ) : ViewModel() {
        private val userAlbums = observeStudioAlbums()
        private val itemAspectRatios = MutableStateFlow<Map<String, Float>>(emptyMap())

        private val mediaItems =
            observeStudioMedia().map { assets ->
                assets.map { asset -> asset.toUi() }
            }

        private val hasImageToVideoModelFlow =
            observeDefaultModelAssignments()
                .map { assignments ->
                    assignments.any { assignment ->
                        assignment.modelType == ModelType.IMAGE_TO_VIDEO && assignment.mediaProviderId != null
                    }
                }.distinctUntilChanged()

        val uiState: StateFlow<GalleryUiState> =
            combine(
                mediaItems,
                userAlbums,
                itemAspectRatios,
                hasImageToVideoModelFlow,
            ) { media, albums, ratios, hasImageToVideoModel ->
                val albumItems = mutableMapOf<String, MutableList<StudioMediaUi>>()
                albumItems[DEFAULT_GALLERY_ALBUM_ID] = mutableListOf()
                albums.forEach { albumItems[it.id] = mutableListOf() }

                media.forEach { item ->
                    val mappedItem = item.copy(aspectRatio = ratios[item.id])
                    val albumId = item.albumId ?: DEFAULT_GALLERY_ALBUM_ID
                    albumItems[albumId]?.add(mappedItem)
                }

                GalleryUiState(
                    albums =
                        listOf(
                            GalleryAlbumUi(
                                id = DEFAULT_GALLERY_ALBUM_ID,
                                name = "Default Album",
                                items = albumItems[DEFAULT_GALLERY_ALBUM_ID] ?: emptyList(),
                            ),
                        ) +
                            albums.map { album ->
                                GalleryAlbumUi(
                                    id = album.id,
                                    name = album.name,
                                    items = albumItems[album.id] ?: emptyList(),
                                )
                            },
                    isLoading = false,
                    hasImageToVideoModel = hasImageToVideoModel,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    GalleryUiState(
                        albums =
                            listOf(
                                GalleryAlbumUi(
                                    id = DEFAULT_GALLERY_ALBUM_ID,
                                    name = "Default Album",
                                    items = emptyList(),
                                ),
                            ),
                        isLoading = true,
                    ),
            )

        fun addAlbum(name: String) {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) {
                return
            }

            viewModelScope.launch {
                createStudioAlbum(trimmedName)
            }
        }

        fun renameAlbum(
            albumId: String,
            newName: String,
        ) {
            val trimmedName = newName.trim()
            if (trimmedName.isEmpty()) {
                return
            }
            viewModelScope.launch {
                renameStudioAlbum(albumId, trimmedName)
            }
        }

        fun deleteMedia(mediaIds: Set<String>) {
            viewModelScope.launch {
                mediaIds.forEach { id ->
                    deleteStudioMedia(id)
                }
            }
        }

        fun deleteAlbums(albumIds: Set<String>) {
            viewModelScope.launch {
                albumIds.forEach { id ->
                    deleteStudioAlbum(id)
                }
            }
        }

        fun shareMedia(mediaIds: Set<String>) {
            viewModelScope.launch {
                val uris = mutableListOf<String>()
                val mediaList = uiState.value.albums.flatMap { it.items }

                mediaIds.forEach { id ->
                    val asset = mediaList.find { it.id == id }
                    if (asset != null) {
                        uris.add(asset.localUri)
                    }
                }

                if (uris.isNotEmpty()) {
                    shareStudioMedia(uris, "*/*")
                }
            }
        }

        fun shareSingleMedia(mediaId: String) {
            viewModelScope.launch {
                val asset =
                    uiState.value.albums
                        .flatMap { it.items }
                        .find { it.id == mediaId }
                if (asset != null) {
                    val mimeType =
                        when (asset.mediaType) {
                            MediaCapability.IMAGE -> "image/*"
                            MediaCapability.VIDEO -> "video/*"
                            MediaCapability.MUSIC -> "audio/*"
                        }
                    shareStudioMedia(listOf(asset.localUri), mimeType)
                }
            }
        }

        fun moveMediaToAlbum(
            mediaIds: Set<String>,
            targetAlbumId: String,
        ) {
            viewModelScope.launch {
                moveStudioMediaToAlbum(mediaIds.toList(), targetAlbumId)
            }
        }

        fun onMediaItemMeasured(
            id: String,
            aspectRatio: Float,
        ) {
            if (itemAspectRatios.value[id] == aspectRatio) return
            itemAspectRatios.update { currentRatios ->
                currentRatios + (id to aspectRatio)
            }
        }
    }
