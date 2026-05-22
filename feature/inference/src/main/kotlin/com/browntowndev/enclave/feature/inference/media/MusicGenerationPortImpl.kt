package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.port.media.MusicGenerationPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import javax.inject.Inject

class MusicGenerationPortImpl
    @Inject
    constructor(
        private val apiKeyProvider: ApiKeyProviderPort,
        private val googleAdapter: GoogleMusicGenerationAdapter,
    ) : MusicGenerationPort {
        override suspend fun generateMusic(
            prompt: String,
            provider: MediaProviderAsset,
            settings: GenerationSettings,
        ): Result<ByteArray> {
            val apiKey =
                apiKeyProvider.getApiKey(provider.credentialAlias)
                    ?: return Result.failure(IllegalStateException("API key not found for alias: ${provider.credentialAlias}"))

            return when (provider.provider) {
                ApiProvider.GOOGLE ->
                    googleAdapter.generateMusic(
                        prompt,
                        apiKey,
                        provider.modelName ?: "lyria-3-clip-preview",
                        provider.baseUrl,
                    )
                else -> Result.failure(UnsupportedOperationException("Music generation not supported for provider: ${provider.provider}"))
            }
        }
    }
