package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.port.media.SpeechRecognitionPort
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class TranscribeSpeechUseCase @Inject constructor(
    private val speechRecognitionPort: SpeechRecognitionPort
) {
    val speechState: StateFlow<SpeechState> = speechRecognitionPort.speechState

    fun startListening(initialText: String = "") {
        speechRecognitionPort.startListening(initialText)
    }

    fun stopListening() {
        speechRecognitionPort.stopListening()
    }
}
