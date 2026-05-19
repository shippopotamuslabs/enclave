package com.browntowndev.pocketcrew.feature.studio.coordinators

import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class StudioPlaybackCoordinator
    @Inject
    constructor(
        private val playbackController: TtsPlaybackControllerPort,
    ) {
        private val _isPlayingTts = MutableStateFlow(false)
        val isPlayingTts: StateFlow<Boolean> = _isPlayingTts.asStateFlow()

        private var streamingTtsJob: Job? = null

        fun onPlayTts(
            text: String,
            scope: CoroutineScope,
            hasTtsProviderAssigned: suspend () -> Boolean,
        ) {
            scope.launch {
                if (!hasTtsProviderAssigned()) return@launch

                streamingTtsJob?.cancel()
                streamingTtsJob = null
                playbackController.stop()

                streamingTtsJob =
                    scope.launch {
                        playbackController
                            .play(text)
                            .collect { status ->
                                when (status) {
                                    is TtsPlaybackStatus.Playing -> {
                                        _isPlayingTts.value = true
                                    }
                                    is TtsPlaybackStatus.Completed,
                                    is TtsPlaybackStatus.Error,
                                    is TtsPlaybackStatus.Stopped,
                                    -> {
                                        _isPlayingTts.value = false
                                        streamingTtsJob = null
                                    }
                                    else -> Unit
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

        fun onCleared() {
            streamingTtsJob?.cancel()
            playbackController.stop()
        }
    }
