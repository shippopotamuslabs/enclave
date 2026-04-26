package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

/**
 * Factory for creating [TtsServicePort] and [StreamingTtsServicePort] instances.
 */
interface TtsServiceFactoryPort {
    /**
     * Creates a [TtsServicePort] for the given [provider].
     *
     * @param provider The API provider.
     * @param apiKey The API key to use.
     * @param baseUrl Optional base URL override.
     * @return A [TtsServicePort] implementation.
     */
    fun create(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String? = null
    ): TtsServicePort

    /**
     * Creates a [StreamingTtsServicePort] for the given [provider] if streaming is supported.
     *
     * Streaming TTS provides lower latency by returning audio chunks as they are generated,
     * rather than waiting for the complete audio file.
     *
     * @param provider The API provider.
     * @param apiKey The API key to use.
     * @param baseUrl Optional base URL override.
     * @return A [StreamingTtsServicePort] implementation, or null if the provider does not support streaming.
     */
    fun createStreaming(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String? = null
    ): StreamingTtsServicePort?
}
