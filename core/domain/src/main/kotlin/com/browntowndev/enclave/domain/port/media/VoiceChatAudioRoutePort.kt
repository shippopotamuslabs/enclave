package com.browntowndev.enclave.domain.port.media

/**
 * Controls audio routing for full-duplex voice chat (Live Voice).
 *
 * Live Voice capture/playback uses the voice-communication audio path so hardware echo
 * cancellation can correlate mic input with assistant audio. That route defaults to the
 * earpiece on phones; this port forces the loudspeaker path while a session is active.
 */
interface VoiceChatAudioRoutePort {
    /** Routes voice-chat audio through the loudspeaker and prepares communication mode. */
    fun activateSpeakerphone()

    /** Restores normal audio routing after a voice-chat session ends. */
    fun deactivateSpeakerphone()
}
