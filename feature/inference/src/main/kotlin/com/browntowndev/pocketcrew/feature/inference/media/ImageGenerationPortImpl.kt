package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.port.media.ImageGenerationPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import javax.inject.Inject

class ImageGenerationPortImpl @Inject constructor(
    private val apiKeyProvider: ApiKeyProviderPort,
    private val openAiAdapter: OpenAiImageGenerationAdapter,
    private val googleAdapter: GoogleImageGenerationAdapter,
    private val xaiAdapter: XaiImageGenerationAdapter
) : ImageGenerationPort {
    override suspend fun generateImage(
        prompt: String,
        provider: MediaProviderAsset,
        settings: GenerationSettings
    ): Result<List<ByteArray>> {
        val apiKey = apiKeyProvider.getApiKey(provider.credentialAlias)
            ?: return Result.failure(IllegalStateException("API key not found for alias: ${provider.credentialAlias}"))

        return when (provider.provider) {
            ApiProvider.OPENAI -> openAiAdapter.generateImage(prompt, apiKey, provider.modelName ?: "dall-e-3", provider.baseUrl, settings)
            ApiProvider.GOOGLE -> googleAdapter.generateImage(prompt, apiKey, provider.modelName ?: "imagen-3", provider.baseUrl, settings)
            ApiProvider.XAI -> xaiAdapter.generateImage(prompt, apiKey, provider.modelName ?: "grok-vision-beta", provider.baseUrl, settings)
            else -> Result.failure(UnsupportedOperationException("Image generation not supported for provider: ${provider.provider}"))
        }
    }
}
