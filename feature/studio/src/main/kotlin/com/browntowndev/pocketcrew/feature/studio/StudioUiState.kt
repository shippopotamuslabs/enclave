package com.browntowndev.pocketcrew.feature.studio

import androidx.compose.runtime.Immutable
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.ProviderCapabilities
import com.browntowndev.pocketcrew.domain.model.media.StudioTemplate

import com.browntowndev.pocketcrew.domain.port.media.SpeechState

sealed interface VideoGenerationState {
    data object Idle : VideoGenerationState
    data class Loading(val sourceAssetId: String) : VideoGenerationState
    data class Success(val sourceAssetId: String, val localUri: String) : VideoGenerationState
    data class Error(val sourceAssetId: String?, val message: String) : VideoGenerationState
}

@Immutable
data class StudioUiState(
    val prompt: String = "",
    val isGenerating: Boolean = false,
    val activeGenerationPrompt: String? = null,
    val mediaType: MediaCapability = MediaCapability.IMAGE,
    val gallery: List<StudioMediaUi> = emptyList(),
    val selectedMediaItemIds: Set<String> = emptySet(),
    val albums: List<GalleryAlbumUi> = emptyList(),
    val settings: GenerationSettings = ImageGenerationSettings(),
    val capabilities: ProviderCapabilities? = null,
    val templates: List<StudioTemplate> = emptyList(),
    val selectedTemplateId: String? = null,
    val isSettingsOpen: Boolean = false,
    val isSaveBottomSheetOpen: Boolean = false,
    val continualMode: Boolean = false,
    val isContinualGenerationActive: Boolean = false,
    val error: String? = null,
    val videoGenerationState: VideoGenerationState = VideoGenerationState.Idle,
    val speechState: SpeechState = SpeechState.Idle,
    val isPlayingTts: Boolean = false,
)
