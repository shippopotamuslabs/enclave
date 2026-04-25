package com.browntowndev.pocketcrew.domain.port.media

/**
 * Port for playing raw audio bytes.
 */
interface AudioPlayerPort {
    /**
     * Plays the given [audioBytes].
     *
     * @param audioBytes The raw audio bytes (typically MP3) to play.
     */
    suspend fun playAudio(audioBytes: ByteArray)

    /**
     * Stops any currently playing audio.
     */
    fun stop()
}
