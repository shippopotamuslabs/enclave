package com.browntowndev.pocketcrew.domain.port.media

import kotlinx.coroutines.flow.Flow

/**
 * Port for controlling TTS playback through Media3.
 *
 * This port abstracts the Media3 ExoPlayer/MediaSession lifecycle so that
 * feature:chat depends only on this pure Kotlin interface, not on
 * Media3 types or the app-level service.
 *
 * The app module provides the implementation that connects to TtsPlaybackService.
 */
interface TtsPlaybackControllerPort {
    /**
     * Requests TTS playback for the given [text].
     *
     * The implementation resolves the assigned TTS provider, registers the request
     * in an in-memory registry, connects to the Media3 service, sets a MediaItem
     * with metadata, and calls prepare/play on the player.
     *
     * @return A [Flow] of [TtsPlaybackStatus] representing the playback lifecycle.
     */
    fun play(text: String): Flow<TtsPlaybackStatus>

    /**
     * Stops any currently active TTS playback.
     *
     * Cancels the Media3 player, clears the media item, and removes the registry entry.
     */
    fun stop()
}