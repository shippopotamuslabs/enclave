package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

/**
 * Factory for creating [TtsServicePort] instances.
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
}
