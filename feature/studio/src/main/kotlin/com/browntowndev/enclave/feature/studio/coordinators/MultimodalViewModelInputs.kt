package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.feature.studio.GalleryAlbumUi
import com.browntowndev.enclave.feature.studio.StudioMediaUi
import com.browntowndev.enclave.feature.studio.VideoGenerationState

internal data class PromptCoreInputs(
    val prompt: String,
    val mediaType: MediaCapability,
    val settings: GenerationSettings,
)

internal data class PromptMetaInputs(
    val selectedTemplateId: String?,
    val isSettingsOpen: Boolean,
    val continualMode: Boolean,
    val referenceMediaType: MediaCapability?,
)

internal data class PromptInputs(
    val prompt: String,
    val mediaType: MediaCapability,
    val settings: GenerationSettings,
    val selectedTemplateId: String?,
    val isSettingsOpen: Boolean,
    val continualMode: Boolean,
    val referenceMediaType: MediaCapability?,
)

internal data class GenerationInputs(
    val isGenerating: Boolean,
    val lastSubmittedPrompt: String?,
    val isContinualGenerationActive: Boolean,
    val error: String?,
    val generationProgressLabel: String?,
    val videoGenerationState: VideoGenerationState,
)

internal data class SelectionInputs(
    val sessionMedia: List<StudioMediaUi>,
    val sessionSavedAssetIds: Set<String>,
    val selectedMediaItemIds: Set<String>,
    val albums: List<GalleryAlbumUi>,
    val isSaveBottomSheetOpen: Boolean,
)

internal data class CapabilitiesPlaybackInputs(
    val capabilities: ProviderCapabilities?,
    val isCapabilitiesLoading: Boolean,
)

internal data class PlaybackInputs(
    val capabilities: ProviderCapabilities?,
    val isCapabilitiesLoading: Boolean,
    val speechState: SpeechState,
    val isPlayingTts: Boolean,
    val hasImageEditModel: Boolean,
    val hasImageToVideoModel: Boolean,
)
