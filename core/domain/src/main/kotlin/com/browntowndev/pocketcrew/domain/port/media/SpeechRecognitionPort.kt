package com.browntowndev.pocketcrew.domain.port.media

import kotlinx.coroutines.flow.StateFlow

sealed class SpeechState {
    data object Idle : SpeechState()
    data object Listening : SpeechState()
    data class PartialText(val text: String) : SpeechState()
    data class FinalText(val text: String) : SpeechState()
    data class Error(val message: String) : SpeechState()
}

interface SpeechRecognitionPort {
    val speechState: StateFlow<SpeechState>
    fun startListening(initialText: String = "")
    fun stopListening()
}
