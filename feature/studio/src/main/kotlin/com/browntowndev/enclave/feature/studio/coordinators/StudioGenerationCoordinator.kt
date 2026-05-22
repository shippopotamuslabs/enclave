package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.port.media.StudioGenerationDispatcherPort
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.feature.studio.VideoGenerationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class StudioGenerationCoordinator
    @Inject
    constructor(
        private val studioRepository: StudioRepositoryPort,
        private val dispatcher: StudioGenerationDispatcherPort,
    ) {
        private val _isGenerating = MutableStateFlow(false)
        val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

        private val _isContinualGenerationActive = MutableStateFlow(false)
        val isContinualGenerationActive: StateFlow<Boolean> = _isContinualGenerationActive.asStateFlow()

        private val _lastSubmittedPrompt = MutableStateFlow<String?>(null)
        val lastSubmittedPrompt: StateFlow<String?> = _lastSubmittedPrompt.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val _videoGenerationState = MutableStateFlow<VideoGenerationState>(VideoGenerationState.Idle)
        val videoGenerationState: StateFlow<VideoGenerationState> = _videoGenerationState.asStateFlow()

        private val consumedScrollAnchors = mutableSetOf<String>()

        var lastProcessedEditAssetId: String? = null
        var lastProcessedAnimateAssetId: String? = null

        fun startObservers(scope: CoroutineScope) {
            studioRepository
                .observeEphemeralMedia()
                .onEach {
                    _isGenerating.value = false
                }.launchIn(scope)

            studioRepository
                .observeGenerationError()
                .onEach { generationError ->
                    if (generationError != null) {
                        _error.value = generationError
                        _isGenerating.value = false
                        _isContinualGenerationActive.value = false
                    }
                }.launchIn(scope)
        }

        fun stopGeneration() {
            _isContinualGenerationActive.value = false
            _isGenerating.value = false
            dispatcher.stopGeneration()
        }

        fun startGeneration(
            prompt: String,
            mediaType: MediaCapability,
        ) {
            dispatcher.startGeneration(prompt, mediaType)
            _isGenerating.value = true
        }

        fun startDetailVideoGeneration(
            asset: StudioMediaAsset,
            customPrompt: String? = null,
        ) {
            val prompt = customPrompt ?: asset.prompt ?: ""
            startGeneration(prompt, MediaCapability.VIDEO)
        }

        fun beginGenerationAttempt(
            submittedPrompt: String,
            mediaType: MediaCapability,
            continualMode: Boolean,
        ) {
            lastProcessedEditAssetId = null
            lastProcessedAnimateAssetId = null
            _lastSubmittedPrompt.value = submittedPrompt
            if (continualMode && mediaType == MediaCapability.IMAGE) {
                _isContinualGenerationActive.value = true
            }
            startGeneration(submittedPrompt, mediaType)
        }

        fun onContinualModeDisabled() {
            stopGeneration()
            clearScrollAnchors()
        }

        fun clearScrollAnchors() {
            consumedScrollAnchors.clear()
        }

        fun resetVideoGenerationState() {
            _videoGenerationState.value = VideoGenerationState.Idle
        }

        fun onGenerativeScrollThresholdVisible(
            anchorAssetId: String,
            mediaType: MediaCapability,
        ) {
            val cannotContinueGeneration =
                mediaType != MediaCapability.IMAGE ||
                    !_isContinualGenerationActive.value ||
                    _lastSubmittedPrompt.value.isNullOrBlank() ||
                    _isGenerating.value
            if (cannotContinueGeneration || !consumedScrollAnchors.add(anchorAssetId)) {
                return
            }
            startGeneration(requireNotNull(_lastSubmittedPrompt.value), mediaType)
        }

        fun clearError(scope: CoroutineScope) {
            _error.value = null
            scope.launch {
                studioRepository.clearGenerationError()
            }
        }

        fun clearLocalError() {
            _error.value = null
        }

        fun resetGeneratingFlags() {
            _isGenerating.value = false
        }

        fun clearLastSubmittedPrompt() {
            _lastSubmittedPrompt.value = null
        }

        fun resetProcessedAssetIds() {
            lastProcessedEditAssetId = null
            lastProcessedAnimateAssetId = null
        }

        fun clearEditAssetId(id: String) {
            if (id == lastProcessedEditAssetId) {
                lastProcessedEditAssetId = null
            }
        }

        fun clearAnimateAssetId(id: String) {
            if (id == lastProcessedAnimateAssetId) {
                lastProcessedAnimateAssetId = null
            }
        }
    }
