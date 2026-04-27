package com.browntowndev.pocketcrew.feature.studio

import androidx.compose.runtime.Immutable
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.ProviderCapabilities
import com.browntowndev.pocketcrew.domain.model.media.StudioTemplate
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset

@Immutable
data class StudioUiState(
    val prompt: String = "",
    val isGenerating: Boolean = false,
    val mediaType: MediaCapability = MediaCapability.IMAGE,
    val gallery: List<StudioMediaAsset> = emptyList(),
    val settings: GenerationSettings = ImageGenerationSettings(),
    val capabilities: ProviderCapabilities? = null,
    val templates: List<StudioTemplate> = emptyList(),
    val selectedTemplateId: String? = null,
    val isSettingsOpen: Boolean = false,
    val continualMode: Boolean = false,
    val error: String? = null
)
