package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VisualGenerationSettings
import com.browntowndev.pocketcrew.domain.port.media.VideoGenerationPort
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import javax.inject.Inject

class VideoGenerationPortImpl @Inject constructor(
    private val apiKeyProvider: ApiKeyProviderPort,
    private val studioRepository: StudioRepositoryPort,
    private val openAiAdapter: OpenAiVideoGenerationAdapter,
    private val googleAdapter: GoogleVideoGenerationAdapter,
    private val xaiAdapter: XaiVideoGenerationAdapter,
) : VideoGenerationPort {
    override suspend fun generateVideo(
        prompt: String,
        provider: MediaProviderAsset,
        settings: GenerationSettings,
    ): Result<ByteArray> {
        val apiKey = apiKeyProvider.getApiKey(provider.credentialAlias)
            ?: return Result.failure(IllegalStateException("API key not found for alias: ${provider.credentialAlias}"))
        val referenceImageBytes = (settings as? VisualGenerationSettings)?.referenceImageUri?.let { uri ->
            studioRepository.readMediaBytes(uri)
        }

        return when (provider.provider) {
            ApiProvider.OPENAI -> openAiAdapter.generateVideo(
                prompt = prompt,
                apiKey = apiKey,
                modelId = provider.modelName ?: "sora-2",
                baseUrl = provider.baseUrl,
                settings = settings,
                referenceImage = referenceImageBytes,
            )
            ApiProvider.GOOGLE -> googleAdapter.generateVideo(
                prompt = prompt,
                apiKey = apiKey,
                modelId = provider.modelName ?: "veo-3.1-generate-preview",
                baseUrl = provider.baseUrl,
                settings = settings,
                referenceImage = referenceImageBytes,
            )
            ApiProvider.XAI -> xaiAdapter.generateVideo(
                prompt = prompt,
                apiKey = apiKey,
                modelId = provider.modelName ?: "grok-imagine-video",
                baseUrl = provider.baseUrl,
                settings = settings,
                referenceImage = referenceImageBytes,
            )
            else -> Result.failure(
                UnsupportedOperationException("Video generation not supported for provider: ${provider.provider}"),
            )
        }
    }
}
