package com.browntowndev.pocketcrew.feature.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.media.*
import com.browntowndev.pocketcrew.domain.port.media.ImageGenerationPort
import com.browntowndev.pocketcrew.domain.port.media.VideoGenerationPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MultimodalViewModel @Inject constructor(
    private val imageGenerationPort: ImageGenerationPort,
    private val videoGenerationPort: VideoGenerationPort,
    private val studioRepository: StudioRepositoryPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val mediaProviderRepository: MediaProviderRepositoryPort
) : ViewModel() {

    private val _prompt = MutableStateFlow("")
    private val _isGenerating = MutableStateFlow(false)
    private val _mediaType = MutableStateFlow(MediaCapability.IMAGE)
    private val _settings = MutableStateFlow<GenerationSettings>(ImageGenerationSettings())
    private val _selectedTemplateId = MutableStateFlow<String?>(null)
    private val _isSettingsOpen = MutableStateFlow(false)
    private val _continualMode = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    private val templates = listOf(
        StudioTemplate("1", "Cyberpunk", "Neon lighting, futuristic city, cyberpunk style, ", " ultra detailed, 8k", "https://example.com/cyberpunk.jpg"),
        StudioTemplate("2", "Oil Painting", "Oil painting of ", ", thick brushstrokes, canvas texture", "https://example.com/oil.jpg"),
        StudioTemplate("3", "Anime", "Anime style illustration of ", ", vibrant colors, clean lines", "https://example.com/anime.jpg"),
        StudioTemplate("4", "Cinematic", "Cinematic shot of ", ", dramatic lighting, shallow depth of field", "https://example.com/cinematic.jpg")
    )

    private val musicTemplates = listOf(
        StudioTemplate("m1", "Lo-Fi", "Lo-Fi hip hop beat, ", " relaxing, study", ""),
        StudioTemplate("m2", "Techno", "High energy techno, ", " 130bpm, club", ""),
        StudioTemplate("m3", "Orchestral", "Epic orchestral soundtrack, ", " cinematic, dramatic", ""),
        StudioTemplate("m4", "Ambient", "Ambient electronic, ", " atmospheric, calm", "")
    )

    private val activeModelFlow = combine(_mediaType, defaultModelRepository.observeDefaults()) { type, defaults ->
        val modelType = when (type) {
            MediaCapability.IMAGE -> ModelType.IMAGE_GENERATION
            MediaCapability.VIDEO -> ModelType.VIDEO_GENERATION
            MediaCapability.MUSIC -> ModelType.MUSIC_GENERATION
        }
        defaults.find { it.modelType == modelType }
    }

    private val activeProviderFlow = activeModelFlow.flatMapLatest { assignment ->
        if (assignment?.mediaProviderId != null) {
            mediaProviderRepository.getMediaProviders().map { providers ->
                providers.find { it.id == assignment.mediaProviderId }
            }
        } else {
            flowOf(null)
        }
    }

    private val capabilitiesFlow = activeProviderFlow.map { provider ->
        val providerId = provider?.id?.toString() ?: ""
        when {
            providerId.contains("openai", ignoreCase = true) -> ProviderCapabilities(
                supportedAspectRatios = listOf(AspectRatio.ONE_ONE, AspectRatio.SIXTEEN_NINE, AspectRatio.NINE_SIXTEEN),
                supportedQualities = listOf(GenerationQuality.SPEED, GenerationQuality.HD),
                supportsReferenceImage = false,
                supportsVideo = false,
                supportsMusic = false
            )
            providerId.contains("google", ignoreCase = true) -> ProviderCapabilities(
                supportedAspectRatios = listOf(AspectRatio.ONE_ONE, AspectRatio.THREE_FOUR, AspectRatio.FOUR_THREE, AspectRatio.NINE_SIXTEEN, AspectRatio.SIXTEEN_NINE),
                supportedQualities = listOf(GenerationQuality.SPEED, GenerationQuality.ULTRA),
                supportsReferenceImage = true,
                supportsVideo = true,
                supportsMusic = false
            )
            else -> ProviderCapabilities(
                supportedAspectRatios = AspectRatio.entries.toList(),
                supportedQualities = listOf(GenerationQuality.SPEED, GenerationQuality.QUALITY, GenerationQuality.HD),
                supportsReferenceImage = true,
                supportsVideo = true,
                supportsMusic = true
            )
        }
    }

    val uiState: StateFlow<StudioUiState> = combine(
        _prompt,
        _isGenerating,
        _mediaType,
        studioRepository.observeAllMedia(),
        _settings,
        capabilitiesFlow,
        _selectedTemplateId,
        _isSettingsOpen,
        _continualMode,
        _error
    ) { args ->
        val prompt = args[0] as String
        val isGenerating = args[1] as Boolean
        val mediaType = args[2] as MediaCapability
        val gallery = args[3] as List<StudioMediaAsset>
        val settings = args[4] as GenerationSettings
        val caps = args[5] as ProviderCapabilities?
        val templateId = args[6] as String?
        val isOpen = args[7] as Boolean
        val continual = args[8] as Boolean
        val error = args[9] as String?

        val currentTemplates = if (mediaType == MediaCapability.MUSIC) musicTemplates else templates

        StudioUiState(
            prompt = prompt,
            isGenerating = isGenerating,
            mediaType = mediaType,
            gallery = gallery,
            settings = settings,
            capabilities = caps,
            templates = currentTemplates,
            selectedTemplateId = templateId,
            isSettingsOpen = isOpen,
            continualMode = continual,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioUiState()
    )

    private var generationJob: Job? = null

    fun onPromptChange(newPrompt: String) {
        _prompt.value = newPrompt
    }

    fun onMediaTypeChange(type: MediaCapability) {
        _mediaType.value = type
        _settings.value = when (type) {
            MediaCapability.IMAGE -> ImageGenerationSettings()
            MediaCapability.VIDEO -> VideoGenerationSettings()
            MediaCapability.MUSIC -> MusicGenerationSettings()
        }
        _selectedTemplateId.value = null
    }

    fun onTemplateSelected(template: StudioTemplate) {
        _selectedTemplateId.value = if (_selectedTemplateId.value == template.id) null else template.id
    }

    fun onSettingsToggle() {
        _isSettingsOpen.value = !_isSettingsOpen.value
    }

    fun onEditMedia(asset: StudioMediaAsset) {
        _prompt.value = asset.prompt
        _selectedTemplateId.value = null
    }

    fun onAnimateMedia(asset: StudioMediaAsset) {
        _mediaType.value = MediaCapability.VIDEO
        _prompt.value = asset.prompt
        _settings.value = VideoGenerationSettings(referenceImageUri = asset.localUri)
    }

    fun onContinualModeToggle(enabled: Boolean) {
        _continualMode.value = enabled
    }

    fun onUpdateSettings(newSettings: GenerationSettings) {
        _settings.value = newSettings
    }

    fun onUpdateReferenceImage(uri: String?) {
        val currentSettings = _settings.value
        _settings.value = when (currentSettings) {
            is ImageGenerationSettings -> currentSettings.copy(referenceImageUri = uri)
            is VideoGenerationSettings -> currentSettings.copy(referenceImageUri = uri)
            is MusicGenerationSettings -> currentSettings
        }
    }

    fun onClearReferenceImage() {
        onUpdateReferenceImage(null)
    }

    fun clearError() {
        _error.value = null
    }

    fun deleteMedia(id: String) {
        viewModelScope.launch {
            studioRepository.deleteMedia(id)
        }
    }

    fun generate() {
        if (_isGenerating.value) return

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null

            try {
                val provider = activeProviderFlow.first()
                if (provider == null) {
                    _error.value = "No media provider configured. Go to Settings to set one up."
                    return@launch
                }

                val currentType = _mediaType.value
                val currentTemplates = if (currentType == MediaCapability.MUSIC) musicTemplates else templates
                val selectedTemplate = currentTemplates.find { it.id == _selectedTemplateId.value }
                val finalPrompt = if (selectedTemplate != null) {
                    "${selectedTemplate.promptPrefix}${_prompt.value}${selectedTemplate.promptSuffix}"
                } else {
                    _prompt.value
                }

                val result = when (val currentSettings = _settings.value) {
                    is ImageGenerationSettings -> {
                        imageGenerationPort.generateImage(finalPrompt, provider, currentSettings)
                    }
                    is VideoGenerationSettings -> {
                        videoGenerationPort.generateVideo(finalPrompt, provider, currentSettings)
                    }
                    is MusicGenerationSettings -> {
                        Result.failure(Exception("Music generation not yet implemented"))
                    }
                }

                result.onSuccess { bytes ->
                    studioRepository.saveMedia(bytes, _prompt.value, currentType.name)
                }.onFailure { e ->
                    _error.value = e.message ?: "${currentType.name} generation failed"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Generation failed"
            } finally {
                _isGenerating.value = false
                
                if (_continualMode.value && _error.value == null && coroutineContext.isActive) {
                    kotlinx.coroutines.delay(2000)
                    generate()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
    }
}
