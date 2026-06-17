package com.browntowndev.enclave.domain.port.media

import kotlinx.coroutines.flow.Flow

data class RealtimeCaptureSessionInfo(
    val audioSessionId: Int,
    val isEchoCancellationActive: Boolean,
)

sealed interface RealtimeCaptureEvent {
    data class SessionStarted(
        val info: RealtimeCaptureSessionInfo,
    ) : RealtimeCaptureEvent

    data class Pcm16Frame(
        val bytes: ByteArray,
    ) : RealtimeCaptureEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Pcm16Frame) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

interface RealtimeAudioCapturePort {
    /** Emits session metadata first, then PCM16 little-endian mono frames at [sampleRate] Hz. */
    fun captureEvents(sampleRate: Int): Flow<RealtimeCaptureEvent>
}
