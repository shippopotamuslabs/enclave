package com.browntowndev.pocketcrew.domain.port.media

/**
 * Configuration for streaming audio playback format.
 *
 * @property sampleRate The sample rate in Hz (e.g., 24000, 16000)
 * @property channels Number of audio channels (1 for mono, 2 for stereo)
 * @property bitsPerSample Bits per sample (typically 16 for PCM)
 */
data class StreamingAudioConfig(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
) {
    companion object {
        /** Standard configuration for TTS PCM 16-bit 24kHz mono audio. */
        val PCM_16_24KHZ_MONO = StreamingAudioConfig(
            sampleRate = 24_000,
            channels = 1,
            bitsPerSample = 16
        )

        /** Standard configuration for TTS PCM 16-bit 16kHz mono audio. */
        val PCM_16_16KHZ_MONO = StreamingAudioConfig(
            sampleRate = 16_000,
            channels = 1,
            bitsPerSample = 16
        )
    }
}

/**
 * Port for streaming audio playback.
 *
 * Unlike [AudioPlayerPort] which requires complete audio before playback,
 * this interface allows audio to be played incrementally as chunks arrive,
 * reducing time-to-first-audio latency.
 */
interface StreamingAudioPlayerPort {
    /**
     * Initializes the audio player with the specified format configuration.
     * Must be called before [enqueueChunk] or [startPlayback].
     *
     * @param config The audio format configuration.
     */
    suspend fun initialize(config: StreamingAudioConfig)

    /**
     * Enqueues a chunk of audio data for playback.
     *
     * The player will buffer a small amount of audio before starting playback
     * to ensure gap-free streaming. Chunks should be in the format specified
     * in [initialize] (typically PCM 16-bit little-endian).
     *
     * @param audioChunk The audio data chunk to play.
     */
    suspend fun enqueueChunk(audioChunk: ByteArray)

    /**
     * Starts playback of enqueued audio.
     * Called automatically after sufficient buffer is accumulated.
     */
    fun startPlayback()

    /**
     * Stops playback and releases resources.
     * After calling this, [initialize] must be called again before reuse.
     */
    fun stop()

    /**
     * Returns true if the player is currently initialized and ready for chunks.
     */
    fun isInitialized(): Boolean
}
