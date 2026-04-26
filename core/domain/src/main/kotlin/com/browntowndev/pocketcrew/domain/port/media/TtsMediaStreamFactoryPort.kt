package com.browntowndev.pocketcrew.domain.port.media

/**
 * Port for creating [TtsMediaStream] instances for a given provider.
 *
 * Each provider adapter (OpenAI, xAI, Google) implements [TtsMediaStreamServicePort]
 * to provide audio data as a streaming [InputStream] suitable for ExoPlayer consumption.
 *
 * The app-level DataSource resolves a URI → requestId → TtsPlaybackRequest, then uses
 * this factory to obtain the correct [TtsMediaStreamServicePort] and open the stream.
 */
interface TtsMediaStreamFactoryPort {
    /**
     * Creates a [TtsMediaStreamServicePort] for the given provider and credentials.
     *
     * @param provider The API provider.
     * @param apiKey The API key to use.
     * @param baseUrl Optional base URL override.
     * @return A [TtsMediaStreamServicePort] that can produce audio streams,
     *   or null if the provider does not support Media3 streaming (should not happen for known providers).
     */
    fun create(
        provider: com.browntowndev.pocketcrew.domain.model.inference.ApiProvider,
        apiKey: String,
        baseUrl: String? = null,
    ): TtsMediaStreamServicePort?
}

/**
 * Port for opening a TTS audio stream from a specific provider.
 *
 * Unlike [com.browntowndev.pocketcrew.domain.port.inference.StreamingTtsServicePort]
 * which emits PCM chunks via a Flow, this port returns a [TtsMediaStream] with an
 * [InputStream] suitable for ExoPlayer's DataSource interface.
 *
 * The InputStream must produce encoded audio (MP3 for OpenAI/xAI, WAV for Google)
 * so ExoPlayer can decode it natively.
 */
interface TtsMediaStreamServicePort {
    /**
     * Opens a media stream for the given TTS request parameters.
     *
     * @param text The text to synthesize.
     * @param voice The voice identifier.
     * @param modelId Optional model identifier.
     * @return A [TtsMediaStream] containing the audio data.
     */
    suspend fun openStream(text: String, voice: String, modelId: String? = null): TtsMediaStream
}