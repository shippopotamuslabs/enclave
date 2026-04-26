package com.browntowndev.pocketcrew.domain.model.inference

/**
 * Represents a chunk of audio data in a streaming TTS response.
 * This sealed class models the incremental delivery of audio from a streaming TTS service.
 */
sealed class TtsAudioChunk {
    /**
     * A chunk of audio data bytes (typically PCM 16-bit).
     */
    data class Data(val bytes: ByteArray) : TtsAudioChunk() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Data
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /**
     * Signals that the audio stream is complete.
     */
    data object Done : TtsAudioChunk()

    /**
     * Signals that an error occurred during streaming.
     */
    data class Error(val message: String, val cause: Throwable? = null) : TtsAudioChunk()
}
