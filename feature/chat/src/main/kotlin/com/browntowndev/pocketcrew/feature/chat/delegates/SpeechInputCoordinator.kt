package com.browntowndev.pocketcrew.feature.chat.delegates

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class SpeechInputCoordinator
    @Inject
    constructor(
        private val chatUseCases: ChatUseCases,
        private val errorHandler: ViewModelErrorHandler,
        private val loggingPort: LoggingPort,
    ) {
        private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
        val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

        private val _stopSpeechSignal = MutableStateFlow(false)
        private var speechJob: Job? = null

        fun observeResults(
            scope: CoroutineScope,
            onFinalText: (String) -> Unit,
        ) {
            _speechState
                .onEach { state ->
                    when (state) {
                        is SpeechState.FinalText -> onFinalText(state.text)
                        is SpeechState.Error -> {
                            loggingPort.error(TAG, "Speech recognition error: ${state.message}")
                            errorHandler.handleError(
                                tag = TAG,
                                message = "Speech Recognition Failed",
                                throwable = Exception(state.message),
                                userMessage = state.message,
                            )
                        }
                        else -> Unit
                    }
                }.launchIn(scope)
        }

        fun onMicClick(
            scope: CoroutineScope,
            currentInput: String,
        ) {
            val currentState = _speechState.value
            if (currentState is SpeechState.ModelLoading || currentState is SpeechState.Listening) {
                _stopSpeechSignal.value = true
                return
            }
            startListening(scope, currentInput)
        }

        fun startListening(
            scope: CoroutineScope,
            currentInput: String,
        ) {
            _stopSpeechSignal.value = false
            speechJob?.cancel()
            speechJob =
                chatUseCases
                    .listenToSpeechUseCase(currentInput, _stopSpeechSignal.asStateFlow())
                    .onEach { state -> _speechState.value = state }
                    .catch { cause ->
                        _speechState.value = SpeechState.Error(cause.message ?: "Speech transcription failed.")
                    }.launchIn(scope)
        }

        fun stopListening() {
            speechJob?.cancel()
            speechJob = null
            _speechState.value = SpeechState.Idle
        }

        private companion object {
            private const val TAG = "SpeechInputCoordinator"
        }
    }
