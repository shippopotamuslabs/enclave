package com.browntowndev.enclave.domain.port.media

/** True while microphone prep, capture, or transcription is in progress. */
val SpeechState.isActiveVoiceInput: Boolean
    get() =
        this is SpeechState.Listening ||
            this is SpeechState.ModelLoading ||
            this is SpeechState.Transcribing
