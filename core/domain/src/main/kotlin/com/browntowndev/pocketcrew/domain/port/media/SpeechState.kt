package com.browntowndev.pocketcrew.domain.port.media

sealed class SpeechState {
    data object Idle : SpeechState()
    data object ModelLoading : SpeechState()
    data class Listening(val volume: Float, val tick: Long = System.currentTimeMillis()) : SpeechState()
    data object Transcribing : SpeechState()
    data class FinalText(val text: String) : SpeechState()
    data class Error(val message: String) : SpeechState()
}
