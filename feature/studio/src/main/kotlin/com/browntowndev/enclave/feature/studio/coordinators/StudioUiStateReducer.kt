package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.feature.studio.GalleryAlbumUi
import com.browntowndev.enclave.feature.studio.StudioCapablePresetsUi
import com.browntowndev.enclave.feature.studio.StudioGalleryGroup
import com.browntowndev.enclave.feature.studio.computeStudioActiveGenerationRemainingPlaceholders
import com.browntowndev.enclave.feature.studio.StudioMediaUi
import com.browntowndev.enclave.feature.studio.StudioTemplateUi
import com.browntowndev.enclave.feature.studio.StudioUiState
import com.browntowndev.enclave.feature.studio.VideoGenerationState
import com.browntowndev.enclave.feature.studio.toUi
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
            val generationProgressLabel: String?,
            val lastSubmittedPrompt: String?,
            val activeGenerationExpectedCount: Int,
            val activeGenerationBaselineMediaCount: Int,
            val videoGenerationState: VideoGenerationState,
            val speechState: SpeechState,
            val isPlayingTts: Boolean,
            val referenceMediaType: MediaCapability?,
            val hasImageEditModel: Boolean,
            val hasImageToVideoModel: Boolean,
            val activeModelDisplayName: String? = null,
            val modelPickerAssets: StudioCapablePresetsUi = StudioCapablePresetsUi(),
            val isModelPickerVisible: Boolean = false,
            val selectedModelIdInPicker: String? = null,
            val resolvedModelTypeLabel: String? = null,
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
            val activeGenerationRemainingPlaceholderCount =
                computeStudioActiveGenerationRemainingPlaceholders(
                    isGenerating = params.isGenerating,
                    lastSubmittedPrompt = params.lastSubmittedPrompt,
                    groupedGallery = groupedGallery,
                    expectedCount = params.activeGenerationExpectedCount,
                    baselineMediaCount = params.activeGenerationBaselineMediaCount,
                )

            return StudioUiState(
                prompt = params.prompt,
                isGenerating = params.isGenerating,
                activeGenerationPrompt = if (params.isGenerating) params.lastSubmittedPrompt else null,
                generationProgressLabel = params.generationProgressLabel,
                mediaType = params.mediaType.toUi(),
                gallery = gallery,
                groupedGallery = groupedGallery,
                activeGenerationRemainingPlaceholderCount = activeGenerationRemainingPlaceholderCount,
                selectedMediaItemIds = params.selectedMediaItemIds,
                albums = params.albums,
                settings = params.settings.toUi(),
                capabilities = params.capabilities?.toUi(),
                isCapabilitiesLoading = params.isCapabilitiesLoading,
                templates = templates,
                selectedTemplateId = params.selectedTemplateId,
                isSettingsOpen = params.isSettingsOpen,
                isSaveBottomSheetOpen = params.isSaveBottomSheetOpen,
                continualMode = params.continualMode,
                isContinualGenerationActive = params.isContinualGenerationActive,
                error = params.error,
                referenceMediaType = params.referenceMediaType?.toUi(),
                videoGenerationState = params.videoGenerationState,
                speechState = params.speechState.toUi(),
                isPlayingTts = params.isPlayingTts,
                hasImageEditModel = params.hasImageEditModel,
                hasImageToVideoModel = params.hasImageToVideoModel,
                activeModelDisplayName = params.activeModelDisplayName,
                modelPickerAssets = params.modelPickerAssets,
                isModelPickerVisible = params.isModelPickerVisible,
                selectedModelIdInPicker = params.selectedModelIdInPicker,
                resolvedModelTypeLabel = params.resolvedModelTypeLabel,
            )
        }

        private companion object {
            private val IMAGE_TEMPLATES =
                listOf(
                    StudioTemplateUi(
                        id = "1",
                        name = "Cyberpunk",
                        exampleUri = "cyberpunk",
                        promptPrefix = "Neon lighting, futuristic city, cyberpunk style, ",
                        promptSuffix = " ultra detailed, 8k",
                    ),
                    StudioTemplateUi(
                        id = "2",
                        name = "Oil Painting",
                        exampleUri = "oil_painting",
                        promptPrefix = "Oil painting of ",
                        promptSuffix = ", thick brushstrokes, canvas texture",
                    ),
                    StudioTemplateUi(
                        id = "3",
                        name = "Anime",
                        exampleUri = "anime",
                        promptPrefix = "Anime style illustration of ",
                        promptSuffix = ", vibrant colors, clean lines",
                    ),
                    StudioTemplateUi(
                        id = "4",
                        name = "Cinematic",
                        exampleUri = "cinematic",
                        promptPrefix = "Cinematic shot of ",
                        promptSuffix = ", dramatic lighting, shallow depth of field",
                    ),
                )

            private val MUSIC_TEMPLATES =
                listOf(
                    StudioTemplateUi("m1", "Lo-Fi", "", "Lo-Fi hip hop beat, ", " relaxing, study"),
                    StudioTemplateUi("m2", "Techno", "", "High energy techno, ", " 130bpm, club"),
                    StudioTemplateUi("m3", "Orchestral", "", "Epic orchestral soundtrack, ", " cinematic, dramatic"),
                    StudioTemplateUi("m4", "Ambient", "", "Ambient electronic, ", " atmospheric, calm"),
                )
        }
    }
