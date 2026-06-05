package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.VisualGenerationSettings
import com.browntowndev.enclave.domain.port.media.GpuCapabilityPort
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import com.browntowndev.enclave.domain.port.media.LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.feature.inference.media.local.LocalStableDiffusionImageGenerator
import javax.inject.Inject

class ImageGenerationPortImpl
    @Inject
    constructor(
        private val apiKeyProvider: ApiKeyProviderPort,
        private val studioRepository: StudioRepositoryPort,
        private val mediaProviderRepository: MediaProviderRepositoryPort,
        private val gpuCapabilityPort: GpuCapabilityPort,
        private val localStableDiffusionImageGenerator: LocalStableDiffusionImageGenerator,
        private val openAiAdapter: OpenAiImageGenerationAdapter,
        private val googleAdapter: GoogleImageGenerationAdapter,
        private val xaiAdapter: XaiImageGenerationAdapter,
        private val falAdapter: FalImageGenerationAdapter,
    ) : ImageGenerationPort {
        override suspend fun generateImage(
            prompt: String,
            target: ImageGenerationTarget,
            settings: GenerationSettings,
            onProgress: (ImageGenerationProgress) -> Unit,
        ): Result<List<ByteArray>> =
            when (target) {
                is ImageGenerationTarget.LocalStableDiffusion -> generateLocal(prompt, target, settings, onProgress)
                is ImageGenerationTarget.Remote -> generateRemote(prompt, target, settings)
            }

        private suspend fun generateLocal(
            prompt: String,
            target: ImageGenerationTarget.LocalStableDiffusion,
            settings: GenerationSettings,
            onProgress: (ImageGenerationProgress) -> Unit,
        ): Result<List<ByteArray>> {
            if (!gpuCapabilityPort.isLocalImageGenerationSupported()) {
                return Result.failure(IllegalStateException(LOCAL_IMAGE_GENERATION_UNSUPPORTED_MESSAGE))
            }

            val imageSettings =
                settings as? ImageGenerationSettings
                    ?: return Result.failure(
                        IllegalArgumentException("Local image generation requires image settings."),
                    )

            return localStableDiffusionImageGenerator
                .generate(prompt = prompt, target = target, settings = imageSettings, onProgress = onProgress)
                .mapCatching { result ->
                    try {
                        result.outputFileReferences.map { outputFileReference ->
                            studioRepository.readMediaBytes(outputFileReference)
                                ?: throw IllegalStateException("Local image generation failed.")
                        }
                    } finally {
                        result.outputFileReferences.forEach { outputFileReference ->
                            java.io.File(outputFileReference).delete()
                        }
                    }
                }
        }

        private suspend fun generateRemote(
            prompt: String,
            target: ImageGenerationTarget.Remote,
            settings: GenerationSettings,
        ): Result<List<ByteArray>> {
            val provider =
                mediaProviderRepository.getMediaProvider(target.providerAssetId)
                    ?: return Result.failure(IllegalStateException("Assigned media provider not found"))

            val hasReferenceImage = (settings as? VisualGenerationSettings)?.referenceImageUri != null
            if (hasReferenceImage && MediaTask.EDIT_IMAGE !in provider.tasks) {
                return Result.failure(IllegalArgumentException("Model '${provider.modelName}' does not support image editing."))
            }

            val apiKey =
                apiKeyProvider.getApiKey(provider.credentialAlias)
                    ?: return Result.failure(IllegalStateException("API key not found for alias: ${provider.credentialAlias}"))

            val referenceImageBytes =
                (settings as? VisualGenerationSettings)?.referenceImageUri?.let { uri ->
                    studioRepository.readMediaBytes(uri)
                }

            return dispatchToProvider(prompt, provider, settings, apiKey, referenceImageBytes)
        }

        private suspend fun dispatchToProvider(
            prompt: String,
            provider: MediaProviderAsset,
            settings: GenerationSettings,
            apiKey: String,
            referenceImageBytes: ByteArray?,
        ): Result<List<ByteArray>> =
            when (provider.provider) {
                ApiProvider.OPENAI ->
                    openAiAdapter.generateImage(
                        prompt,
                        apiKey,
                        provider.modelName ?: "dall-e-3",
                        provider.baseUrl,
                        settings,
                        referenceImageBytes,
                    )
                ApiProvider.GOOGLE ->
                    googleAdapter.generateImage(
                        prompt,
                        apiKey,
                        provider.modelName ?: "imagen-3",
                        provider.baseUrl,
                        settings,
                        referenceImageBytes,
                    )
                ApiProvider.XAI ->
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
                        supportsEdit = MediaTask.EDIT_IMAGE in provider.tasks,
                    )
                else -> Result.failure(UnsupportedOperationException("Image generation not supported for provider: ${provider.provider}"))
            }
    }
