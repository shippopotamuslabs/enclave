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

        private val _isLoadingTts = MutableStateFlow(false)
        val isLoadingTts: StateFlow<Boolean> = _isLoadingTts.asStateFlow()

        private val _isPausedTts = MutableStateFlow(false)
        val isPausedTts: StateFlow<Boolean> = _isPausedTts.asStateFlow()

        private var streamingTtsJob: Job? = null
        private var currentText: String? = null

        fun onPlayTts(
            text: String,
            scope: CoroutineScope,
            hasTtsProviderAssigned: Boolean,
        ) {
            if (!hasTtsProviderAssigned) return

            if (_isPausedTts.value && currentText == text) {
                playbackController.resume()
                return
            }

            _isPlayingTts.value = false
            _isPausedTts.value = false
            _isLoadingTts.value = true
            currentText = text

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
                                    _isLoadingTts.value = true
                                    loggingPort.debug(TAG, "TTS initializing")
                                }
                                is TtsPlaybackStatus.Playing -> {
                                    _isLoadingTts.value = false
                                    _isPlayingTts.value = true
                                    _isPausedTts.value = false
                                    loggingPort.debug(TAG, "TTS playing")
                                }
                                is TtsPlaybackStatus.Paused -> {
                                    _isLoadingTts.value = false
                                    _isPlayingTts.value = false
                                    _isPausedTts.value = true
                                    loggingPort.debug(TAG, "TTS paused")
                                }
                                is TtsPlaybackStatus.Completed -> {
                                    _isLoadingTts.value = false
                                    _isPlayingTts.value = false
                                    _isPausedTts.value = false
                                    streamingTtsJob = null
                                    currentText = null
                                    loggingPort.debug(TAG, "TTS completed")
                                }
                                is TtsPlaybackStatus.Error -> {
                                    _isLoadingTts.value = false
                                    _isPlayingTts.value = false
                                    _isPausedTts.value = false
                                    streamingTtsJob = null
                                    currentText = null
                                    loggingPort.error(TAG, "TTS failed: ${status.message}", status.cause)
                                    errorHandler.handleError(
                                        tag = TAG,
                                        message = "TTS playback failed: ${status.message}",
                                        throwable = status.cause ?: RuntimeException(status.message),
                                        userMessage = "Audio playback failed: ${status.message}",
                                    )
                                }
                                is TtsPlaybackStatus.Stopped -> {
                                    _isLoadingTts.value = false
                                    _isPlayingTts.value = false
                                    _isPausedTts.value = false
                                    streamingTtsJob = null
                                    currentText = null
                                    loggingPort.debug(TAG, "TTS stopped")
                                }
                            }
                        }
                }
        }

        fun onPauseTts() {
            playbackController.pause()
        }

        fun onStopTts() {
            streamingTtsJob?.cancel()
            streamingTtsJob = null
            playbackController.stop()
            _isPlayingTts.value = false
            _isPausedTts.value = false
            _isLoadingTts.value = false
            currentText = null
        }

        private companion object {
            private const val TAG = "TtsController"
        }
    }
