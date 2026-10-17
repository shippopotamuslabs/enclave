package com.browntowndev.enclave.feature.studio

import androidx.compose.runtime.Immutable

sealed interface VideoGenerationState {
    data object Idle : VideoGenerationState

    data class Loading(
        val sourceAssetId: String,
    ) : VideoGenerationState

    data class Success(
        val sourceAssetId: String,
        val localUri: String,
    ) : VideoGenerationState

    data class Error(
        val sourceAssetId: String?,
        val message: String,
    ) : VideoGenerationState
}

@Immutable
data class StudioUiState(
    val prompt: String = "",
    val isGenerating: Boolean = false,
    val activeGenerationPrompt: String? = null,
    val generationProgressLabel: String? = null,
    val mediaType: StudioMediaTypeUi = StudioMediaTypeUi.IMAGE,
    val gallery: List<StudioMediaUi> = emptyList(),
    val selectedMediaItemIds: Set<String> = emptySet(),
    val albums: List<GalleryAlbumUi> = emptyList(),
    val settings: StudioGenerationSettingsUi = StudioImageSettingsUi(),
    val capabilities: StudioCapabilitiesUi? = null,
    /** True while fal OpenAPI-backed capability options are being fetched. */
    val isCapabilitiesLoading: Boolean = false,
    val templates: List<StudioTemplateUi> = emptyList(),
    val selectedTemplateId: String? = null,
    val isSettingsOpen: Boolean = false,
    val isSaveBottomSheetOpen: Boolean = false,
    val continualMode: Boolean = false,
    val isContinualGenerationActive: Boolean = false,
    val error: String? = null,
    val referenceMediaType: StudioMediaTypeUi? = null,
    val videoGenerationState: VideoGenerationState = VideoGenerationState.Idle,
    val speechState: StudioSpeechStateUi = StudioSpeechStateUi.Idle,
    val isPlayingTts: Boolean = false,
    val groupedGallery: List<StudioGalleryGroup> = emptyList(),
    val activeGenerationRemainingPlaceholderCount: Int = 0,
    val hasImageEditModel: Boolean = false,
    val hasImageToVideoModel: Boolean = false,
    val activeModelDisplayName: String? = null,
    val modelPickerAssets: StudioCapablePresetsUi = StudioCapablePresetsUi(),
    val isModelPickerVisible: Boolean = false,
    val selectedModelIdInPicker: String? = null,
    val resolvedModelTypeLabel: String? = null,
)
