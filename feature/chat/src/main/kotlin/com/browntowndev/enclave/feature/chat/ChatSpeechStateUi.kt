package com.browntowndev.enclave.feature.chat

import androidx.compose.runtime.Immutable
import com.browntowndev.enclave.domain.port.media.SpeechState

/** Presentation mirror of domain [SpeechState] for chat composables. */
@Immutable
sealed class ChatSpeechStateUi {
    data object Idle : ChatSpeechStateUi()

    data object ModelLoading : ChatSpeechStateUi()

    data class Listening(
        val volume: Float,
        val tick: Long,
    ) : ChatSpeechStateUi()

    data object Transcribing : ChatSpeechStateUi()

    data class FinalText(
        val text: String,
    ) : ChatSpeechStateUi()

    data class Error(
        val message: String,
    ) : ChatSpeechStateUi()
}

/** True while microphone prep, capture, or transcription is in progress. */
val ChatSpeechStateUi.isActiveVoiceInput: Boolean
    get() =
        this is ChatSpeechStateUi.Listening ||
            this is ChatSpeechStateUi.ModelLoading ||
            this is ChatSpeechStateUi.Transcribing

fun SpeechState.toChatSpeechStateUi(): ChatSpeechStateUi =
    when (this) {
        SpeechState.Idle -> ChatSpeechStateUi.Idle
        SpeechState.ModelLoading -> ChatSpeechStateUi.ModelLoading
        is SpeechState.Listening -> ChatSpeechStateUi.Listening(volume = volume, tick = tick)
        SpeechState.Transcribing -> ChatSpeechStateUi.Transcribing
        is SpeechState.FinalText -> ChatSpeechStateUi.FinalText(text = text)
        is SpeechState.Error -> ChatSpeechStateUi.Error(message = message)
    }

fun ChatSpeechStateUi.toSpeechState(): SpeechState =
    when (this) {
        ChatSpeechStateUi.Idle -> SpeechState.Idle
        ChatSpeechStateUi.ModelLoading -> SpeechState.ModelLoading
        is ChatSpeechStateUi.Listening -> SpeechState.Listening(volume = volume, tick = tick)
        ChatSpeechStateUi.Transcribing -> SpeechState.Transcribing
        is ChatSpeechStateUi.FinalText -> SpeechState.FinalText(text = text)
        is ChatSpeechStateUi.Error -> SpeechState.Error(message = message)
    }
