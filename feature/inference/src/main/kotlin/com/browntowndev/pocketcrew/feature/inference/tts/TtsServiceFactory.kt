package com.browntowndev.pocketcrew.feature.inference.tts

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.StreamingTtsServicePort
import com.browntowndev.pocketcrew.domain.port.inference.TtsServiceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.TtsServicePort
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiClientProviderPort
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsServiceFactory @Inject constructor(
    private val httpClient: OkHttpClient,
    private val openAiClientProvider: OpenAiClientProviderPort,
    private val googleClientProvider: GoogleGenAiClientProviderPort,
    private val logger: LoggingPort,
) : TtsServiceFactoryPort {
    override fun create(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String?
    ): TtsServicePort = when (provider) {
        ApiProvider.OPENAI -> OpenAiTtsService(
            clientProvider = openAiClientProvider,
            apiKey = apiKey,
            baseUrl = baseUrl
        )
        ApiProvider.XAI -> XAiTtsService(
            httpClient = httpClient,
            apiKey = apiKey
        )
        ApiProvider.GOOGLE -> GoogleTtsService(
            modelsApiProvider = {
                // Gemini TTS models require the v1beta API version.
                googleClientProvider.getClient(apiKey, baseUrl, apiVersion = "v1beta").models
            },
        )
        else -> throw IllegalArgumentException("TTS not supported for provider: $provider")
    }

    override fun createStreaming(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String?
    ): StreamingTtsServicePort? = when (provider) {
        ApiProvider.OPENAI -> OpenAiStreamingTtsService(
            clientProvider = openAiClientProvider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            logger = logger,
        )
        ApiProvider.XAI -> XAiStreamingTtsService(
            httpClient = httpClient,
            apiKey = apiKey,
            logger = logger,
        )
        // Google TTS via Gemini GenAI SDK does not support streaming audio generation
        // with the current API surface. Falls back to batch mode.
        else -> null
    }
}