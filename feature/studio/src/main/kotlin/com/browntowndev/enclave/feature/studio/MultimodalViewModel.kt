package com.browntowndev.enclave.feature.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.inference.ShouldModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaModelTypeUseCase
import com.browntowndev.enclave.feature.studio.coordinators.CapabilitiesPlaybackInputs
import com.browntowndev.enclave.feature.studio.coordinators.GenerationInputs
import com.browntowndev.enclave.feature.studio.coordinators.PlaybackInputs
import com.browntowndev.enclave.feature.studio.coordinators.PromptCoreInputs
import com.browntowndev.enclave.feature.studio.coordinators.PromptInputs
import com.browntowndev.enclave.feature.studio.coordinators.PromptMetaInputs
import com.browntowndev.enclave.feature.studio.coordinators.SelectionInputs
import com.browntowndev.enclave.feature.studio.coordinators.StudioAlbumSelectionCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioCapabilitiesCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioGenerationCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioMediaActionsCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioPlaybackCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioPromptCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioRemoteCapabilitiesCoordinator
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MultimodalViewModel
    @Inject
    constructor(
        private val defaultModelRepository: DefaultModelRepositoryPort,
        private val mediaProviderRepository: MediaProviderRepositoryPort,
        private val moderatePromptUseCase: ModeratePromptUseCase,
        private val shouldModeratePromptUseCase: ShouldModeratePromptUseCase,
        private val resolveMediaModelTypeUseCase: ResolveMediaModelTypeUseCase,
        private val generationCoordinator: StudioGenerationCoordinator,
        private val albumSelectionCoordinator: StudioAlbumSelectionCoordinator,
        private val speechCoordinator: StudioSpeechCoordinator,
        private val playbackCoordinator: StudioPlaybackCoordinator,
        private val studioUiStateReducer: StudioUiStateReducer,
        private val capabilitiesCoordinator: StudioCapabilitiesCoordinator,
        private val remoteCapabilitiesCoordinator: StudioRemoteCapabilitiesCoordinator,
        val promptCoordinator: StudioPromptCoordinator,
        val mediaActionsCoordinator: StudioMediaActionsCoordinator,
    ) : ViewModel() {
        private val sessionMedia = albumSelectionCoordinator.sessionMediaFlow(viewModelScope)

        private val activeModelFlow =
            combine(
                promptCoordinator.mediaType,
                promptCoordinator.settings,
                defaultModelRepository.observeDefaults(),
            ) { type, settings, defaults ->
                val modelType = resolveMediaModelTypeUseCase(type, settings)
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

        private val _capabilities = MutableStateFlow<ProviderCapabilities?>(null)
        private val _isCapabilitiesLoading = MutableStateFlow(false)

        private val hasTtsProviderAssignedFlow =
            defaultModelRepository
                .observeDefaults()
                .map { assignments ->
                    assignments.any { assignment ->
                        assignment.modelType == ModelType.TTS && assignment.ttsProviderId != null
                    }
                }.distinctUntilChanged()

        private val hasImageEditModelFlow =
            defaultModelRepository
                .observeDefaults()
                .map { assignments ->
                    assignments.any { assignment ->
                        assignment.modelType == ModelType.IMAGE_EDIT && assignment.mediaProviderId != null
                    }
                }.distinctUntilChanged()

        private val hasImageToVideoModelFlow =
            defaultModelRepository
                .observeDefaults()
                .map { assignments ->
                    assignments.any { assignment ->
                        assignment.modelType == ModelType.IMAGE_TO_VIDEO && assignment.mediaProviderId != null
                    }
                }.distinctUntilChanged()

        private val promptCoreFlow =
            combine(
                promptCoordinator.prompt,
                promptCoordinator.mediaType,
                promptCoordinator.settings,
            ) { prompt, mediaType, settings ->
                PromptCoreInputs(
                    prompt = prompt,
                    mediaType = mediaType,
                    settings = settings,
                )
            }

        private val promptMetaFlow =
            combine(
                promptCoordinator.selectedTemplateId,
                promptCoordinator.isSettingsOpen,
                promptCoordinator.continualMode,
                promptCoordinator.referenceMediaType,
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

        private val capabilitiesPlaybackFlow =
            combine(
                _capabilities,
                _isCapabilitiesLoading,
                activeProviderFlow,
            ) { capabilities, isCapabilitiesLoading, activeProvider ->
                CapabilitiesPlaybackInputs(
                    capabilities = capabilities,
                    isCapabilitiesLoading =
                        isCapabilitiesLoading &&
                            remoteCapabilitiesCoordinator.requiresRemoteCapabilitiesLoading(activeProvider),
                )
            }

        private val playbackInputsFlow =
            combine(
                capabilitiesPlaybackFlow,
                speechCoordinator.speechState,
                playbackCoordinator.isPlayingTts,
                hasImageEditModelFlow,
                hasImageToVideoModelFlow,
            ) { capabilitiesPlayback, speechState, isPlayingTts, hasImageEdit, hasImageToVideo ->
                PlaybackInputs(
                    capabilities = capabilitiesPlayback.capabilities,
                    isCapabilitiesLoading = capabilitiesPlayback.isCapabilitiesLoading,
                    speechState = speechState,
                    isPlayingTts = isPlayingTts,
                    hasImageEditModel = hasImageEdit,
                    hasImageToVideoModel = hasImageToVideo,
                )
            }

        init {
            generationCoordinator.startObservers(viewModelScope)
            speechCoordinator.observeResults(viewModelScope) { text ->
                promptCoordinator.prompt.value = text
            }
            capabilitiesCoordinator.observe(
                scope = viewModelScope,
                settings = promptCoordinator.settings,
                capabilitiesFlow = _capabilities,
            )
            remoteCapabilitiesCoordinator.attach(
                scope = viewModelScope,
                activeProviderFlow = activeProviderFlow,
                mediaType = promptCoordinator.mediaType,
                capabilities = _capabilities,
                isCapabilitiesLoading = _isCapabilitiesLoading,
            )
            mediaActionsCoordinator.attach(
                scope = viewModelScope,
                sessionMedia = sessionMedia,
                hasImageToVideoModel = hasImageToVideoModelFlow,
            )
        }

        fun onMicClick() {
            speechCoordinator.onMicClick(viewModelScope, promptCoordinator.prompt.value)
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
                        isCapabilitiesLoading = playbackInputs.isCapabilitiesLoading,
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
                        hasImageEditModel = playbackInputs.hasImageEditModel,
                        hasImageToVideoModel = playbackInputs.hasImageToVideoModel,
                    ),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                initialValue = StudioUiState(),
            )

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

        fun onSaveSelectedMediaToAlbum(albumId: String) {
            albumSelectionCoordinator.onSaveSelectedMediaToAlbum(viewModelScope, albumId)
        }

        fun clearSession() {
            promptCoordinator.clearSession(viewModelScope, albumSelectionCoordinator)
        }

        fun clearError() {
            generationCoordinator.clearError(viewModelScope)
        }

        fun generate() {
            if (generationCoordinator.isGenerating.value || generationCoordinator.isContinualGenerationActive.value) {
                generationCoordinator.stopGeneration()
                return
            }

            val submittedPrompt = promptCoordinator.prompt.value
            if (submittedPrompt.isBlank()) return

            viewModelScope.launch {
                try {
                    val shouldModerate = shouldModeratePromptUseCase(ModelType.VISION)
                    if (shouldModerate) {
                        val moderationResult = moderatePromptUseCase(submittedPrompt)
                        if (moderationResult.blocked) {
                            generationCoordinator.rejectPrompt(PROMPT_REJECTED_MESSAGE)
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MultimodalViewModel", "Prompt moderation failed", e)
                    generationCoordinator.rejectPrompt("Failed to verify prompt safety. Please try again.")
                    return@launch
                }

                generationCoordinator.beginGenerationAttempt(
                    submittedPrompt = submittedPrompt,
                    mediaType = promptCoordinator.mediaType.value,
                    continualMode = promptCoordinator.continualMode.value,
                    settings = promptCoordinator.settings.value,
                )
                promptCoordinator.prompt.update { "" }
            }
        }

        fun onGenerativeScrollThresholdVisible(anchorAssetId: String) {
            generationCoordinator.onGenerativeScrollThresholdVisible(
                anchorAssetId = anchorAssetId,
                mediaType = promptCoordinator.mediaType.value,
            )
        }

        override fun onCleared() {
            super.onCleared()
            speechCoordinator.onCleared()
            playbackCoordinator.onCleared()
        }

        private companion object {
            private const val SUBSCRIBE_TIMEOUT_MS = 5_000L
            private const val PROMPT_REJECTED_MESSAGE = "Prompt rejected due to moderation"
        }
    }
