package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
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

        private val _lastSubmittedSettings = MutableStateFlow<GenerationSettings?>(null)

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val _videoGenerationState = MutableStateFlow<VideoGenerationState>(VideoGenerationState.Idle)
        val videoGenerationState: StateFlow<VideoGenerationState> = _videoGenerationState.asStateFlow()

        private val consumedScrollAnchors = mutableSetOf<String>()

        var lastProcessedEditAssetId: String? = null
        var lastProcessedAnimateAssetId: String? = null

        private var pendingDetailVideoSourceAssetId: String? = null
        private var pendingDetailVideoSourceCreatedAt: Long? = null

        fun startObservers(scope: CoroutineScope) {
            studioRepository
                .observeEphemeralMedia()
                .onEach { media ->
                    _isGenerating.value = false
                    resolveDetailVideoGeneration(media)
                }.launchIn(scope)

            studioRepository
                .observeGenerationError()
                .onEach { generationError ->
                    if (generationError != null) {
                        _error.value = generationError
                        _isGenerating.value = false
                        _isContinualGenerationActive.value = false
                        pendingDetailVideoSourceAssetId?.let { sourceAssetId ->
                            _videoGenerationState.value =
                                VideoGenerationState.Error(
                                    sourceAssetId = sourceAssetId,
                                    message = generationError,
                                )
                            clearPendingDetailVideoGeneration()
                        }
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
            settings: GenerationSettings,
        ) {
            dispatcher.startGeneration(prompt, mediaType, settings)
            _isGenerating.value = true
        }

        fun startDetailVideoGeneration(
            asset: StudioMediaAsset,
            customPrompt: String? = null,
        ) {
            val prompt = customPrompt ?: asset.prompt
            pendingDetailVideoSourceAssetId = asset.id
            pendingDetailVideoSourceCreatedAt = asset.createdAt
            _videoGenerationState.value = VideoGenerationState.Loading(asset.id)
            startGeneration(
                prompt = prompt,
                mediaType = MediaCapability.VIDEO,
                settings =
                    VideoGenerationSettings(
                        aspectRatio = AspectRatio.ONE_ONE,
                        quality = GenerationQuality.SPEED,
                        videoDuration = DEFAULT_DETAIL_VIDEO_DURATION_SECONDS,
                        videoResolution = DEFAULT_DETAIL_VIDEO_RESOLUTION,
                        referenceImageUri = asset.localUri,
                    ),
            )
        }

        fun beginGenerationAttempt(
            submittedPrompt: String,
            mediaType: MediaCapability,
            continualMode: Boolean,
            settings: GenerationSettings,
        ) {
            lastProcessedEditAssetId = null
            lastProcessedAnimateAssetId = null
            _lastSubmittedPrompt.value = submittedPrompt
            _lastSubmittedSettings.value = settings
            if (continualMode && mediaType == MediaCapability.IMAGE) {
                _isContinualGenerationActive.value = true
            }
            startGeneration(submittedPrompt, mediaType, settings)
        }

        fun onContinualModeDisabled() {
            stopGeneration()
            clearScrollAnchors()
        }

        fun clearScrollAnchors() {
            consumedScrollAnchors.clear()
        }

        fun resetVideoGenerationState() {
            clearPendingDetailVideoGeneration()
            _videoGenerationState.value = VideoGenerationState.Idle
        }

        private fun resolveDetailVideoGeneration(media: List<StudioMediaAsset>) {
            val sourceAssetId = pendingDetailVideoSourceAssetId ?: return
            val sourceCreatedAt = pendingDetailVideoSourceCreatedAt ?: return
            val generatedVideo =
                media
                    .filter { asset ->
                        asset.mediaType == MediaCapability.VIDEO.name &&
                            asset.createdAt > sourceCreatedAt
                    }.maxByOrNull { it.createdAt }
                    ?: return

            _videoGenerationState.value =
                VideoGenerationState.Success(
                    sourceAssetId = sourceAssetId,
                    localUri = generatedVideo.localUri,
                )
            clearPendingDetailVideoGeneration()
        }

        private fun clearPendingDetailVideoGeneration() {
            pendingDetailVideoSourceAssetId = null
            pendingDetailVideoSourceCreatedAt = null
        }

        fun onGenerativeScrollThresholdVisible(
            anchorAssetId: String,
            mediaType: MediaCapability,
        ) {
            val cannotContinueGeneration =
                mediaType != MediaCapability.IMAGE ||
                    !_isContinualGenerationActive.value ||
                    _lastSubmittedPrompt.value.isNullOrBlank() ||
                    _lastSubmittedSettings.value == null ||
                    _isGenerating.value
            if (cannotContinueGeneration || !consumedScrollAnchors.add(anchorAssetId)) {
                return
            }
            startGeneration(
                requireNotNull(_lastSubmittedPrompt.value),
                mediaType,
                requireNotNull(_lastSubmittedSettings.value),
            )
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

        private companion object {
            private const val DEFAULT_DETAIL_VIDEO_DURATION_SECONDS = 5
            private const val DEFAULT_DETAIL_VIDEO_RESOLUTION = "480p"
        }
    }
