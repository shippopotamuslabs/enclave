package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class TtsController
    @Inject
    constructor(
        private val playbackController: TtsPlaybackControllerPort,
        private val loggingPort: LoggingPort,
        private val errorHandler: ViewModelErrorHandler,
    ) {
        private val _isPlayingTts = MutableStateFlow(false)
        val isPlayingTts: StateFlow<Boolean> = _isPlayingTts.asStateFlow()

        private var streamingTtsJob: Job? = null

        fun onPlayTts(
            text: String,
            scope: CoroutineScope,
            hasTtsProviderAssigned: Boolean,
        ) {
            if (!hasTtsProviderAssigned) return

            streamingTtsJob?.cancel()
            playbackController.stop()

            streamingTtsJob =
                scope.launch(
                    errorHandler.coroutineExceptionHandler(TAG, "Failed to play TTS audio", "Failed to play audio"),
                ) {
                    playbackController
                        .play(text)
                        .collect { status ->
                            when (status) {
                                is TtsPlaybackStatus.Initializing -> {
                                    loggingPort.debug(TAG, "TTS initializing")
                                }
                                is TtsPlaybackStatus.Playing -> {
                                    _isPlayingTts.value = true
                                    loggingPort.debug(TAG, "TTS playing")
                                }
                                is TtsPlaybackStatus.Completed -> {
                                    _isPlayingTts.value = false
                                    streamingTtsJob = null
                                    loggingPort.debug(TAG, "TTS completed")
                                }
                                is TtsPlaybackStatus.Error -> {
                                    _isPlayingTts.value = false
                                    streamingTtsJob = null
                                    loggingPort.error(TAG, "TTS failed: ${status.message}", status.cause)
                                }
                                is TtsPlaybackStatus.Stopped -> {
                                    _isPlayingTts.value = false
                                    streamingTtsJob = null
                                    loggingPort.debug(TAG, "TTS stopped")
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

        private companion object {
            private const val TAG = "TtsController"
        }
    }
