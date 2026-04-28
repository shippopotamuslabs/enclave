package com.browntowndev.pocketcrew.feature.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.media.*
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.ImageGenerationPort
import com.browntowndev.pocketcrew.domain.port.media.VideoGenerationPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.media.GetProviderCapabilitiesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MultimodalViewModel @Inject constructor(
    private val imageGenerationPort: ImageGenerationPort,
    private val videoGenerationPort: VideoGenerationPort,
    private val studioRepository: StudioRepositoryPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val mediaProviderRepository: MediaProviderRepositoryPort,
    private val getProviderCapabilitiesUseCase: GetProviderCapabilitiesUseCase,
    private val logger: LoggingPort
) : ViewModel() {

    private val _prompt = MutableStateFlow("")
    private val _isGenerating = MutableStateFlow(false)
    private val _mediaType = MutableStateFlow(MediaCapability.IMAGE)
    private val _settings = MutableStateFlow<GenerationSettings>(ImageGenerationSettings())
    private val _selectedTemplateId = MutableStateFlow<String?>(null)
    private val _isSettingsOpen = MutableStateFlow(false)
    private val _continualMode = MutableStateFlow(false)
    private val _isContinualGenerationActive = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    private val templates = listOf(
        StudioTemplate("1", "Cyberpunk", "cyberpunk", "Neon lighting, futuristic city, cyberpunk style, ", " ultra detailed, 8k"),
        StudioTemplate("2", "Oil Painting", "oil_painting", "Oil painting of ", ", thick brushstrokes, canvas texture"),
        StudioTemplate("3", "Anime", "anime", "Anime style illustration of ", ", vibrant colors, clean lines"),
        StudioTemplate("4", "Cinematic", "cinematic", "Cinematic shot of ", ", dramatic lighting, shallow depth of field")
    )

    private val musicTemplates = listOf(
        StudioTemplate("m1", "Lo-Fi", "", "Lo-Fi hip hop beat, ", " relaxing, study"),
        StudioTemplate("m2", "Techno", "", "High energy techno, ", " 130bpm, club"),
        StudioTemplate("m3", "Orchestral", "", "Epic orchestral soundtrack, ", " cinematic, dramatic"),
        StudioTemplate("m4", "Ambient", "", "Ambient electronic, ", " atmospheric, calm")
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
        getProviderCapabilitiesUseCase(provider?.provider?.name)
    }

    private val galleryFlow = studioRepository.observeAllMedia().map { assets ->
        assets.map { asset -> asset.toUi() }
    }

    val uiState: StateFlow<StudioUiState> = combine(
        _prompt,
        _isGenerating,
        _mediaType,
        galleryFlow,
        _settings,
        capabilitiesFlow,
        _selectedTemplateId,
        _isSettingsOpen,
        _continualMode,
        _isContinualGenerationActive,
        _error
    ) { args ->
        val prompt = args[0] as String
        val isGenerating = args[1] as Boolean
        val mediaType = args[2] as MediaCapability
        @Suppress("UNCHECKED_CAST")
        val gallery = args[3] as List<StudioMediaUi>
        val settings = args[4] as GenerationSettings
        val caps = args[5] as ProviderCapabilities?
        val templateId = args[6] as String?
        val isOpen = args[7] as Boolean
        val continual = args[8] as Boolean
        val isContinualGenerationActive = args[9] as Boolean
        val error = args[10] as String?

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
            isContinualGenerationActive = isContinualGenerationActive,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioUiState()
    )

    private var generationJob: Job? = null
    private val consumedScrollAnchors = mutableSetOf<String>()

    fun onPromptChange(newPrompt: String) {
        _prompt.value = newPrompt
        stopGeneration()
        consumedScrollAnchors.clear()
    }

    fun onMediaTypeChange(type: MediaCapability) {
        _mediaType.value = type
        _settings.value = when (type) {
            MediaCapability.IMAGE -> ImageGenerationSettings()
            MediaCapability.VIDEO -> VideoGenerationSettings()
            MediaCapability.MUSIC -> MusicGenerationSettings()
        }
        _selectedTemplateId.value = null
        stopGeneration()
        consumedScrollAnchors.clear()
    }

    fun onTemplateSelected(template: StudioTemplate) {
        _selectedTemplateId.value = if (_selectedTemplateId.value == template.id) null else template.id
    }

    fun onSettingsToggle() {
        _isSettingsOpen.value = !_isSettingsOpen.value
    }

    fun onEditMedia(assetId: String) {
        viewModelScope.launch {
            val asset = studioRepository.getMediaById(assetId) ?: return@launch
            _prompt.value = asset.prompt
            _selectedTemplateId.value = null
            stopGeneration()
            consumedScrollAnchors.clear()
        }
    }

    fun onAnimateMedia(assetId: String) {
        viewModelScope.launch {
            val asset = studioRepository.getMediaById(assetId) ?: return@launch
            _mediaType.value = MediaCapability.VIDEO
            _prompt.value = asset.prompt
            _settings.value = VideoGenerationSettings(referenceImageUri = asset.localUri)
            stopGeneration()
            consumedScrollAnchors.clear()
        }
    }

    fun onContinualModeToggle(enabled: Boolean) {
        _continualMode.value = enabled
        if (!enabled) {
            stopGeneration()
            consumedScrollAnchors.clear()
        }
    }

    fun onUpdateSettings(newSettings: GenerationSettings) {
        _settings.value = when (newSettings) {
            is ImageGenerationSettings -> newSettings.withClampedGenerationCount()
            else -> newSettings
        }
        consumedScrollAnchors.clear()
    }

    fun onUpdateReferenceImage(uri: String?) {
        val currentSettings = _settings.value
        _settings.value = when (currentSettings) {
            is ImageGenerationSettings -> currentSettings.copy(referenceImageUri = uri).withClampedGenerationCount()
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
        if (_isGenerating.value || _isContinualGenerationActive.value) {
            stopGeneration()
            return
        }

        if (_continualMode.value && _mediaType.value == MediaCapability.IMAGE) {
            _isContinualGenerationActive.value = true
        }
        startGeneration()
    }

    fun onGenerativeScrollThresholdVisible(anchorAssetId: String) {
        if (_mediaType.value != MediaCapability.IMAGE ||
            !_isContinualGenerationActive.value ||
            _prompt.value.isBlank() ||
            _isGenerating.value ||
            !consumedScrollAnchors.add(anchorAssetId)
        ) {
            return
        }

        startGeneration()
    }

    private fun stopGeneration() {
        _isContinualGenerationActive.value = false
        generationJob?.cancel()
        _isGenerating.value = false
    }

    private fun deactivateContinualGeneration() {
        _isContinualGenerationActive.value = false
    }

    private fun startGeneration() {
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            try {
                val provider = activeProviderFlow.first()
                if (provider == null) {
                    deactivateContinualGeneration()
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
                        imageGenerationPort.generateImage(finalPrompt, provider, currentSettings.withClampedGenerationCount())
                    }
                    is VideoGenerationSettings -> {
                        videoGenerationPort.generateVideo(finalPrompt, provider, currentSettings).map { listOf(it) }
                    }
                    is MusicGenerationSettings -> {
                        Result.failure(Exception("Music generation not yet implemented"))
                    }
                }

                result.onSuccess { images ->
                    images.forEach { bytes ->
                        studioRepository.saveMedia(bytes, _prompt.value, currentType.name)
                    }
                }.onFailure { e ->
                    logger.error(TAG, "Generation failed for ${currentType.name}", e)
                    deactivateContinualGeneration()
                    _error.value = e.message ?: "${currentType.name} generation failed"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(TAG, "Unexpected error during generation", e)
                _error.value = e.message ?: "Generation failed"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
    }

    companion object {
        private const val TAG = "MultimodalViewModel"
    }
}
