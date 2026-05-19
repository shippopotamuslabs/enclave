package com.browntowndev.pocketcrew.feature.studio.coordinators

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

class StudioSpeechCoordinator
    @Inject
    constructor(
        private val chatUseCases: ChatUseCases,
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
                    if (state is SpeechState.FinalText) {
                        onFinalText(state.text)
                    }
                }.launchIn(scope)
        }

        fun onMicClick(
            scope: CoroutineScope,
            currentPrompt: String,
        ) {
            val currentState = _speechState.value
            if (currentState is SpeechState.ModelLoading || currentState is SpeechState.Listening) {
                stopListening()
                return
            }
            startListening(scope, currentPrompt)
        }

        fun stopListening() {
            _stopSpeechSignal.value = true
            speechJob?.cancel()
            speechJob = null
            _speechState.value = SpeechState.Idle
        }

        private fun startListening(
            scope: CoroutineScope,
            currentPrompt: String,
        ) {
            _stopSpeechSignal.value = false
            speechJob?.cancel()
            speechJob =
                chatUseCases
                    .listenToSpeechUseCase(currentPrompt, _stopSpeechSignal.asStateFlow())
                    .onEach { state -> _speechState.value = state }
                    .catch { cause ->
                        _speechState.value = SpeechState.Error(cause.message ?: "Speech transcription failed.")
                    }.launchIn(scope)
        }

        fun onCleared() {
            speechJob?.cancel()
        }
    }
