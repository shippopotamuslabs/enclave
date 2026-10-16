package com.browntowndev.enclave.feature.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.model.media.StudioTemplate

import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.inference.ShouldModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.inference.SubmitModerationReportUseCase
import com.browntowndev.enclave.domain.usecase.media.GetImageGenerationCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.GetStudioCapablePresetsUseCase
import com.browntowndev.enclave.domain.usecase.studio.GetMediaProvidersUseCase
import com.browntowndev.enclave.domain.usecase.studio.GetSelectedStudioMediaTypeUseCase
import com.browntowndev.enclave.domain.usecase.studio.ObserveDefaultModelAssignmentsUseCase
import com.browntowndev.enclave.domain.usecase.studio.SetSelectedStudioMediaTypeUseCase

import com.browntowndev.enclave.feature.studio.coordinators.CapabilitiesPlaybackInputs
import com.browntowndev.enclave.feature.studio.coordinators.GenerationInputs
import com.browntowndev.enclave.feature.studio.coordinators.OverrideInputs
import com.browntowndev.enclave.feature.studio.coordinators.PlaybackInputs
import com.browntowndev.enclave.feature.studio.coordinators.PromptCoreInputs
import com.browntowndev.enclave.feature.studio.coordinators.PromptInputs
import com.browntowndev.enclave.feature.studio.coordinators.PromptMetaInputs
import com.browntowndev.enclave.feature.studio.coordinators.SelectionInputs
import com.browntowndev.enclave.feature.studio.coordinators.StudioAlbumSelectionCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioCapabilitiesCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioGenerationCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioMediaActionsCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioModelOverrideCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioResolvedModelCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioPlaybackCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioPromptCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioRemoteCapabilitiesCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioSpeechCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioUiStateReducer
import com.browntowndev.enclave.feature.studio.StudioCapablePresetsUi
import com.browntowndev.enclave.feature.studio.StudioModelOptionUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
class MultimodalViewModel
    @Inject
    constructor(
        private val observeDefaultModelAssignments: ObserveDefaultModelAssignmentsUseCase,
        private val getMediaProviders: GetMediaProvidersUseCase,
        private val moderatePromptUseCase: ModeratePromptUseCase,
        private val shouldModeratePromptUseCase: ShouldModeratePromptUseCase,
        private val submitModerationReportUseCase: SubmitModerationReportUseCase,
        private val getImageGenerationCapabilitiesUseCase: GetImageGenerationCapabilitiesUseCase,
        private val generationCoordinator: StudioGenerationCoordinator,
        private val albumSelectionCoordinator: StudioAlbumSelectionCoordinator,
        private val speechCoordinator: StudioSpeechCoordinator,
        private val playbackCoordinator: StudioPlaybackCoordinator,
        private val studioUiStateReducer: StudioUiStateReducer,
        private val capabilitiesCoordinator: StudioCapabilitiesCoordinator,
        private val remoteCapabilitiesCoordinator: StudioRemoteCapabilitiesCoordinator,
        private val promptCoordinator: StudioPromptCoordinator,
        private val mediaActionsCoordinator: StudioMediaActionsCoordinator,
        private val getSelectedStudioMediaType: GetSelectedStudioMediaTypeUseCase,
        private val setSelectedStudioMediaType: SetSelectedStudioMediaTypeUseCase,
        private val overrideCoordinator: StudioModelOverrideCoordinator,
        private val resolvedModelCoordinator: StudioResolvedModelCoordinator,
        private val getStudioCapablePresetsUseCase: GetStudioCapablePresetsUseCase,
    ) : ViewModel() {
        private val generationAttemptInFlight = AtomicBoolean(false)
        private val sessionMedia = albumSelectionCoordinator.sessionMediaFlow(viewModelScope)

        private val _moderationEvents = Channel<ModerationEvent>(Channel.BUFFERED)
        val moderationEvents: Flow<ModerationEvent> = _moderationEvents.receiveAsFlow()

        private val resolvedStudioModelFlow =
            resolvedModelCoordinator.observeResolvedModel(
                overrideState = overrideCoordinator.state,
                mediaType = promptCoordinator.mediaType,
                settings = promptCoordinator.settings,
                defaults = observeDefaultModelAssignments(),
            )

        private val activeModelDisplayNameFlow =
            resolvedStudioModelFlow
                .map { it.displayName }
                .distinctUntilChanged()

        private val activeProviderFlow =
            resolvedStudioModelFlow.flatMapLatest { resolved ->
                val providerId = resolved.mediaProviderId
                if (providerId != null) {
                    getMediaProviders().map { providers ->
                        providers.find { it.id == providerId }
                    }
                } else {
                    flowOf(null)
                }
            }

        /**
         * Emits the on-device backend capabilities while the active image-generation slot is a
         * local stable-diffusion (Bonsai) assignment, or `null` for remote/other slots so the
         * remote capabilities coordinator stays in charge of provider-backed schemas.
         *
         * Capabilities are sourced through [GetImageGenerationCapabilitiesUseCase] so Studio drives
         * its pickers from the same capability shape as remote providers.
         */
        private val localImageCapabilitiesFlow =
            resolvedStudioModelFlow
                .map { resolved ->
                    resolved.localConfigId?.takeIf { resolved.mediaProviderId == null }
                }.distinctUntilChanged()
                .flatMapLatest { localConfigId: LocalModelConfigurationId? ->
                    if (localConfigId == null) {
                        flowOf<ProviderCapabilities?>(null)
                    } else {
                        flow {
                            emit(getImageGenerationCapabilitiesUseCase.localCapabilities(localConfigId))
                        }
                    }
                }

        private val _capabilities = MutableStateFlow<ProviderCapabilities?>(null)
        private val _isCapabilitiesLoading = MutableStateFlow(false)

        private val hasTtsProviderAssignedFlow =
            observeDefaultModelAssignments()
                .map { assignments ->
                    assignments.any { assignment ->
                        assignment.modelType == ModelType.TTS && assignment.ttsProviderId != null
                    }
                }.distinctUntilChanged()

        private val hasImageEditModelFlow =
            observeDefaultModelAssignments()
                .map { assignments ->
                    assignments.any { assignment ->
                        assignment.modelType == ModelType.IMAGE_EDIT &&
                            (assignment.mediaProviderId != null || assignment.localConfigId != null)
                    }
                }.distinctUntilChanged()

        private val hasImageToVideoModelFlow =
            observeDefaultModelAssignments()
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
                combine(
                    generationCoordinator.isGenerating,
                    generationCoordinator.lastSubmittedPrompt,
                    generationCoordinator.isContinualGenerationActive,
                    generationCoordinator.error,
                    generationCoordinator.generationProgressLabel,
                ) { isGenerating, lastSubmittedPrompt, isContinualGenerationActive, error, generationProgressLabel ->
                    GenerationInputs(
                        isGenerating = isGenerating,
                        lastSubmittedPrompt = lastSubmittedPrompt,
                        isContinualGenerationActive = isContinualGenerationActive,
                        error = error,
                        generationProgressLabel = generationProgressLabel,
                        activeGenerationExpectedCount = 1,
                        activeGenerationBaselineMediaCount = 0,
                        videoGenerationState = VideoGenerationState.Idle,
                    )
                },
                combine(
                    generationCoordinator.activeGenerationExpectedCount,
                    generationCoordinator.activeGenerationBaselineMediaCount,
                ) { activeGenerationExpectedCount, activeGenerationBaselineMediaCount ->
                    activeGenerationExpectedCount to activeGenerationBaselineMediaCount
                },
            ) { generationInputs, generationTracking ->
                generationInputs.copy(
                    activeGenerationExpectedCount = generationTracking.first,
                    activeGenerationBaselineMediaCount = generationTracking.second,
                )
            }.combine(generationCoordinator.videoGenerationState) { generationInputs, videoGenerationState ->
                generationInputs.copy(videoGenerationState = videoGenerationState)
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
                activeModelDisplayNameFlow,
            ) { capabilities, isCapabilitiesLoading, activeProvider, activeModelDisplayName ->
                CapabilitiesPlaybackInputs(
                    capabilities = capabilities,
                    isCapabilitiesLoading =
                        isCapabilitiesLoading &&
                            remoteCapabilitiesCoordinator.requiresRemoteCapabilitiesLoading(activeProvider),
                    activeModelDisplayName = activeModelDisplayName,
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
                    activeModelDisplayName = capabilitiesPlayback.activeModelDisplayName,
                )
            }

        private val modelPickerAssetsFlow: Flow<StudioCapablePresetsUi> =
            overrideCoordinator.state
                .map { it.picker.modelType }
                .distinctUntilChanged()
                .flatMapLatest { modelType ->
                    if (modelType != null) {
                        getStudioCapablePresetsUseCase(modelType).map { it.toPickerUi() }
                    } else {
                        flowOf(StudioCapablePresetsUi.EMPTY)
                    }
                }

        private val overrideInputsFlow: Flow<OverrideInputs> =
            combine(
                overrideCoordinator.state,
                modelPickerAssetsFlow,
            ) { overrideState, pickerAssets ->
                OverrideInputs(
                    modelPickerAssets = pickerAssets,
                    isModelPickerVisible = overrideState.picker.isVisible,
                    selectedModelIdInPicker = overrideState.picker.pendingSelection?.optionId(),
                    resolvedModelTypeLabel = overrideState.picker.modelType?.displayName(),
                )
            }

        init {
            generationCoordinator.startObservers(
                scope = viewModelScope,
                onActiveGeneration = promptCoordinator::restoreActiveGeneration,
            )
            viewModelScope.launch {
                val persistedMediaType = getSelectedStudioMediaType().first()
                promptCoordinator.restoreLastMediaType(persistedMediaType)
            }
            generationCoordinator.moderationEvents
                .onEach { event -> _moderationEvents.send(event) }
                .launchIn(viewModelScope)
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
            // The remote coordinator only resolves provider-backed schemas; feed the on-device
            // stable-diffusion capabilities directly when the active image slot is local.
            combine(localImageCapabilitiesFlow, promptCoordinator.mediaType) { localCapabilities, mediaType ->
                localCapabilities to mediaType
            }.onEach { (localCapabilities, mediaType) ->
                if (localCapabilities != null && mediaType == MediaCapability.IMAGE) {
                    _isCapabilitiesLoading.value = false
                    _capabilities.value = localCapabilities
                }
            }.launchIn(viewModelScope)
            mediaActionsCoordinator.attach(
                scope = viewModelScope,
                sessionMedia = sessionMedia,
                hasImageToVideoModel = hasImageToVideoModelFlow,
            )
            resolvedModelCoordinator.attachProviderCache(viewModelScope)
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

        fun onPromptChange(newPrompt: String) {
            promptCoordinator.onPromptChange(newPrompt)
        }

        fun onUpdateReferenceImage(uri: String?) {
            val previousModelType = currentResolvedModelType()
            promptCoordinator.onUpdateReferenceImage(uri)
            resolvedModelCoordinator.revalidateOverrideIfModelTypeChanged(
                overrideCoordinator = overrideCoordinator,
                previousModelType = previousModelType,
                mediaType = promptCoordinator.mediaType.value,
                settings = promptCoordinator.settings.value,
                scope = viewModelScope,
            )
        }

        fun onSettingsToggle() {
            promptCoordinator.onSettingsToggle()
        }

        fun onUpdateSettings(newSettings: GenerationSettings) {
            val previousModelType = currentResolvedModelType()
            promptCoordinator.onUpdateSettings(newSettings)
            resolvedModelCoordinator.revalidateOverrideIfModelTypeChanged(
                overrideCoordinator = overrideCoordinator,
                previousModelType = previousModelType,
                mediaType = promptCoordinator.mediaType.value,
                settings = promptCoordinator.settings.value,
                scope = viewModelScope,
            )
        }

        fun onContinualModeToggle(enabled: Boolean) {
            promptCoordinator.onContinualModeToggle(enabled)
        }

        fun onMediaTypeChange(type: MediaCapability) {
            overrideCoordinator.clearForCapabilityChange()
            promptCoordinator.onMediaTypeChange(type)
            viewModelScope.launch {
                setSelectedStudioMediaType(type)
            }
        }

        fun onTemplateSelected(template: StudioTemplate) {
            promptCoordinator.onTemplateSelected(template)
        }

        fun onOpenModelPicker() {
            val modelType = currentResolvedModelType()
            overrideCoordinator.openPicker(
                modelType = modelType,
                current = overrideCoordinator.state.value.committedOverride,
            )
        }

        fun onStageModelSelection(option: StudioModelOptionUi) {
            overrideCoordinator.stageSelection(option.toDomainOverride())
        }

        fun onConfirmModelSelection() {
            overrideCoordinator.confirmSelection()
        }

        fun onCancelModelPicker() {
            overrideCoordinator.cancelPicker()
        }

        fun shareMedia(assetIds: Set<String>) {
            mediaActionsCoordinator.shareMedia(assetIds)
        }

        fun shareSingleMedia(assetId: String) {
            mediaActionsCoordinator.shareSingleMedia(assetId)
        }

        fun onEditMedia(
            assetId: String?,
            rehydrateIds: List<String> = emptyList(),
            rehydrateUris: List<String> = emptyList(),
            rehydrateTypes: List<String> = emptyList(),
            rehydratePrompts: List<String> = emptyList(),
            fromUserAction: Boolean = false,
        ) {
            mediaActionsCoordinator.onEditMedia(
                prompt = promptCoordinator,
                assetId = assetId,
                rehydrateIds = rehydrateIds,
                rehydrateUris = rehydrateUris,
                rehydrateTypes = rehydrateTypes,
                rehydratePrompts = rehydratePrompts,
                fromUserAction = fromUserAction,
            )
        }

        fun onAnimateMedia(
            assetId: String,
            autoAnimate: Boolean = false,
            customPrompt: String? = null,
        ) {
            mediaActionsCoordinator.onAnimateMedia(
                prompt = promptCoordinator,
                assetId = assetId,
                autoAnimate = autoAnimate,
                customPrompt = customPrompt,
            )
        }

        fun deleteMedia(id: String) {
            mediaActionsCoordinator.deleteMedia(id)
        }

        val uiState: StateFlow<StudioUiState> =
            combine(
                promptInputsFlow,
                generationInputsFlow,
                selectionInputsFlow,
                playbackInputsFlow,
                overrideInputsFlow,
            ) { promptInputs, generationInputs, selectionInputs, playbackInputs, overrideInputs ->
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
                        generationProgressLabel = generationInputs.generationProgressLabel,
                        lastSubmittedPrompt = generationInputs.lastSubmittedPrompt,
                        activeGenerationExpectedCount = generationInputs.activeGenerationExpectedCount,
                        activeGenerationBaselineMediaCount = generationInputs.activeGenerationBaselineMediaCount,
                        videoGenerationState = generationInputs.videoGenerationState,
                        speechState = playbackInputs.speechState,
                        isPlayingTts = playbackInputs.isPlayingTts,
                        referenceMediaType = promptInputs.referenceMediaType,
                        hasImageEditModel = playbackInputs.hasImageEditModel,
                        hasImageToVideoModel = playbackInputs.hasImageToVideoModel,
                        activeModelDisplayName = playbackInputs.activeModelDisplayName,
                        modelPickerAssets = overrideInputs.modelPickerAssets,
                        isModelPickerVisible = overrideInputs.isModelPickerVisible,
                        selectedModelIdInPicker = overrideInputs.selectedModelIdInPicker,
                        resolvedModelTypeLabel = overrideInputs.resolvedModelTypeLabel,
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
            overrideCoordinator.clearSession()
            promptCoordinator.clearSession(viewModelScope, albumSelectionCoordinator)
        }

        suspend fun clearSessionIfIdle() {
            if (!generationCoordinator.isGenerationActive()) {
                clearSession()
            }
        }

        fun clearError() {
            generationCoordinator.clearError(viewModelScope)
        }

        /**
         * Submits the moderated prompt as a false-positive report to Firestore so the moderation
         * thresholds can be tuned. Called when the user taps "GOT IT WRONG?" on the snackbar.
         */
        fun reportBlockedPrompt(prompt: String) {
            viewModelScope.launch {
                submitModerationReportUseCase(prompt, "blocked")
                _moderationEvents.send(ModerationEvent.ReportSubmitted)
            }
        }

        fun generate() {
            if (generationCoordinator.isGenerating.value || generationCoordinator.isContinualGenerationActive.value) {
                generationAttemptInFlight.set(false)
                generationCoordinator.stopGeneration()
                return
            }

            if (generationAttemptInFlight.get()) {
                generationAttemptInFlight.set(false)
                return
            }

            val submittedPrompt = promptCoordinator.prompt.value
            if (submittedPrompt.isBlank()) return

            if (!generationAttemptInFlight.compareAndSet(false, true)) {
                return
            }

            viewModelScope.launch {
                try {
                    val shouldModerate = shouldModeratePromptUseCase(ModelType.VISION)
                    if (shouldModerate) {
                        val moderationResult = moderatePromptUseCase(submittedPrompt)
                        if (moderationResult.blocked) {
                            generationCoordinator.rejectPromptAsModerated(submittedPrompt)
                            return@launch
                        }
                    }

                    val baselineMediaCountForPrompt =
                        uiState.value.groupedGallery
                            .find { group -> group.prompt == submittedPrompt }
                            ?.mediaItems
                            ?.size
                            ?: 0
                    generationCoordinator.beginGenerationAttempt(
                        submittedPrompt = submittedPrompt,
                        mediaType = promptCoordinator.mediaType.value,
                        continualMode = promptCoordinator.continualMode.value,
                        settings = promptCoordinator.settings.value,
                        baselineMediaCountForPrompt = baselineMediaCountForPrompt,
                        override = overrideCoordinator.state.value.committedOverride,
                    )
                    promptCoordinator.prompt.update { "" }
                } catch (e: Exception) {
                    android.util.Log.e("MultimodalViewModel", "Prompt moderation failed", e)
                    generationCoordinator.rejectPrompt("Failed to verify prompt safety. Please try again.")
                } finally {
                    generationAttemptInFlight.set(false)
                }
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
            overrideCoordinator.clearSession()
            speechCoordinator.onCleared()
            playbackCoordinator.onCleared()
        }

        private fun currentResolvedModelType(): ModelType =
            resolvedModelCoordinator.resolveModelType(
                promptCoordinator.mediaType.value,
                promptCoordinator.settings.value,
            )

        private companion object {
            private const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        }
    }
