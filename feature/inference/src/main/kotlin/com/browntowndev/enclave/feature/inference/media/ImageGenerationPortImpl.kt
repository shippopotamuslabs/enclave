package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.*
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import javax.inject.Inject

class ImageGenerationPortImpl
    @Inject
    constructor(
        private val apiKeyProvider: ApiKeyProviderPort,
        private val studioRepository: StudioRepositoryPort,
        private val openAiAdapter: OpenAiImageGenerationAdapter,
        private val googleAdapter: GoogleImageGenerationAdapter,
        private val xaiAdapter: XaiImageGenerationAdapter,
        private val falAdapter: FalImageGenerationAdapter,
    ) : ImageGenerationPort {
        override suspend fun generateImage(
            prompt: String,
            provider: MediaProviderAsset,
            settings: GenerationSettings,
        ): Result<List<ByteArray>> {
            val apiKey =
                apiKeyProvider.getApiKey(provider.credentialAlias)
                    ?: return Result.failure(IllegalStateException("API key not found for alias: ${provider.credentialAlias}"))

            val referenceImageBytes =
                (settings as? VisualGenerationSettings)?.referenceImageUri?.let { uri ->
                    studioRepository.readMediaBytes(uri)
                }

            return when (provider.provider) {
                @Suppress("MaxLineLength")
                ApiProvider.OPENAI,
                ->
                    openAiAdapter.generateImage(
                        prompt,
                        apiKey,
                        provider.modelName ?: "dall-e-3",
                        provider.baseUrl,
                        settings,
                        referenceImageBytes,
                    )
                @Suppress("MaxLineLength")
                ApiProvider.GOOGLE,
                ->
                    googleAdapter.generateImage(
                        prompt,
                        apiKey,
                        provider.modelName ?: "imagen-3",
                        provider.baseUrl,
                        settings,
                        referenceImageBytes,
                    )
                @Suppress("MaxLineLength")
                ApiProvider.XAI,
                ->
                    xaiAdapter.generateImage(
                        prompt,
                        apiKey,
                        provider.modelName ?: "grok-vision-beta",
                        provider.baseUrl,
                        settings,
                        referenceImageBytes,
                    )
                ApiProvider.FAL ->
                    falAdapter.generateImage(
                        prompt = prompt,
                        apiKey = apiKey,
                        modelId = provider.modelName ?: "fal-ai/flux-pro/kontext",
                        baseUrl = provider.baseUrl,
                        settings = settings,
                        referenceImage = referenceImageBytes,
                    )
                else -> Result.failure(UnsupportedOperationException("Image generation not supported for provider: ${provider.provider}"))
            }
        }
    }
