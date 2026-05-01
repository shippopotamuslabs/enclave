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
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.port.media.ShareMediaPort
import com.browntowndev.pocketcrew.domain.usecase.media.GenerateMusicUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.GenerateVideoUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackStatus
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MultimodalViewModel @Inject constructor(
    private val imageGenerationPort: ImageGenerationPort,
    private val videoGenerationPort: VideoGenerationPort,
    private val generateVideoUseCase: GenerateVideoUseCase,
    private val generateMusicUseCase: GenerateMusicUseCase,
    private val shareMediaPort: ShareMediaPort,
    private val studioRepository: StudioRepositoryPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val mediaProviderRepository: MediaProviderRepositoryPort,
    private val getProviderCapabilitiesUseCase: GetProviderCapabilitiesUseCase,
    private val chatUseCases: ChatUseCases,
    private val playbackController: TtsPlaybackControllerPort,
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
    private val _referenceMediaType = MutableStateFlow<MediaCapability?>(null)
    private val _lastSubmittedPrompt = MutableStateFlow<String?>(null)
    private val _sessionMedia = MutableStateFlow<List<StudioMediaUi>>(emptyList())
    private val _selectedMediaItemIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isSaveBottomSheetOpen = MutableStateFlow(false)
    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    private val _stopSpeechSignal = MutableStateFlow(false)
    private val _isPlayingTts = MutableStateFlow(false)
    private val _videoGenerationState = MutableStateFlow<VideoGenerationState>(VideoGenerationState.Idle)

    private var speechJob: Job? = null
    private var streamingTtsJob: Job? = null

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

    private val albumsFlow = combine(
        studioRepository.observeAllAlbums(),
        studioRepository.observeAllMedia()
    ) { albums, allMedia ->
        val albumItems = mutableMapOf<String, MutableList<StudioMediaUi>>()
        albumItems[DEFAULT_GALLERY_ALBUM_ID] = mutableListOf()
        albums.forEach { albumItems[it.id] = mutableListOf() }

        allMedia.forEach { asset ->
            val item = asset.toUi()
            val albumId = asset.albumId ?: DEFAULT_GALLERY_ALBUM_ID
            albumItems[albumId]?.add(item)
        }

        listOf(
            GalleryAlbumUi(
                id = DEFAULT_GALLERY_ALBUM_ID,
                name = "Default Album",
                items = albumItems[DEFAULT_GALLERY_ALBUM_ID] ?: emptyList(),
            ),
        ) + albums.map { album ->
            GalleryAlbumUi(
                id = album.id,
                name = album.name,
                items = albumItems[album.id] ?: emptyList(),
            )
        }
    }

    private val hasTtsProviderAssignedFlow = defaultModelRepository.observeDefaults()
        .map { assignments ->
            assignments.any { assignment ->
                assignment.modelType == ModelType.TTS && assignment.ttsProviderId != null
            }
        }
        .distinctUntilChanged()

    init {
        observeSpeechResults()
    }

    private fun observeSpeechResults() {
        _speechState
            .onEach { state ->
                when (state) {
                    is SpeechState.FinalText -> {
                        _prompt.value = state.text
                    }
                    else -> {}
                }
            }
            .launchIn(viewModelScope)
    }

    fun onMicClick() {
        val currentState = _speechState.value
        if (
            currentState is SpeechState.ModelLoading ||
            currentState is SpeechState.Listening
        ) {
            _stopSpeechSignal.value = true
        } else {
            startListening()
        }
    }

    private fun startListening() {
        _stopSpeechSignal.value = false
        speechJob?.cancel()
        speechJob = chatUseCases.listenToSpeechUseCase(_prompt.value, _stopSpeechSignal.asStateFlow())
            .onEach { state ->
                _speechState.value = state
            }
            .catch { cause ->
                _speechState.value = SpeechState.Error(cause.message ?: "Speech transcription failed.")
            }
            .launchIn(viewModelScope)
    }

    private fun stopListening() {
        speechJob?.cancel()
        speechJob = null
        _speechState.value = SpeechState.Idle
    }

    fun onPlayTts(text: String) {
        viewModelScope.launch {
            if (!hasTtsProviderAssignedFlow.first()) return@launch

            streamingTtsJob?.cancel()
            streamingTtsJob = null
            playbackController.stop()

            streamingTtsJob = viewModelScope.launch {
                playbackController.play(text)
                    .collect { status ->
                        when (status) {
                            is TtsPlaybackStatus.Playing -> {
                                _isPlayingTts.value = true
                            }
                            is TtsPlaybackStatus.Completed,
                            is TtsPlaybackStatus.Error,
                            is TtsPlaybackStatus.Stopped -> {
                                _isPlayingTts.value = false
                                streamingTtsJob = null
                            }
                            else -> {}
                        }
                    }
            }
        }
    }

    fun onStopTts() {
        streamingTtsJob?.cancel()
        streamingTtsJob = null
        playbackController.stop()
        _isPlayingTts.value = false
    }

    val uiState: StateFlow<StudioUiState> = combine(
        _prompt,
        _isGenerating,
        _mediaType,
        _sessionMedia,
        _selectedMediaItemIds,
        albumsFlow,
        _settings,
        capabilitiesFlow,
        _selectedTemplateId,
        _isSettingsOpen,
        _isSaveBottomSheetOpen,
        _continualMode,
        _isContinualGenerationActive,
        _error,
        _lastSubmittedPrompt,
        _videoGenerationState,
        _speechState,
        _isPlayingTts,
        _referenceMediaType,
    ) { args ->
        val prompt = args[0] as String
        val isGenerating = args[1] as Boolean
        val mediaType = args[2] as MediaCapability
        @Suppress("UNCHECKED_CAST")
        val sessionMedia = args[3] as List<StudioMediaUi>
        @Suppress("UNCHECKED_CAST")
        val selectedMediaItemIds = args[4] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val albums = args[5] as List<GalleryAlbumUi>
        val settings = args[6] as GenerationSettings
        val caps = args[7] as ProviderCapabilities?
        val templateId = args[8] as String?
        val isOpen = args[9] as Boolean
        val isSaveOpen = args[10] as Boolean
        val continual = args[11] as Boolean
        val isContinualGenerationActive = args[12] as Boolean
        val error = args[13] as String?
        val lastSubmittedPrompt = args[14] as String?
        val videoGenerationState = args[15] as VideoGenerationState
        val speechState = args[16] as SpeechState
        val isPlayingTts = args[17] as Boolean
        val referenceMediaType = args[18] as MediaCapability?
        val gallery = sessionMedia.sortedBy { it.createdAt }

        val currentTemplates = if (mediaType == MediaCapability.MUSIC) musicTemplates else templates

        StudioUiState(
            prompt = prompt,
            isGenerating = isGenerating,
            activeGenerationPrompt = if (isGenerating) lastSubmittedPrompt else null,
            mediaType = mediaType,
            gallery = gallery,
            selectedMediaItemIds = selectedMediaItemIds,
            albums = albums,
            settings = settings,
            capabilities = caps,
            templates = currentTemplates,
            selectedTemplateId = templateId,
            isSettingsOpen = isOpen,
            isSaveBottomSheetOpen = isSaveOpen,
            continualMode = continual,
            isContinualGenerationActive = isContinualGenerationActive,
            error = error,
            referenceMediaType = referenceMediaType,
            videoGenerationState = videoGenerationState,
            speechState = speechState,
            isPlayingTts = isPlayingTts
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
        _videoGenerationState.value = VideoGenerationState.Idle
        consumedScrollAnchors.clear()
    }

    fun onTemplateSelected(template: StudioTemplate) {
        _selectedTemplateId.value = if (_selectedTemplateId.value == template.id) null else template.id
    }

    fun onSettingsToggle() {
        _isSettingsOpen.value = !_isSettingsOpen.value
    }

    fun onToggleSaveBottomSheet() {
        _isSaveBottomSheetOpen.value = !_isSaveBottomSheetOpen.value
    }

    fun onAddAlbum(name: String) {
        viewModelScope.launch {
            studioRepository.createAlbum(name)
        }
    }

    fun toggleMediaSelection(id: String) {
        _selectedMediaItemIds.update { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun clearMediaSelection() {
        _selectedMediaItemIds.value = emptySet()
    }

    fun shareMedia(assetIds: Set<String>) {
        val uris = _sessionMedia.value
            .filter { it.id in assetIds }
            .map { it.localUri }
        
        if (uris.isNotEmpty()) {
            shareMediaPort.shareMedia(uris, "*/*")
        }
    }

    fun shareSingleMedia(assetId: String) {
        val asset = _sessionMedia.value.find { it.id == assetId }
        if (asset != null) {
            val mimeType = when (asset.mediaType) {
                MediaCapability.IMAGE -> "image/*"
                MediaCapability.VIDEO -> "video/*"
                MediaCapability.MUSIC -> "audio/*"
            }
            shareMediaPort.shareMedia(listOf(asset.localUri), mimeType)
        }
    }

    fun onSaveSelectedMediaToAlbum(albumId: String) {
        val selectedIds = _selectedMediaItemIds.value
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            _sessionMedia.update { gallery ->
                gallery.map { item ->
                    if (item.id in selectedIds) {
                        val savedAsset = studioRepository.saveMedia(
                            localUri = item.localUri,
                            prompt = item.prompt,
                            mediaType = item.mediaType.name,
                            albumId = albumId
                        )
                        item.copy(id = savedAsset.id, localUri = savedAsset.localUri, albumId = albumId)
                    } else {
                        item
                    }
                }
            }
            
            clearMediaSelection()
            onToggleSaveBottomSheet()
        }
    }

    fun onEditMedia(assetId: String) {
        viewModelScope.launch {
            val sessionItem = _sessionMedia.value.find { it.id == assetId }
            val asset = if (sessionItem != null) {
                StudioMediaAsset(
                    id = sessionItem.id,
                    localUri = sessionItem.localUri,
                    prompt = sessionItem.prompt,
                    mediaType = sessionItem.mediaType.name,
                    createdAt = sessionItem.createdAt,
                    albumId = sessionItem.albumId
                )
            } else {
                studioRepository.getMediaById(assetId)
            } ?: return@launch

            val mediaCapability = MediaCapability.valueOf(asset.mediaType)
            _mediaType.value = mediaCapability
            _prompt.value = asset.prompt
            _referenceMediaType.value = mediaCapability
            _settings.value = when (mediaCapability) {
                MediaCapability.IMAGE -> ImageGenerationSettings(referenceImageUri = asset.localUri)
                MediaCapability.VIDEO -> VideoGenerationSettings(referenceImageUri = asset.localUri)
                MediaCapability.MUSIC -> MusicGenerationSettings()
            }
            _selectedTemplateId.value = null
            stopGeneration()
            consumedScrollAnchors.clear()
        }
    }

    fun onAnimateMedia(assetId: String, autoAnimate: Boolean = false) {
        viewModelScope.launch {
            val sessionItem = _sessionMedia.value.find { it.id == assetId }
            val asset = if (sessionItem != null) {
                StudioMediaAsset(
                    id = sessionItem.id,
                    localUri = sessionItem.localUri,
                    prompt = sessionItem.prompt,
                    mediaType = sessionItem.mediaType.name,
                    createdAt = sessionItem.createdAt,
                    albumId = sessionItem.albumId
                )
            } else {
                studioRepository.getMediaById(assetId)
            } ?: return@launch

            _mediaType.value = MediaCapability.VIDEO
            _prompt.value = asset.prompt
            _referenceMediaType.value = MediaCapability.valueOf(asset.mediaType)
            _settings.value = VideoGenerationSettings(referenceImageUri = asset.localUri)
            stopGeneration()
            consumedScrollAnchors.clear()
            if (autoAnimate) {
                startDetailVideoGeneration(asset)
            }
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
        if (uri == null) {
            _referenceMediaType.value = null
        } else if (_referenceMediaType.value == null) {
            _referenceMediaType.value = MediaCapability.IMAGE
        }

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
            _sessionMedia.update { gallery -> gallery.filter { it.id != id } }
        }
    }

    fun generate() {
        if (_isGenerating.value || _isContinualGenerationActive.value) {
            stopGeneration()
            return
        }

        val submittedPrompt = _prompt.value
        if (submittedPrompt.isBlank()) {
            return
        }

        _lastSubmittedPrompt.value = submittedPrompt
        _prompt.value = ""

        if (_continualMode.value && _mediaType.value == MediaCapability.IMAGE) {
            _isContinualGenerationActive.value = true
        }
        startGeneration(submittedPrompt)
    }

    fun onGenerativeScrollThresholdVisible(anchorAssetId: String, gallerySize: Int) {
        val scrollTriggerKey = "$anchorAssetId:$gallerySize"
        if (_mediaType.value != MediaCapability.IMAGE ||
            !_isContinualGenerationActive.value ||
            _lastSubmittedPrompt.value.isNullOrBlank() ||
            _isGenerating.value ||
            !consumedScrollAnchors.add(scrollTriggerKey)
        ) {
            return
        }

        startGeneration(requireNotNull(_lastSubmittedPrompt.value))
    }

    private fun stopGeneration() {
        _isContinualGenerationActive.value = false
        generationJob?.cancel()
        _isGenerating.value = false
    }

    private fun deactivateContinualGeneration() {
        _isContinualGenerationActive.value = false
    }

    private fun startGeneration(prompt: String) {
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
                    "${selectedTemplate.promptPrefix}$prompt${selectedTemplate.promptSuffix}"
                } else {
                    prompt
                }

                val result = when (val currentSettings = _settings.value) {
                    is ImageGenerationSettings -> {
                        imageGenerationPort.generateImage(finalPrompt, provider, currentSettings.withClampedGenerationCount())
                    }
                    is VideoGenerationSettings -> {
                        generateVideoUseCase(finalPrompt, currentSettings).map { listOf(it) }
                    }
                    is MusicGenerationSettings -> {
                        generateMusicUseCase(finalPrompt, currentSettings).map { listOf(it) }
                    }
                }

                result.onSuccess { images ->
                    images.forEach { bytes ->
                        val localUri = studioRepository.cacheEphemeralMedia(bytes, currentType.name)
                        val newItem = StudioMediaUi(
                            id = UUID.randomUUID().toString(),
                            localUri = localUri,
                            prompt = prompt,
                            mediaType = currentType,
                            createdAt = System.currentTimeMillis()
                        )
                        _sessionMedia.update { it + newItem }
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

    private fun startDetailVideoGeneration(asset: StudioMediaAsset) {
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            _videoGenerationState.value = VideoGenerationState.Loading(asset.id)
            try {
                val settings = (_settings.value as? VideoGenerationSettings) ?: VideoGenerationSettings(referenceImageUri = asset.localUri)
                generateVideoUseCase(asset.prompt, settings)
                    .onSuccess { bytes ->
                        val localUri = studioRepository.cacheEphemeralMedia(bytes, MediaCapability.VIDEO.name)
                        val newItem = StudioMediaUi(
                            id = UUID.randomUUID().toString(),
                            localUri = localUri,
                            prompt = asset.prompt,
                            mediaType = MediaCapability.VIDEO,
                            createdAt = System.currentTimeMillis(),
                            albumId = asset.albumId,
                        )
                        _sessionMedia.update { it + newItem }
                        _videoGenerationState.value = VideoGenerationState.Success(
                            sourceAssetId = asset.id,
                            localUri = localUri,
                        )
                    }.onFailure { cause ->
                        logger.error(TAG, "Detail video generation failed", cause)
                        val message = cause.message ?: "Video generation failed"
                        _error.value = message
                        _videoGenerationState.value = VideoGenerationState.Error(asset.id, message)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(TAG, "Unexpected detail video generation error", e)
                val message = e.message ?: "Video generation failed"
                _error.value = message
                _videoGenerationState.value = VideoGenerationState.Error(asset.id, message)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        speechJob?.cancel()
        streamingTtsJob?.cancel()
        playbackController.stop()
        viewModelScope.launch {
            studioRepository.clearEphemeralCache()
        }
    }

    private fun StudioMediaAsset.toUi() = StudioMediaUi(
        id = id,
        localUri = localUri,
        prompt = prompt,
        mediaType = MediaCapability.valueOf(mediaType),
        createdAt = createdAt,
        albumId = albumId,
    )

    companion object {
        private const val TAG = "MultimodalViewModel"
    }
}
