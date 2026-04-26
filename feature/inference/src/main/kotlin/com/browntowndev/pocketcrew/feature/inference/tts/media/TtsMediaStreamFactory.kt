package com.browntowndev.pocketcrew.feature.inference.tts.media

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.media.TtsMediaStreamFactoryPort
import com.browntowndev.pocketcrew.domain.port.media.TtsMediaStreamServicePort
import com.browntowndev.pocketcrew.feature.inference.AndroidLoggingAdapter
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiClientProviderPort
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory that creates [TtsMediaStreamServicePort] instances for each TTS provider.
 *
 * Each adapter opens an audio stream suitable for ExoPlayer consumption:
 * - OpenAI: MP3 audio stream from the /v1/audio/speech endpoint
 * - xAI: MP3 audio stream via WebSocket TTS with codec=mp3
 * - Google: WAV bytes from the batch synthesizeSpeech API (PCM wrapped in WAV header)
 */
@Singleton
class TtsMediaStreamFactory @Inject constructor(
    private val httpClient: OkHttpClient,
    private val openAiClientProvider: OpenAiClientProviderPort,
    private val googleClientProvider: GoogleGenAiClientProviderPort,
    private val logger: AndroidLoggingAdapter,
) : TtsMediaStreamFactoryPort {

    override fun create(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String?,
    ): TtsMediaStreamServicePort? = when (provider) {
        ApiProvider.OPENAI -> OpenAiMediaStreamService(
            clientProvider = openAiClientProvider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            logger = logger,
        )
        ApiProvider.XAI -> XAiMediaStreamService(
            httpClient = httpClient,
            apiKey = apiKey,
            logger = logger,
        )
        ApiProvider.GOOGLE -> GoogleMediaStreamService(
            modelsApiProvider = {
                googleClientProvider.getClient(apiKey, baseUrl, apiVersion = "v1beta").models
            },
            logger = logger,
        )
        else -> null
    }
}