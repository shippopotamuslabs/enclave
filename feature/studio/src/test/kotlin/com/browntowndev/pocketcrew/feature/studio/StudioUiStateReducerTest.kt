package com.browntowndev.pocketcrew.feature.studio

import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.feature.studio.coordinators.StudioUiStateReducer
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
    ): StudioUiStateReducer.ReduceParams =
        StudioUiStateReducer.ReduceParams(
            prompt = prompt,
            isGenerating = isGenerating,
            mediaType = mediaType,
            sessionMedia = emptyList(),
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
            lastSubmittedPrompt = lastSubmittedPrompt,
            videoGenerationState = VideoGenerationState.Idle,
            speechState = SpeechState.Idle,
            isPlayingTts = false,
            referenceMediaType = null,
            hasVideoModel = false,
        )
}
