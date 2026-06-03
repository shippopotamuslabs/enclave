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
        private val falAdapter: FalMusicGenerationAdapter,
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
                ApiProvider.GOOGLE -> {
                    val modelId =
                        provider.modelName
                            ?: return Result.failure(IllegalStateException("No model configured for provider: ${provider.id}"))
                    googleAdapter.generateMusic(
                        prompt,
                        apiKey,
                        modelId,
                        provider.baseUrl,
                        settings,
                    )
                }
                ApiProvider.FAL -> {
                    val modelId = provider.modelName ?: "fal-ai/stable-audio"
                    falAdapter.generateMusic(
                        prompt = prompt,
                        apiKey = apiKey,
                        modelId = modelId,
                        baseUrl = provider.baseUrl,
                        settings = settings,
                    )
                }
                else -> Result.failure(UnsupportedOperationException("Music generation not supported for provider: ${provider.provider}"))
            }
        }
    }
