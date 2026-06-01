package com.browntowndev.enclave.feature.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.model.media.VisualGenerationSettings
import com.browntowndev.enclave.domain.model.media.withClampedGenerationCount
import com.browntowndev.enclave.domain.port.media.ShareMediaPort
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.enclave.feature.studio.coordinators.StudioAlbumSelectionCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioCapabilitiesCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioGenerationCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioPlaybackCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioSpeechCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioUiStateReducer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MultimodalViewModel
    @Inject
    constructor(
        private val shareMediaPort: ShareMediaPort,
        private val studioRepository: StudioRepositoryPort,
        private val defaultModelRepository: DefaultModelRepositoryPort,
        private val mediaProviderRepository: MediaProviderRepositoryPort,
        private val getProviderCapabilitiesUseCase: GetProviderCapabilitiesUseCase,
        private val generationCoordinator: StudioGenerationCoordinator,
        private val albumSelectionCoordinator: StudioAlbumSelectionCoordinator,
        private val speechCoordinator: StudioSpeechCoordinator,
        private val playbackCoordinator: StudioPlaybackCoordinator,
        private val studioUiStateReducer: StudioUiStateReducer,
        private val capabilitiesCoordinator: StudioCapabilitiesCoordinator,
    ) : ViewModel() {
        private val _prompt = MutableStateFlow("")
        private val _mediaType = MutableStateFlow(MediaCapability.IMAGE)
        private val _settings = MutableStateFlow<GenerationSettings>(ImageGenerationSettings())
        private val _selectedTemplateId = MutableStateFlow<String?>(null)
        private val _isSettingsOpen = MutableStateFlow(false)
        private val _continualMode = MutableStateFlow(false)
        private val _referenceMediaType = MutableStateFlow<MediaCapability?>(null)

        private val sessionMedia = albumSelectionCoordinator.sessionMediaFlow(viewModelScope)

        private val activeModelFlow =
            combine(_mediaType, defaultModelRepository.observeDefaults()) { type, defaults ->
                val modelType =
                    when (type) {
                        MediaCapability.IMAGE -> ModelType.IMAGE_GENERATION
                        MediaCapability.VIDEO -> ModelType.VIDEO_GENERATION
                        MediaCapability.MUSIC -> ModelType.MUSIC_GENERATION
                    }
                defaults.find { it.modelType == modelType }
            }

        private val activeProviderFlow =
            activeModelFlow.flatMapLatest { assignment ->
                if (assignment?.mediaProviderId != null) {
                    mediaProviderRepository.getMediaProviders().map { providers ->
                        providers.find { it.id == assignment.mediaProviderId }
                    }
                } else {
                    flowOf(null)
                }
            }

        private val capabilitiesFlow =
            activeProviderFlow.map { provider ->
                getProviderCapabilitiesUseCase(provider?.provider?.name)
            }

        private val hasTtsProviderAssignedFlow =
            defaultModelRepository
                .observeDefaults()
                .map { assignments ->
                    assignments.any { assignment ->
                        assignment.modelType == ModelType.TTS && assignment.ttsProviderId != null
                    }
                }.distinctUntilChanged()

        private val hasVideoModelFlow =
            defaultModelRepository
                .observeDefaults()
                .map { assignments ->
                    assignments.any { assignment ->
                        assignment.modelType == ModelType.VIDEO_GENERATION && assignment.mediaProviderId != null
                    }
                }.distinctUntilChanged()

        private val promptCoreFlow =
            combine(
                _prompt,
                _mediaType,
                _settings,
            ) { prompt, mediaType, settings ->
                PromptCoreInputs(
                    prompt = prompt,
                    mediaType = mediaType,
                    settings = settings,
                )
            }

        private val promptMetaFlow =
            combine(
                _selectedTemplateId,
                _isSettingsOpen,
                _continualMode,
                _referenceMediaType,
            ) { templateId, isSettingsOpen, continualMode, referenceMediaType ->
                PromptMetaInputs(
                    selectedTemplateId = templateId,
                    isSettingsOpen = isSettingsOpen,
                    continualMode = continualMode,
                    referenceMediaType = referenceMediaType,
                )
            }

        private val promptInputsFlow =
            combine(promptCoreFlow, promptMetaFlow) { core, meta ->
                PromptInputs(
                    prompt = core.prompt,
                    mediaType = core.mediaType,
                    settings = core.settings,
                    selectedTemplateId = meta.selectedTemplateId,
                    isSettingsOpen = meta.isSettingsOpen,
                    continualMode = meta.continualMode,
                    referenceMediaType = meta.referenceMediaType,
                )
            }

        private val generationInputsFlow =
            combine(
                generationCoordinator.isGenerating,
                generationCoordinator.lastSubmittedPrompt,
                generationCoordinator.isContinualGenerationActive,
                generationCoordinator.error,
                generationCoordinator.videoGenerationState,
            ) { isGenerating, lastSubmittedPrompt, isContinualGenerationActive, error, videoGenerationState ->
                GenerationInputs(
                    isGenerating = isGenerating,
                    lastSubmittedPrompt = lastSubmittedPrompt,
                    isContinualGenerationActive = isContinualGenerationActive,
                    error = error,
                    videoGenerationState = videoGenerationState,
                )
            }

        private val selectionInputsFlow =
            combine(
                sessionMedia,
                albumSelectionCoordinator.sessionSavedAssetIds,
                albumSelectionCoordinator.selectedMediaItemIds,
                albumSelectionCoordinator.albumsFlow,
                albumSelectionCoordinator.isSaveBottomSheetOpen,
            ) { sessionMediaItems, sessionSavedAssetIds, selectedMediaItemIds, albums, isSaveBottomSheetOpen ->
                SelectionInputs(
                    sessionMedia = sessionMediaItems,
                    sessionSavedAssetIds = sessionSavedAssetIds,
                    selectedMediaItemIds = selectedMediaItemIds,
                    albums = albums,
                    isSaveBottomSheetOpen = isSaveBottomSheetOpen,
                )
            }

        private val playbackInputsFlow =
            combine(
                capabilitiesFlow,
                speechCoordinator.speechState,
                playbackCoordinator.isPlayingTts,
                hasVideoModelFlow,
            ) { capabilities, speechState, isPlayingTts, hasVideoModel ->
                PlaybackInputs(
                    capabilities = capabilities,
                    speechState = speechState,
                    isPlayingTts = isPlayingTts,
                    hasVideoModel = hasVideoModel,
                )
            }

        init {
            generationCoordinator.startObservers(viewModelScope)
            speechCoordinator.observeResults(viewModelScope) { text ->
                _prompt.value = text
            }
            capabilitiesCoordinator.observe(
                scope = viewModelScope,
                settings = _settings,
                capabilitiesFlow = capabilitiesFlow,
            )
        }

        fun onMicClick() {
            speechCoordinator.onMicClick(viewModelScope, _prompt.value)
        }

        fun onPlayTts(text: String) {
            playbackCoordinator.onPlayTts(
                text = text,
                scope = viewModelScope,
                hasTtsProviderAssigned = { hasTtsProviderAssignedFlow.first() },
            )
        }

        fun onStopTts() {
            playbackCoordinator.onStopTts()
        }

        val uiState: StateFlow<StudioUiState> =
            combine(
                promptInputsFlow,
                generationInputsFlow,
                selectionInputsFlow,
                playbackInputsFlow,
            ) { promptInputs, generationInputs, selectionInputs, playbackInputs ->
                studioUiStateReducer.reduce(
                    StudioUiStateReducer.ReduceParams(
                        prompt = promptInputs.prompt,
                        isGenerating = generationInputs.isGenerating,
                        mediaType = promptInputs.mediaType,
                        sessionMedia = selectionInputs.sessionMedia,
                        sessionSavedAssetIds = selectionInputs.sessionSavedAssetIds,
                        selectedMediaItemIds = selectionInputs.selectedMediaItemIds,
                        albums = selectionInputs.albums,
                        settings = promptInputs.settings,
                        capabilities = playbackInputs.capabilities,
                        selectedTemplateId = promptInputs.selectedTemplateId,
                        isSettingsOpen = promptInputs.isSettingsOpen,
                        isSaveBottomSheetOpen = selectionInputs.isSaveBottomSheetOpen,
                        continualMode = promptInputs.continualMode,
                        isContinualGenerationActive = generationInputs.isContinualGenerationActive,
                        error = generationInputs.error,
                        lastSubmittedPrompt = generationInputs.lastSubmittedPrompt,
                        videoGenerationState = generationInputs.videoGenerationState,
                        speechState = playbackInputs.speechState,
                        isPlayingTts = playbackInputs.isPlayingTts,
                        referenceMediaType = promptInputs.referenceMediaType,
                        hasVideoModel = playbackInputs.hasVideoModel,
                    ),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                initialValue = StudioUiState(),
            )

        fun onPromptChange(newPrompt: String) {
            if (_prompt.value == newPrompt) return
            _prompt.value = newPrompt
            generationCoordinator.stopGeneration()
            generationCoordinator.clearScrollAnchors()
        }

        fun onMediaTypeChange(type: MediaCapability) {
            val currentRefUri = (_settings.value as? VisualGenerationSettings)?.referenceImageUri
            _mediaType.value = type
            _settings.value =
                when (type) {
                    MediaCapability.IMAGE -> ImageGenerationSettings(referenceImageUri = currentRefUri)
                    MediaCapability.VIDEO -> VideoGenerationSettings(referenceImageUri = currentRefUri)
                    MediaCapability.MUSIC -> MusicGenerationSettings()
                }
            _selectedTemplateId.value = null
            generationCoordinator.stopGeneration()
            generationCoordinator.resetVideoGenerationState()
            generationCoordinator.clearScrollAnchors()
        }

        fun onTemplateSelected(template: com.browntowndev.enclave.domain.model.media.StudioTemplate) {
            _selectedTemplateId.value = if (_selectedTemplateId.value == template.id) null else template.id
        }

        fun onSettingsToggle() {
            _isSettingsOpen.value = !_isSettingsOpen.value
        }

        fun onToggleSaveBottomSheet() {
            albumSelectionCoordinator.onToggleSaveBottomSheet()
        }

        fun onAddAlbum(name: String) {
            albumSelectionCoordinator.onAddAlbum(viewModelScope, name)
        }

        fun toggleMediaSelection(id: String) {
            albumSelectionCoordinator.toggleMediaSelection(id)
        }

        fun clearMediaSelection() {
            albumSelectionCoordinator.clearMediaSelection()
        }

        fun shareMedia(assetIds: Set<String>) {
            val uris =
                sessionMedia.value
                    .filter { it.id in assetIds }
                    .map { it.localUri }

            if (uris.isNotEmpty()) {
                shareMediaPort.shareMedia(uris, "*/*")
            }
        }

        fun shareSingleMedia(assetId: String) {
            val asset = sessionMedia.value.find { it.id == assetId }
            if (asset != null) {
                val mimeType =
                    when (asset.mediaType) {
                        MediaCapability.IMAGE -> "image/*"
                        MediaCapability.VIDEO -> "video/*"
                        MediaCapability.MUSIC -> "audio/*"
                    }
                shareMediaPort.shareMedia(listOf(asset.localUri), mimeType)
            }
        }

        fun onSaveSelectedMediaToAlbum(albumId: String) {
            albumSelectionCoordinator.onSaveSelectedMediaToAlbum(viewModelScope, albumId)
        }

        fun onEditMedia(
            assetId: String?,
            rehydrateIds: List<String> = emptyList(),
            rehydrateUris: List<String> = emptyList(),
            rehydrateTypes: List<String> = emptyList(),
            rehydratePrompts: List<String> = emptyList(),
        ) {
            val cacheKey = assetId ?: rehydrateUris.firstOrNull()
            if (cacheKey == null || cacheKey == generationCoordinator.lastProcessedEditAssetId) return
            generationCoordinator.lastProcessedEditAssetId = cacheKey

            viewModelScope.launch {
                val sessionItem = assetId?.let { id -> sessionMedia.value.find { it.id == id } }
                val asset =
                    if (sessionItem != null) {
                        sessionItem.toAsset()
                    } else if (assetId != null) {
                        studioRepository.getMediaById(assetId)
                    } else {
                        null
                    }

                val rehydratedAssets =
                    if (asset == null && rehydrateUris.isNotEmpty()) {
                        buildRehydratedAssets(rehydrateIds, rehydrateUris, rehydrateTypes, rehydratePrompts)
                    } else {
                        emptyList()
                    }

                val finalAsset = asset ?: rehydratedAssets.firstOrNull() ?: return@launch

                _mediaType.value = finalAsset.toMediaCapability()
                _referenceMediaType.value = finalAsset.toMediaCapability()
                _selectedTemplateId.value = null
                generationCoordinator.stopGeneration()
                generationCoordinator.clearScrollAnchors()

                rehydratedAssets.forEach { studioRepository.addEphemeralMedia(it) }
            }
        }

        fun clearSession() {
            _prompt.value = ""
            generationCoordinator.clearLastSubmittedPrompt()
            albumSelectionCoordinator.clearSessionSavedAssets()
            generationCoordinator.resetGeneratingFlags()
            generationCoordinator.clearLocalError()
            viewModelScope.launch {
                studioRepository.clearEphemeralCache()
            }
        }

        fun onAnimateMedia(
            assetId: String,
            autoAnimate: Boolean = false,
            customPrompt: String? = null,
        ) {
            if (assetId == generationCoordinator.lastProcessedAnimateAssetId && customPrompt == null && !autoAnimate) {
                return
            }

            if (!autoAnimate) {
                generationCoordinator.lastProcessedAnimateAssetId = assetId
            }

            viewModelScope.launch {
                val sessionItem = sessionMedia.value.find { it.id == assetId }
                val asset =
                    if (sessionItem != null) {
                        sessionItem.toAsset()
                    } else {
                        studioRepository.getMediaById(assetId)
                    } ?: return@launch

                if (!autoAnimate) {
                    _mediaType.value = MediaCapability.VIDEO
                    _referenceMediaType.value = MediaCapability.VIDEO
                    _settings.value =
                        VideoGenerationSettings(
                            aspectRatio = AspectRatio.ONE_ONE,
                            quality = GenerationQuality.SPEED,
                            videoDuration = DEFAULT_VIDEO_DURATION_SECONDS,
                            videoResolution = DEFAULT_VIDEO_RESOLUTION,
                        )
                } else {
                    generationCoordinator.startDetailVideoGeneration(asset, customPrompt)
                }
            }
        }

        fun onContinualModeToggle(enabled: Boolean) {
            _continualMode.value = enabled
            if (!enabled) {
                generationCoordinator.onContinualModeDisabled()
            }
        }

        fun onUpdateSettings(newSettings: GenerationSettings) {
            _settings.value =
                when (newSettings) {
                    is ImageGenerationSettings -> newSettings.withClampedGenerationCount()
                    else -> newSettings
                }
            generationCoordinator.clearScrollAnchors()
        }

        fun onUpdateReferenceImage(uri: String?) {
            val currentSettings = _settings.value
            if (uri == null) {
                _referenceMediaType.value = null
            } else if (_referenceMediaType.value == null) {
                _referenceMediaType.value = MediaCapability.IMAGE
            }

            _settings.value =
                when (currentSettings) {
                    is ImageGenerationSettings -> currentSettings.copy(referenceImageUri = uri).withClampedGenerationCount()
                    is VideoGenerationSettings -> currentSettings.copy(referenceImageUri = uri)
                    is MusicGenerationSettings -> currentSettings
                }
        }

        fun onClearReferenceImage() {
            onUpdateReferenceImage(null)
        }

        fun clearError() {
            generationCoordinator.clearError(viewModelScope)
        }

        fun deleteMedia(id: String) {
            generationCoordinator.clearEditAssetId(id)
            generationCoordinator.clearAnimateAssetId(id)
            viewModelScope.launch {
                studioRepository.deleteMedia(id)
                studioRepository.removeEphemeralMedia(id)
            }
        }

        fun generate() {
            if (generationCoordinator.isGenerating.value || generationCoordinator.isContinualGenerationActive.value) {
                generationCoordinator.stopGeneration()
                return
            }

            val submittedPrompt = _prompt.value
            if (submittedPrompt.isBlank()) return

            generationCoordinator.beginGenerationAttempt(
                submittedPrompt = submittedPrompt,
                mediaType = _mediaType.value,
                continualMode = _continualMode.value,
                settings = _settings.value,
            )
            _prompt.value = ""
        }

        fun onGenerativeScrollThresholdVisible(anchorAssetId: String) {
            generationCoordinator.onGenerativeScrollThresholdVisible(
                anchorAssetId = anchorAssetId,
                mediaType = _mediaType.value,
            )
        }

        override fun onCleared() {
            super.onCleared()
            speechCoordinator.onCleared()
            playbackCoordinator.onCleared()
        }

        private data class PromptCoreInputs(
            val prompt: String,
            val mediaType: MediaCapability,
            val settings: GenerationSettings,
        )

        private data class PromptMetaInputs(
            val selectedTemplateId: String?,
            val isSettingsOpen: Boolean,
            val continualMode: Boolean,
            val referenceMediaType: MediaCapability?,
        )

        private data class PromptInputs(
            val prompt: String,
            val mediaType: MediaCapability,
            val settings: GenerationSettings,
            val selectedTemplateId: String?,
            val isSettingsOpen: Boolean,
            val continualMode: Boolean,
            val referenceMediaType: MediaCapability?,
        )

        private data class GenerationInputs(
            val isGenerating: Boolean,
            val lastSubmittedPrompt: String?,
            val isContinualGenerationActive: Boolean,
            val error: String?,
            val videoGenerationState: VideoGenerationState,
        )

        private data class SelectionInputs(
            val sessionMedia: List<StudioMediaUi>,
            val sessionSavedAssetIds: Set<String>,
            val selectedMediaItemIds: Set<String>,
            val albums: List<GalleryAlbumUi>,
            val isSaveBottomSheetOpen: Boolean,
        )

        private data class PlaybackInputs(
            val capabilities: ProviderCapabilities?,
            val speechState: SpeechState,
            val isPlayingTts: Boolean,
            val hasVideoModel: Boolean,
        )

        private companion object {
            private const val SUBSCRIBE_TIMEOUT_MS = 5_000L
            private const val DEFAULT_VIDEO_DURATION_SECONDS = 5
            private const val DEFAULT_VIDEO_RESOLUTION = "480p"
        }
    }
