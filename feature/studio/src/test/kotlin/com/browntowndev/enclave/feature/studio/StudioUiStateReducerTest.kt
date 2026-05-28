package com.browntowndev.enclave.feature.studio

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.repository.DEFAULT_GALLERY_ALBUM_ID
import com.browntowndev.enclave.feature.studio.coordinators.StudioUiStateReducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StudioUiStateReducerTest {
    private val reducer = StudioUiStateReducer()

    @Test
    fun reduce_buildsGroupedGalleryFromSessionMedia() {
        val sessionMedia =
            listOf(
                StudioMediaUi(
                    id = "1",
                    localUri = "file://a.jpg",
                    prompt = "sunset",
                    mediaType = MediaCapability.IMAGE,
                    createdAt = 2L,
                ),
                StudioMediaUi(
                    id = "2",
                    localUri = "file://b.jpg",
                    prompt = "sunset",
                    mediaType = MediaCapability.IMAGE,
                    createdAt = 1L,
                ),
            )

        val state =
            reducer.reduce(
                StudioUiStateReducer.ReduceParams(
                    prompt = "next",
                    isGenerating = false,
                    mediaType = MediaCapability.IMAGE,
                    sessionMedia = sessionMedia,
                    sessionSavedAssetIds = emptySet(),
                    selectedMediaItemIds = emptySet(),
                    albums = emptyList(),
                    settings = ImageGenerationSettings(),
                    capabilities = null,
                    selectedTemplateId = null,
                    isSettingsOpen = false,
                    isSaveBottomSheetOpen = false,
                    continualMode = false,
                    isContinualGenerationActive = false,
                    error = null,
                    lastSubmittedPrompt = null,
                    videoGenerationState = VideoGenerationState.Idle,
                    speechState = SpeechState.Idle,
                    isPlayingTts = false,
                    referenceMediaType = null,
                    hasVideoModel = false,
                ),
            )

        assertEquals(2, state.gallery.size)
        assertEquals(1, state.groupedGallery.size)
        assertEquals("sunset", state.groupedGallery.first().prompt)
    }

    @Test
    fun reduce_exposesActiveGenerationPromptOnlyWhileGenerating() {
        val generating =
            reducer.reduce(
                baseParams(
                    isGenerating = true,
                    lastSubmittedPrompt = "held prompt",
                ),
            )
        val idle =
            reducer.reduce(
                baseParams(
                    isGenerating = false,
                    lastSubmittedPrompt = "held prompt",
                ),
            )

        assertEquals("held prompt", generating.activeGenerationPrompt)
        assertEquals(null, idle.activeGenerationPrompt)
    }

    @Test
    fun `ST3 reduce exposes selected items and custom albums in state`() {
        val customAlbum =
            GalleryAlbumUi(
                id = "custom-album",
                name = "Moodboards",
                items = emptyList(),
            )
        val sessionMedia =
            listOf(
                StudioMediaUi(
                    id = "ephemeral-1",
                    localUri = "cache://ephemeral.jpg",
                    prompt = "portrait",
                    mediaType = MediaCapability.IMAGE,
                    createdAt = 1L,
                ),
            )
        val savedInAlbum =
            StudioMediaUi(
                id = "saved-1",
                localUri = "files://saved.jpg",
                prompt = "portrait",
                mediaType = MediaCapability.IMAGE,
                createdAt = 2L,
                albumId = "custom-album",
            )
        val defaultAlbum =
            GalleryAlbumUi(
                id = DEFAULT_GALLERY_ALBUM_ID,
                name = "Default Album",
                items = listOf(savedInAlbum),
            )

        val state =
            reducer.reduce(
                baseParams().copy(
                    sessionMedia = sessionMedia,
                    sessionSavedAssetIds = setOf("saved-1"),
                    selectedMediaItemIds = setOf("ephemeral-1"),
                    albums = listOf(defaultAlbum, customAlbum),
                ),
            )

        assertEquals(setOf("ephemeral-1"), state.selectedMediaItemIds)
        assertEquals(listOf("Default Album", "Moodboards"), state.albums.map { it.name })
        assertEquals(2, state.gallery.size)
        assertEquals("ephemeral-1", state.gallery.first().id)
        assertEquals("saved-1", state.gallery.last().id)
    }

    @Test
    fun reduce_selectsMusicTemplatesForMusicMediaType() {
        val state =
            reducer.reduce(
                baseParams(
                    mediaType = MediaCapability.MUSIC,
                ),
            )

        assertTrue(state.templates.all { it.id.startsWith("m") })
        assertFalse(state.templates.any { it.id == "1" })
    }

    private fun baseParams(
        prompt: String = "",
        isGenerating: Boolean = false,
        mediaType: MediaCapability = MediaCapability.IMAGE,
        lastSubmittedPrompt: String? = null,
        sessionMedia: List<StudioMediaUi> = emptyList(),
        sessionSavedAssetIds: Set<String> = emptySet(),
        selectedMediaItemIds: Set<String> = emptySet(),
        albums: List<GalleryAlbumUi> = emptyList(),
    ): StudioUiStateReducer.ReduceParams =
        StudioUiStateReducer.ReduceParams(
            prompt = prompt,
            isGenerating = isGenerating,
            mediaType = mediaType,
            sessionMedia = sessionMedia,
            sessionSavedAssetIds = sessionSavedAssetIds,
            selectedMediaItemIds = selectedMediaItemIds,
            albums = albums,
            settings = ImageGenerationSettings(),
            capabilities = null,
            selectedTemplateId = null,
            isSettingsOpen = false,
            isSaveBottomSheetOpen = false,
            continualMode = false,
            isContinualGenerationActive = false,
            error = null,
            lastSubmittedPrompt = lastSubmittedPrompt,
            videoGenerationState = VideoGenerationState.Idle,
            speechState = SpeechState.Idle,
            isPlayingTts = false,
            referenceMediaType = null,
            hasVideoModel = false,
        )
}
