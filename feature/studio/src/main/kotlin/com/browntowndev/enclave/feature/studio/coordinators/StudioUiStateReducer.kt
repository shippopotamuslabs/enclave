package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.model.media.StudioTemplate
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.feature.studio.GalleryAlbumUi
import com.browntowndev.enclave.feature.studio.StudioGalleryGroup
import com.browntowndev.enclave.feature.studio.StudioMediaUi
import com.browntowndev.enclave.feature.studio.StudioUiState
import com.browntowndev.enclave.feature.studio.VideoGenerationState
import javax.inject.Inject

class StudioUiStateReducer
    @Inject
    constructor() {
        data class ReduceParams(
            val prompt: String,
            val isGenerating: Boolean,
            val mediaType: MediaCapability,
            val sessionMedia: List<StudioMediaUi>,
            val sessionSavedAssetIds: Set<String>,
            val selectedMediaItemIds: Set<String>,
            val albums: List<GalleryAlbumUi>,
            val settings: GenerationSettings,
            val capabilities: ProviderCapabilities?,
            val isCapabilitiesLoading: Boolean,
            val selectedTemplateId: String?,
            val isSettingsOpen: Boolean,
            val isSaveBottomSheetOpen: Boolean,
            val continualMode: Boolean,
            val isContinualGenerationActive: Boolean,
            val error: String?,
            val lastSubmittedPrompt: String?,
            val videoGenerationState: VideoGenerationState,
            val speechState: SpeechState,
            val isPlayingTts: Boolean,
            val referenceMediaType: MediaCapability?,
            val hasImageEditModel: Boolean,
            val hasImageToVideoModel: Boolean,
        )

        fun reduce(params: ReduceParams): StudioUiState {
            val gallery =
                (params.albums.flatMap { it.items }.filter { it.id in params.sessionSavedAssetIds } + params.sessionMedia)
                    .distinctBy { it.localUri.substringAfterLast('/') }
                    .sortedBy { it.createdAt }

            val groupedGallery =
                gallery
                    .groupBy { it.prompt }
                    .map { (prompt, items) ->
                        StudioGalleryGroup(prompt = prompt, mediaItems = items)
                    }.sortedBy { group -> group.mediaItems.maxOf { item -> item.createdAt } }

            val templates = if (params.mediaType == MediaCapability.MUSIC) MUSIC_TEMPLATES else IMAGE_TEMPLATES

            return StudioUiState(
                prompt = params.prompt,
                isGenerating = params.isGenerating,
                activeGenerationPrompt = if (params.isGenerating) params.lastSubmittedPrompt else null,
                mediaType = params.mediaType,
                gallery = gallery,
                groupedGallery = groupedGallery,
                selectedMediaItemIds = params.selectedMediaItemIds,
                albums = params.albums,
                settings = params.settings,
                capabilities = params.capabilities,
                isCapabilitiesLoading = params.isCapabilitiesLoading,
                templates = templates,
                selectedTemplateId = params.selectedTemplateId,
                isSettingsOpen = params.isSettingsOpen,
                isSaveBottomSheetOpen = params.isSaveBottomSheetOpen,
                continualMode = params.continualMode,
                isContinualGenerationActive = params.isContinualGenerationActive,
                error = params.error,
                referenceMediaType = params.referenceMediaType,
                videoGenerationState = params.videoGenerationState,
                speechState = params.speechState,
                isPlayingTts = params.isPlayingTts,
                hasImageEditModel = params.hasImageEditModel,
                hasImageToVideoModel = params.hasImageToVideoModel,
            )
        }

        private companion object {
            private val IMAGE_TEMPLATES =
                listOf(
                    StudioTemplate(
                        id = "1",
                        name = "Cyberpunk",
                        exampleUri = "cyberpunk",
                        promptPrefix = "Neon lighting, futuristic city, cyberpunk style, ",
                        promptSuffix = " ultra detailed, 8k",
                    ),
                    StudioTemplate(
                        id = "2",
                        name = "Oil Painting",
                        exampleUri = "oil_painting",
                        promptPrefix = "Oil painting of ",
                        promptSuffix = ", thick brushstrokes, canvas texture",
                    ),
                    StudioTemplate(
                        id = "3",
                        name = "Anime",
                        exampleUri = "anime",
                        promptPrefix = "Anime style illustration of ",
                        promptSuffix = ", vibrant colors, clean lines",
                    ),
                    StudioTemplate(
                        id = "4",
                        name = "Cinematic",
                        exampleUri = "cinematic",
                        promptPrefix = "Cinematic shot of ",
                        promptSuffix = ", dramatic lighting, shallow depth of field",
                    ),
                )

            private val MUSIC_TEMPLATES =
                listOf(
                    StudioTemplate("m1", "Lo-Fi", "", "Lo-Fi hip hop beat, ", " relaxing, study"),
                    StudioTemplate("m2", "Techno", "", "High energy techno, ", " 130bpm, club"),
                    StudioTemplate("m3", "Orchestral", "", "Epic orchestral soundtrack, ", " cinematic, dramatic"),
                    StudioTemplate("m4", "Ambient", "", "Ambient electronic, ", " atmospheric, calm"),
                )
        }
    }
