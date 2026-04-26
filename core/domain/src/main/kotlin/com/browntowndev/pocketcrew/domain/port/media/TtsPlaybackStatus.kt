package com.browntowndev.pocketcrew.domain.port.media

/**
 * Sealed class representing the status of a TTS playback session driven by Media3.
 *
 * This mirrors [com.browntowndev.pocketcrew.domain.usecase.chat.StreamingPlaybackStatus]
 * but is the domain-level abstraction that the TtsPlaybackControllerPort emits,
 * decoupling feature:chat from the specific playback engine.
 */
sealed class TtsPlaybackStatus {
    /** Initializing the media session and resolving the provider. */
    data object Initializing : TtsPlaybackStatus()

    /** Audio is actively playing through Media3. */
    data object Playing : TtsPlaybackStatus()

    /** Playback completed successfully. */
    data object Completed : TtsPlaybackStatus()

    /** An error occurred during playback. */
    data class Error(val message: String, val cause: Throwable? = null) : TtsPlaybackStatus()

    /** Playback was explicitly stopped by the user. */
    data object Stopped : TtsPlaybackStatus()
}