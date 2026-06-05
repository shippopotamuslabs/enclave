package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelPolicy
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import javax.inject.Inject

/**
 * Returns the [ProviderCapabilities] for a resolved [ImageGenerationTarget].
 *
 * Studio drives its aspect-ratio/resolution pickers from these capabilities, so the
 * local stable-diffusion backend exposes the same capability shape as remote providers.
 */
class GetImageGenerationCapabilitiesUseCase
    @Inject
    constructor(
        private val localModelRepository: LocalModelRepositoryPort,
    ) {
        suspend fun localCapabilities(localConfigId: LocalModelConfigurationId): ProviderCapabilities {
            val asset =
                localModelRepository.getAssetByConfigId(localConfigId)
                    ?: error("Missing local model configuration for $localConfigId.")
            val descriptor =
                LocalImageGenerationModelRegistry.descriptorFor(asset)
                    ?: error("Assigned local model is not an image generation model.")
            return LocalImageGenerationModelPolicy.providerCapabilitiesFor(descriptor.id)
        }

        operator fun invoke(target: ImageGenerationTarget): ProviderCapabilities =
            when (target) {
                is ImageGenerationTarget.LocalStableDiffusion -> target.capabilities
                is ImageGenerationTarget.Remote ->
                    target.providerCapabilities ?: EMPTY_CAPABILITIES
            }

        private companion object {
            val EMPTY_CAPABILITIES =
                ProviderCapabilities(
                    supportedAspectRatios = emptyList(),
                    supportedImageQualities = emptyList(),
                    supportedVideoQualities = emptyList(),
                    supportedVideoResolutions = emptyList(),
                    supportedVideoDurations = emptyList(),
                    supportsVideo = false,
                )
        }
    }
