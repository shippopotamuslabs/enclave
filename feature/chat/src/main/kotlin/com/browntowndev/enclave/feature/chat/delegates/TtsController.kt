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

sealed interface TtsPlaybackUiState {
    data object Idle : TtsPlaybackUiState

    data object Loading : TtsPlaybackUiState

    data class Playing(
        val text: String,
    ) : TtsPlaybackUiState

    data class Paused(
        val text: String,
    ) : TtsPlaybackUiState
}

class TtsController
    @Inject
    constructor(
        private val playbackController: TtsPlaybackControllerPort,
        private val loggingPort: LoggingPort,
        private val errorHandler: ViewModelErrorHandler,
    ) {
        private val _playbackState = MutableStateFlow<TtsPlaybackUiState>(TtsPlaybackUiState.Idle)
        val playbackState: StateFlow<TtsPlaybackUiState> = _playbackState.asStateFlow()

        private val _isPlayingTts = MutableStateFlow(false)
        val isPlayingTts: StateFlow<Boolean> = _isPlayingTts.asStateFlow()

        private val _isLoadingTts = MutableStateFlow(false)
        val isLoadingTts: StateFlow<Boolean> = _isLoadingTts.asStateFlow()

        private val _isPausedTts = MutableStateFlow(false)
        val isPausedTts: StateFlow<Boolean> = _isPausedTts.asStateFlow()

        private var streamingTtsJob: Job? = null

        fun onPlayTts(
            text: String,
            scope: CoroutineScope,
            hasTtsProviderAssigned: Boolean,
        ) {
            if (!hasTtsProviderAssigned) return

            val currentState = _playbackState.value
            if (currentState is TtsPlaybackUiState.Paused && currentState.text == text) {
                playbackController.resume()
                return
            }

            setPlaybackState(TtsPlaybackUiState.Loading)

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
                                    setPlaybackState(TtsPlaybackUiState.Loading)
                                    loggingPort.debug(TAG, "TTS initializing")
                                }
                                is TtsPlaybackStatus.Playing -> {
                                    setPlaybackState(TtsPlaybackUiState.Playing(text))
                                    loggingPort.debug(TAG, "TTS playing")
                                }
                                is TtsPlaybackStatus.Paused -> {
                                    setPlaybackState(TtsPlaybackUiState.Paused(text))
                                    loggingPort.debug(TAG, "TTS paused")
                                }
                                is TtsPlaybackStatus.Completed -> {
                                    setPlaybackState(TtsPlaybackUiState.Idle)
                                    streamingTtsJob = null
                                    loggingPort.debug(TAG, "TTS completed")
                                }
                                is TtsPlaybackStatus.Error -> {
                                    setPlaybackState(TtsPlaybackUiState.Idle)
                                    streamingTtsJob = null
                                    loggingPort.error(TAG, "TTS failed: ${status.message}", status.cause)
                                    errorHandler.handleError(
                                        tag = TAG,
                                        message = "TTS playback failed: ${status.message}",
                                        throwable = status.cause ?: RuntimeException(status.message),
                                        userMessage = "Audio playback failed: ${status.message}",
                                    )
                                }
                                is TtsPlaybackStatus.Stopped -> {
                                    setPlaybackState(TtsPlaybackUiState.Idle)
                                    streamingTtsJob = null
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
            setPlaybackState(TtsPlaybackUiState.Idle)
        }

        private fun setPlaybackState(state: TtsPlaybackUiState) {
            _playbackState.value = state
            _isPlayingTts.value = state is TtsPlaybackUiState.Playing
            _isLoadingTts.value = state is TtsPlaybackUiState.Loading
            _isPausedTts.value = state is TtsPlaybackUiState.Paused
        }

        private companion object {
            private const val TAG = "TtsController"
        }
    }
