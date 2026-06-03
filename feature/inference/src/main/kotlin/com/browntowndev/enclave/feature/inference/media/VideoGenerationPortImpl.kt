package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.VisualGenerationSettings
import com.browntowndev.enclave.domain.port.media.VideoGenerationPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import javax.inject.Inject

class VideoGenerationPortImpl
    @Inject
    constructor(
        private val apiKeyProvider: ApiKeyProviderPort,
        private val studioRepository: StudioRepositoryPort,
        private val openAiAdapter: OpenAiVideoGenerationAdapter,
        private val googleAdapter: GoogleVideoGenerationAdapter,
        private val xaiAdapter: XaiVideoGenerationAdapter,
        private val falAdapter: FalVideoGenerationAdapter,
    ) : VideoGenerationPort {
        override suspend fun generateVideo(
            prompt: String,
            provider: MediaProviderAsset,
            settings: GenerationSettings,
        ): Result<ByteArray> {
            val hasReferenceImage = (settings as? VisualGenerationSettings)?.referenceImageUri != null
            if (hasReferenceImage && MediaTask.IMAGE_TO_VIDEO !in provider.tasks) {
                return Result.failure(IllegalArgumentException("Model '${provider.modelName}' does not support image-to-video."))
            }

            val apiKey =
                apiKeyProvider.getApiKey(provider.credentialAlias)
                    ?: return Result.failure(IllegalStateException("API key not found for alias: ${provider.credentialAlias}"))
            val referenceImageBytes =
                (settings as? VisualGenerationSettings)?.referenceImageUri?.let { uri ->
                    studioRepository.readMediaBytes(uri)
                }

            return when (provider.provider) {
                ApiProvider.OPENAI ->
                    openAiAdapter.generateVideo(
                        prompt = prompt,
                        apiKey = apiKey,
                        modelId = provider.modelName ?: "sora-2",
                        baseUrl = provider.baseUrl,
                        settings = settings,
                        referenceImage = referenceImageBytes,
                    )
                ApiProvider.GOOGLE ->
                    googleAdapter.generateVideo(
                        prompt = prompt,
                        apiKey = apiKey,
                        modelId = provider.modelName ?: "veo-3.1-generate-preview",
                        baseUrl = provider.baseUrl,
                        settings = settings,
                    )
                ApiProvider.XAI ->
                    xaiAdapter.generateVideo(
                        prompt = prompt,
                        apiKey = apiKey,
                        modelId = provider.modelName ?: "grok-imagine-video",
                        baseUrl = provider.baseUrl,
                        settings = settings,
                        referenceImage = referenceImageBytes,
                    )
                ApiProvider.FAL ->
                    falAdapter.generateVideo(
                        prompt = prompt,
                        apiKey = apiKey,
                        modelId = provider.modelName ?: "fal-ai/minimax/video-01",
                        baseUrl = provider.baseUrl,
                        settings = settings,
                        referenceImage = referenceImageBytes,
                        supportsImageToVideo = MediaTask.IMAGE_TO_VIDEO in provider.tasks,
                    )
                else ->
                    Result.failure(
                        UnsupportedOperationException("Video generation not supported for provider: ${provider.provider}"),
                    )
            }
        }
    }
