package com.browntowndev.pocketcrew.domain.port.media

sealed class SpeechState {
    data object Idle : SpeechState()
    data object ModelLoading : SpeechState()
    data object Listening : SpeechState()
    data class PartialText(val text: String) : SpeechState()
    data class FinalText(val text: String) : SpeechState()
    data class Error(val message: String) : SpeechState()
}
