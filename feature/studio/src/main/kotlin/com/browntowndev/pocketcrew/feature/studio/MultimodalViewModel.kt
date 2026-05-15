package com.browntowndev.pocketcrew.feature.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.media.*

import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DEFAULT_GALLERY_ALBUM_ID
import com.browntowndev.pocketcrew.domain.port.media.ShareMediaPort
import com.browntowndev.pocketcrew.domain.usecase.media.GenerateMusicUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.GenerateVideoUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.port.media.StudioGenerationDispatcherPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackStatus
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions", "MagicNumber")
class MultimodalViewModel @Inject constructor(
    private val shareMediaPort: ShareMediaPort,
    private val studioRepository: StudioRepositoryPort,
    private val dispatcher: StudioGenerationDispatcherPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val mediaProviderRepository: MediaProviderRepositoryPort,
    private val getProviderCapabilitiesUseCase: GetProviderCapabilitiesUseCase,
    private val chatUseCases: ChatUseCases,
    private val playbackController: TtsPlaybackControllerPort,
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
    private val _sessionMedia = studioRepository.observeEphemeralMedia()
        .map { assets -> assets.map { it.toUi() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    private val _sessionSavedAssetIds = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedMediaItemIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isSaveBottomSheetOpen = MutableStateFlow(false)
    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    private val _stopSpeechSignal = MutableStateFlow(false)
    private val _isPlayingTts = MutableStateFlow(false)
    private val _videoGenerationState = MutableStateFlow<VideoGenerationState>(VideoGenerationState.Idle)

    private var lastProcessedEditAssetId: String? = null
    private var lastProcessedAnimateAssetId: String? = null

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
            val item = asset.toUi().copy(
                albumId = asset.albumId ?: DEFAULT_GALLERY_ALBUM_ID
            )
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

    private val hasVideoModelFlow = defaultModelRepository.observeDefaults()
        .map { assignments ->
            assignments.any { assignment ->
                assignment.modelType == ModelType.VIDEO_GENERATION && assignment.mediaProviderId != null
            }
        }
        .distinctUntilChanged()

    init {
        observeSpeechResults()
        observeCapabilities()
        observeGenerationErrors()
        observeEphemeralMedia()
    }

    private fun observeEphemeralMedia() {
        studioRepository.observeEphemeralMedia()
            .onEach { 
                _isGenerating.value = false 
            }
            .launchIn(viewModelScope)
    }

    private fun observeGenerationErrors() {
        studioRepository.observeGenerationError()
            .onEach { error ->
                if (error != null) {
                    _error.value = error
                    _isGenerating.value = false
                    _isContinualGenerationActive.value = false
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeCapabilities() {
        capabilitiesFlow
            .onEach { caps ->
                val currentSettings = _settings.value
                if (currentSettings is ImageGenerationSettings) {
                    if (caps.supportedImageQualities.isNotEmpty() && !caps.supportedImageQualities.contains(currentSettings.quality)) {
                        _settings.value = currentSettings.copy(quality = caps.supportedImageQualities.first())
                    }
                } else if (currentSettings is VideoGenerationSettings) {
                    if (caps.supportedVideoQualities.isNotEmpty() && !caps.supportedVideoQualities.contains(currentSettings.quality)) {
                        _settings.value = currentSettings.copy(quality = caps.supportedVideoQualities.first())
                    }
                }
            }
            .launchIn(viewModelScope)
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

    @Suppress("UnusedPrivateMember")
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
        _sessionSavedAssetIds,
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
        hasVideoModelFlow,
    ) { args ->
        val prompt = args[0] as String
        val isGenerating = args[1] as Boolean
        val mediaType = args[2] as MediaCapability
        @Suppress("UNCHECKED_CAST")
        val sessionMedia = args[3] as List<StudioMediaUi>
        @Suppress("UNCHECKED_CAST")
        val sessionSavedAssetIds = args[4] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val selectedMediaItemIds = args[5] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val albums = args[6] as List<GalleryAlbumUi>
        val settings = args[7] as GenerationSettings
        val caps = args[8] as ProviderCapabilities?
        val templateId = args[9] as String?
        val isOpen = args[10] as Boolean
        val isSaveOpen = args[11] as Boolean
        val continual = args[12] as Boolean
        val isContinualGenerationActive = args[13] as Boolean
        val error = args[14] as String?
        val lastSubmittedPrompt = args[15] as String?
        val videoGenerationState = args[16] as VideoGenerationState
        val speechState = args[17] as SpeechState
        val isPlayingTts = args[18] as Boolean
        val referenceMediaType = args[19] as MediaCapability?
        val hasVideoModel = args[20] as Boolean
        
        val gallery = (albums.flatMap { it.items }.filter { it.id in sessionSavedAssetIds } + sessionMedia)
            .distinctBy { it.localUri.substringAfterLast('/') }
            .sortedBy { it.createdAt }

        val groupedGallery = gallery.groupBy { it.prompt }.map { (prompt, items) ->
            StudioGalleryGroup(prompt = prompt, mediaItems = items)
        }.sortedBy { it.mediaItems.maxOf { item -> item.createdAt } }

        val currentTemplates = if (mediaType == MediaCapability.MUSIC) musicTemplates else templates

        StudioUiState(
            prompt = prompt,
            isGenerating = isGenerating,
            activeGenerationPrompt = if (isGenerating) lastSubmittedPrompt else null,
            mediaType = mediaType,
            gallery = gallery,
            groupedGallery = groupedGallery,
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
            isPlayingTts = isPlayingTts,
            hasVideoModel = hasVideoModel,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioUiState()
    )

    private val consumedScrollAnchors = mutableSetOf<String>()

    fun onPromptChange(newPrompt: String) {
        if (_prompt.value == newPrompt) return
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
            val currentEphemeral = studioRepository.observeEphemeralMedia().first()
            currentEphemeral
                .filter { it.id in selectedIds }
                .distinctBy { it.id }
                .forEach { item ->
                    val savedAsset = studioRepository.promoteEphemeralMedia(item.id, albumId)
                    _sessionSavedAssetIds.value = _sessionSavedAssetIds.value + savedAsset.id
                }
            
            clearMediaSelection()
            onToggleSaveBottomSheet()
        }
    }

    fun onEditMedia(
        assetId: String?,
        rehydrateIds: List<String> = emptyList(),
        rehydrateUris: List<String> = emptyList(),
        rehydrateTypes: List<String> = emptyList(),
        rehydratePrompts: List<String> = emptyList()
    ) {
        val cacheKey = assetId ?: rehydrateUris.firstOrNull()
        if (cacheKey == null || cacheKey == lastProcessedEditAssetId) return
        lastProcessedEditAssetId = cacheKey

        viewModelScope.launch {
            val sessionItem = assetId?.let { id -> _sessionMedia.value.find { it.id == id } }
            val asset = if (sessionItem != null) {
                StudioMediaAsset(
                    id = sessionItem.id,
                    localUri = sessionItem.localUri,
                    prompt = sessionItem.prompt,
                    mediaType = sessionItem.mediaType.name,
                    createdAt = sessionItem.createdAt,
                    albumId = sessionItem.albumId
                )
            } else if (assetId != null) {
                studioRepository.getMediaById(assetId)
            } else null

            val rehydratedAssets = if (asset == null && rehydrateUris.isNotEmpty()) {
                val baseTime = System.currentTimeMillis()
                rehydrateUris.indices.mapNotNull { i ->
                    val uri = rehydrateUris[i]
                    val type = rehydrateTypes.getOrNull(i)
                    val p = rehydratePrompts.getOrNull(i)
                    val id = rehydrateIds.getOrNull(i) ?: "rehydrated_${baseTime}_$i"
                    
                    StudioMediaAsset(
                        id = id,
                        localUri = uri,
                        prompt = p ?: "",
                        mediaType = type ?: "IMAGE",
                        createdAt = baseTime + i
                    )
                }
            } else emptyList()

            val finalAsset = asset ?: rehydratedAssets.firstOrNull()
            if (finalAsset == null) return@launch

            val mediaCapability = try {
                MediaCapability.valueOf(finalAsset.mediaType)
            } catch (_: Exception) {
                MediaCapability.IMAGE
            }
            
            _mediaType.value = mediaCapability
            _referenceMediaType.value = mediaCapability
            _selectedTemplateId.value = null
            stopGeneration()
            consumedScrollAnchors.clear()

            // Add all rehydrated assets to session media so they show up in the gallery
            rehydratedAssets.forEach { studioRepository.addEphemeralMedia(it) }
        }
    }

    fun clearSession() {
        _prompt.value = ""
        _lastSubmittedPrompt.value = null
        _sessionSavedAssetIds.value = emptySet()
        _selectedMediaItemIds.value = emptySet()
        _isGenerating.value = false
        _error.value = null
        viewModelScope.launch {
            studioRepository.clearEphemeralCache()
        }
    }

    fun onAnimateMedia(assetId: String, autoAnimate: Boolean = false, customPrompt: String? = null) {
        if (assetId == lastProcessedAnimateAssetId && customPrompt == null && !autoAnimate) return
        
        if (!autoAnimate) {
            lastProcessedAnimateAssetId = assetId
        }

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

            if (!autoAnimate) {
                // Update the shared state so the input bar reflects the current context
                _mediaType.value = MediaCapability.VIDEO
                _referenceMediaType.value = MediaCapability.VIDEO
                _settings.value = VideoGenerationSettings(
                    aspectRatio = AspectRatio.ONE_ONE,
                    quality = GenerationQuality.SPEED,
                    videoDuration = 5,
                    videoResolution = "480p"
                )
            } else {
                // For autoAnimate, we just want to trigger the generation with the asset
                startDetailVideoGeneration(asset, customPrompt)
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
        viewModelScope.launch {
            studioRepository.clearGenerationError()
        }
    }

    fun deleteMedia(id: String) {
        if (id == lastProcessedEditAssetId) {
            lastProcessedEditAssetId = null
        }
        if (id == lastProcessedAnimateAssetId) {
            lastProcessedAnimateAssetId = null
        }
        viewModelScope.launch {
            studioRepository.deleteMedia(id)
            studioRepository.removeEphemeralMedia(id)
        }
    }

    fun generate() {
        if (_isGenerating.value || _isContinualGenerationActive.value) {
            stopGeneration()
            return
        }

        lastProcessedEditAssetId = null
        lastProcessedAnimateAssetId = null

        val submittedPrompt = _prompt.value
        if (submittedPrompt.isBlank()) return

        _lastSubmittedPrompt.value = submittedPrompt
        _prompt.value = ""

        if (_continualMode.value && _mediaType.value == MediaCapability.IMAGE) {
            _isContinualGenerationActive.value = true
        }
        
        startGeneration(submittedPrompt)
    }

    fun onGenerativeScrollThresholdVisible(anchorAssetId: String) {
        val scrollTriggerKey = anchorAssetId
        val cannotContinueGeneration = _mediaType.value != MediaCapability.IMAGE ||
            !_isContinualGenerationActive.value ||
            _lastSubmittedPrompt.value.isNullOrBlank() ||
            _isGenerating.value
        if (cannotContinueGeneration ||
            !consumedScrollAnchors.add(scrollTriggerKey)
        ) {
            return
        }

        startGeneration(requireNotNull(_lastSubmittedPrompt.value))
    }

    private fun stopGeneration() {
        _isContinualGenerationActive.value = false
        _isGenerating.value = false
        dispatcher.stopGeneration()
    }

    private fun startGeneration(prompt: String) {
        dispatcher.startGeneration(prompt, _mediaType.value)
        _isGenerating.value = true
    }

    private fun startDetailVideoGeneration(asset: StudioMediaAsset, customPrompt: String? = null) {
        val prompt = customPrompt ?: asset.prompt ?: ""
        dispatcher.startGeneration(prompt, MediaCapability.VIDEO)
        _isGenerating.value = true
    }

    override fun onCleared() {
        super.onCleared()
        speechJob?.cancel()
        streamingTtsJob?.cancel()
        playbackController.stop()
    }

    private fun StudioMediaAsset.toUi() = StudioMediaUi(
        id = id,
        localUri = localUri,
        prompt = prompt,
        mediaType = MediaCapability.valueOf(mediaType),
        createdAt = createdAt,
        albumId = albumId,
    )
}
